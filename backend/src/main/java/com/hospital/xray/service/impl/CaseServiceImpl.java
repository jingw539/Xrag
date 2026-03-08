package com.hospital.xray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.xray.dto.CaseCreateDTO;
import com.hospital.xray.dto.CaseDetailVO;
import com.hospital.xray.dto.CaseQueryDTO;
import com.hospital.xray.dto.CaseUpdateDTO;
import com.hospital.xray.dto.CaseVO;
import com.hospital.xray.dto.ImportError;
import com.hospital.xray.dto.ImportResult;
import com.hospital.xray.dto.PageResult;
import com.hospital.xray.dto.TypicalMarkDTO;
import com.hospital.xray.entity.CaseInfo;
import com.hospital.xray.entity.ImageInfo;
import com.hospital.xray.entity.ReportInfo;
import com.hospital.xray.entity.SysUser;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.exception.CaseHasSignedReportException;
import com.hospital.xray.exception.CaseNotFoundException;
import com.hospital.xray.exception.DuplicateExamNoException;
import com.hospital.xray.mapper.CaseInfoMapper;
import com.hospital.xray.mapper.ImageInfoMapper;
import com.hospital.xray.mapper.ReportInfoMapper;
import com.hospital.xray.mapper.SysUserMapper;
import com.hospital.xray.service.CaseService;
import com.hospital.xray.util.SecurityUtils;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 病例服务实现类
 */
@Slf4j
@Service
public class CaseServiceImpl implements CaseService {
    
    @Autowired
    private CaseInfoMapper caseInfoMapper;
    
    @Autowired
    private ImageInfoMapper imageInfoMapper;
    
    @Autowired
    private ReportInfoMapper reportInfoMapper;

    @Autowired
    private SysUserMapper sysUserMapper;
    
    @Autowired
    private MinioClient minioClient;
    
    @Autowired
    private String minioBucketName;
    
    @Override
    public PageResult<CaseVO> listCases(CaseQueryDTO query) {
        // 创建分页对象
        Page<CaseInfo> page = new Page<>(query.getPage(), query.getPageSize());
        
        // 构建查询条件
        LambdaQueryWrapper<CaseInfo> wrapper = new LambdaQueryWrapper<>();
        
        // 检查号模糊查询
        wrapper.like(StringUtils.hasText(query.getExamNo()), 
                    CaseInfo::getExamNo, query.getExamNo());
        
        // 患者匿名ID模糊查询
        wrapper.like(StringUtils.hasText(query.getPatientAnonId()), 
                    CaseInfo::getPatientAnonId, query.getPatientAnonId());
        
        // 时间范围查询
        wrapper.between(query.getStartTime() != null && query.getEndTime() != null,
                       CaseInfo::getExamTime, query.getStartTime(), query.getEndTime());
        
        // 报告状态精确查询
        wrapper.eq(StringUtils.hasText(query.getReportStatus()), 
                  CaseInfo::getReportStatus, query.getReportStatus());
        
        // 科室精确查询
        wrapper.eq(StringUtils.hasText(query.getDepartment()), 
                  CaseInfo::getDepartment, query.getDepartment());
        
        // 是否典型病例查询
        wrapper.eq(query.getIsTypical() != null, 
                  CaseInfo::getIsTypical, query.getIsTypical());

        if (Boolean.TRUE.equals(query.getUnassignedOnly())) {
            wrapper.isNull(CaseInfo::getResponsibleDoctorId);
        } else if (SecurityUtils.hasRole("DOCTOR")) {
            wrapper.eq(CaseInfo::getResponsibleDoctorId, SecurityUtils.getCurrentUserId());
        } else if (query.getDoctorId() != null) {
            wrapper.eq(CaseInfo::getResponsibleDoctorId, query.getDoctorId());
        }
        
        // 排序
        if ("asc".equalsIgnoreCase(query.getSortOrder())) {
            wrapper.orderByAsc(CaseInfo::getExamTime);
        } else {
            wrapper.orderByDesc(CaseInfo::getExamTime);
        }
        
        // 执行分页查询
        Page<CaseInfo> result = caseInfoMapper.selectPage(page, wrapper);
        
        // 转换为 VO
        List<CaseVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        
        populateResponsibleDoctorNames(voList);

        // 批量关联最新报告数据
        enrichWithReportData(voList);
        
        return PageResult.of(result.getTotal(), voList);
    }
    
    @Override
    public CaseDetailVO getCaseById(Long caseId) {
        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        if (caseInfo == null) {
            throw new CaseNotFoundException(caseId);
        }

        assertCaseAccessible(caseInfo);

        if (caseInfo.getReportStatus() != null && !"NONE".equals(caseInfo.getReportStatus())) {
            Long reportCount = reportInfoMapper.selectCount(
                    new LambdaQueryWrapper<ReportInfo>().eq(ReportInfo::getCaseId, caseId));
            if (reportCount == 0) {
                log.warn("数据自愈: caseId={} reportStatus={} 但无报告记录，纠正为NONE", caseId, caseInfo.getReportStatus());
                caseInfo.setReportStatus("NONE");
                caseInfo.setUpdatedAt(LocalDateTime.now());
                caseInfoMapper.updateById(caseInfo);
            }
        }

        CaseDetailVO detailVO = new CaseDetailVO();
        BeanUtils.copyProperties(caseInfo, detailVO);
        fillResponsibleDoctor(detailVO, caseInfo.getResponsibleDoctorId());

        log.info("查询病例详情: caseId={}", caseId);
        return detailVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "case", key = "#caseId")
    public CaseDetailVO claimCase(Long caseId) {
        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        if (caseInfo == null) {
            throw new CaseNotFoundException(caseId);
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未登录");
        }

        if (caseInfo.getResponsibleDoctorId() == null) {
            caseInfo.setResponsibleDoctorId(currentUserId);
            caseInfo.setUpdatedAt(LocalDateTime.now());
            caseInfoMapper.updateById(caseInfo);
        } else if (!caseInfo.getResponsibleDoctorId().equals(currentUserId)) {
            throw new BusinessException(409, "该病例已被其他医生接诊");
        }

        return getCaseById(caseId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCase(CaseCreateDTO dto) {
        // 1. 校验检查号唯一性
        LambdaQueryWrapper<CaseInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CaseInfo::getExamNo, dto.getExamNo());
        CaseInfo existing = caseInfoMapper.selectOne(wrapper);
        
        if (existing != null) {
            throw new DuplicateExamNoException(dto.getExamNo());
        }
        
        // 2. 创建病例实体（使用雪花算法自动生成 caseId）
        CaseInfo caseInfo = new CaseInfo();
        BeanUtils.copyProperties(dto, caseInfo);
        caseInfo.setReportStatus("NONE");
        caseInfo.setIsTypical(0);
        if (SecurityUtils.hasRole("DOCTOR")) {
            caseInfo.setResponsibleDoctorId(SecurityUtils.getCurrentUserId());
        }
        caseInfo.setCreatedAt(LocalDateTime.now());
        caseInfo.setUpdatedAt(LocalDateTime.now());
        
        // 3. 插入数据库
        caseInfoMapper.insert(caseInfo);
        
        // 4. 返回生成的 caseId
        return caseInfo.getCaseId();
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "case", key = "#caseId")
    public void updateCase(Long caseId, CaseUpdateDTO dto) {
        // 1. 查询病例是否存在
        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        if (caseInfo == null) {
            throw new CaseNotFoundException(caseId);
        }
        
        // 2. 更新允许修改的字段
        if (StringUtils.hasText(dto.getPatientAnonId())) {
            caseInfo.setPatientAnonId(dto.getPatientAnonId());
        }
        if (StringUtils.hasText(dto.getGender())) {
            caseInfo.setGender(dto.getGender());
        }
        if (dto.getAge() != null) {
            caseInfo.setAge(dto.getAge());
        }
        if (StringUtils.hasText(dto.getDepartment())) {
            caseInfo.setDepartment(dto.getDepartment());
        }
        
        // 3. 更新时间戳
        caseInfo.setUpdatedAt(LocalDateTime.now());
        
        // 4. 保存到数据库
        caseInfoMapper.updateById(caseInfo);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "case", key = "#caseId")
    public void deleteCase(Long caseId) {
        // 1. 查询病例是否存在
        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        if (caseInfo == null) {
            throw new CaseNotFoundException(caseId);
        }
        
        // 2. 检查是否存在已签发报告
        if ("SIGNED".equals(caseInfo.getReportStatus())) {
            throw new CaseHasSignedReportException();
        }
        
        // 3. 查询关联的所有影像记录
        LambdaQueryWrapper<ImageInfo> imageWrapper = new LambdaQueryWrapper<>();
        imageWrapper.eq(ImageInfo::getCaseId, caseId);
        List<ImageInfo> images = imageInfoMapper.selectList(imageWrapper);
        
        // 4. 删除 MinIO 中的影像文件
        for (ImageInfo image : images) {
            try {
                minioClient.removeObject(
                    RemoveObjectArgs.builder()
                        .bucket(minioBucketName)
                        .object(image.getFilePath())
                        .build()
                );
                log.info("删除MinIO文件成功: {}", image.getFilePath());
            } catch (Exception e) {
                log.error("删除MinIO文件失败: {}", image.getFilePath(), e);
                // 继续删除其他文件，不中断流程
            }
        }
        
        // 5. 删除影像元数据记录
        if (!images.isEmpty()) {
            imageInfoMapper.delete(imageWrapper);
        }
        
        // 6. 删除病例记录
        caseInfoMapper.deleteById(caseId);
        
        log.info("删除病例成功: caseId={}, 删除影像数量={}", caseId, images.size());
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "case", key = "#caseId")
    public void markTypical(Long caseId, TypicalMarkDTO dto) {
        // 1. 查询病例是否存在
        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        if (caseInfo == null) {
            throw new CaseNotFoundException(caseId);
        }
        
        // 2. 更新典型病例标记
        caseInfo.setIsTypical(dto.getIsTypical());
        caseInfo.setTypicalTags(dto.getTypicalTags());
        caseInfo.setTypicalRemark(dto.getTypicalRemark());
        caseInfo.setUpdatedAt(LocalDateTime.now());
        
        // 3. 保存到数据库
        caseInfoMapper.updateById(caseInfo);
        
        log.info("标记典型病例: caseId={}, isTypical={}, tags={}", 
                caseId, dto.getIsTypical(), dto.getTypicalTags());
    }
    
    /**
     * 将实体转换为 VO
     */
    private CaseVO convertToVO(CaseInfo caseInfo) {
        CaseVO vo = new CaseVO();
        BeanUtils.copyProperties(caseInfo, vo);
        return vo;
    }

    private void populateResponsibleDoctorNames(List<CaseVO> voList) {
        List<Long> doctorIds = voList.stream()
                .map(CaseVO::getResponsibleDoctorId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (doctorIds.isEmpty()) {
            return;
        }

        Map<Long, String> doctorNameMap = sysUserMapper.selectBatchIds(doctorIds).stream()
                .collect(Collectors.toMap(SysUser::getUserId, SysUser::getRealName));
        for (CaseVO vo : voList) {
            if (vo.getResponsibleDoctorId() != null) {
                vo.setResponsibleDoctorName(doctorNameMap.get(vo.getResponsibleDoctorId()));
            }
        }
    }

    private void fillResponsibleDoctor(CaseDetailVO detailVO, Long doctorId) {
        if (doctorId == null) {
            return;
        }
        SysUser doctor = sysUserMapper.selectById(doctorId);
        if (doctor != null) {
            detailVO.setResponsibleDoctorName(doctor.getRealName());
        }
    }

    private void assertCaseAccessible(CaseInfo caseInfo) {
        if (!SecurityUtils.hasRole("DOCTOR")) {
            return;
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (caseInfo.getResponsibleDoctorId() == null) {
            throw new BusinessException(403, "请先接诊该病例");
        }
        if (!currentUserId.equals(caseInfo.getResponsibleDoctorId())) {
            throw new BusinessException(403, "无权查看其他医生负责的病例");
        }
    }

    /**
     * 批量关联最新报告数据到 CaseVO
     */
    private void enrichWithReportData(List<CaseVO> voList) {
        if (voList.isEmpty()) return;
        List<Long> caseIds = voList.stream().map(CaseVO::getCaseId).collect(Collectors.toList());
        List<ReportInfo> reports = reportInfoMapper.selectList(
                new LambdaQueryWrapper<ReportInfo>()
                        .in(ReportInfo::getCaseId, caseIds)
                        .orderByDesc(ReportInfo::getCreatedAt));
        Map<Long, ReportInfo> latestReportMap = reports.stream()
                .collect(Collectors.toMap(ReportInfo::getCaseId, r -> r, (a, b) -> a));
        for (CaseVO vo : voList) {
            ReportInfo r = latestReportMap.get(vo.getCaseId());
            if (r != null) {
                vo.setModelConfidence(r.getModelConfidence());
                vo.setQualityGrade(r.getQualityGrade());
                vo.setSignTime(r.getSignTime());
                vo.setLastEditTime(r.getUpdatedAt());
            } else if (vo.getReportStatus() != null && !"NONE".equals(vo.getReportStatus())) {
                // 数据自愈：case_info 标记了非 NONE 状态但 report_info 无记录，纠正为 NONE
                vo.setReportStatus("NONE");
                log.warn("数据不一致: caseId={} reportStatus={} 但无报告记录，已纠正为NONE",
                        vo.getCaseId(), vo.getReportStatus());
            }
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importCases(MultipartFile file) {
        ImportResult result = ImportResult.builder()
                .totalRows(0)
                .successCount(0)
                .failedCount(0)
                .errors(new ArrayList<>())
                .build();
        
        // 1. 校验文件格式
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new BusinessException("仅支持 CSV 格式文件");
        }
        
        // 2. 解析 CSV 文件
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            
            // 读取表头
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new BusinessException("CSV 文件为空");
            }
            
            // 校验表头格式
            if (!isValidHeader(headerLine)) {
                throw new BusinessException("CSV 文件格式错误，期望表头：检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室");
            }
            
            // 逐行读取数据
            String line;
            int rowNumber = 1; // 从第1行开始（不包括表头）
            
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                result.setTotalRows(result.getTotalRows() + 1);
                
                // 跳过空行
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                // 解析并导入单行数据
                try {
                    CaseCreateDTO dto = parseCsvLine(line, rowNumber);
                    createCase(dto);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } catch (DuplicateExamNoException e) {
                    result.setFailedCount(result.getFailedCount() + 1);
                    result.getErrors().add(new ImportError(rowNumber, "检查号已存在"));
                    log.warn("导入失败 - 行{}: 检查号已存在", rowNumber);
                } catch (Exception e) {
                    result.setFailedCount(result.getFailedCount() + 1);
                    result.getErrors().add(new ImportError(rowNumber, e.getMessage()));
                    log.warn("导入失败 - 行{}: {}", rowNumber, e.getMessage());
                }
            }
            
            log.info("批量导入完成: 总行数={}, 成功={}, 失败={}", 
                    result.getTotalRows(), result.getSuccessCount(), result.getFailedCount());
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("批量导入失败", e);
            throw new BusinessException("批量导入失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 校验 CSV 表头格式
     */
    private boolean isValidHeader(String headerLine) {
        String expectedHeader = "检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室";
        return headerLine.trim().equals(expectedHeader);
    }
    
    /**
     * 解析 CSV 行数据
     */
    private CaseCreateDTO parseCsvLine(String line, int rowNumber) {
        String[] fields = line.split(",", -1); // -1 保留空字段
        
        if (fields.length != 7) {
            throw new BusinessException("字段数量不正确，期望7个字段");
        }
        
        CaseCreateDTO dto = new CaseCreateDTO();
        
        // 检查号（必填）
        String examNo = fields[0].trim();
        if (examNo.isEmpty()) {
            throw new BusinessException("必填字段缺失：检查号");
        }
        dto.setExamNo(examNo);
        
        // 患者匿名ID（必填）
        String patientAnonId = fields[1].trim();
        if (patientAnonId.isEmpty()) {
            throw new BusinessException("必填字段缺失：患者匿名ID");
        }
        dto.setPatientAnonId(patientAnonId);
        
        // 性别（可选）
        String gender = fields[2].trim();
        if (!gender.isEmpty()) {
            dto.setGender(gender);
        }
        
        // 年龄（可选）
        String ageStr = fields[3].trim();
        if (!ageStr.isEmpty()) {
            try {
                dto.setAge(Integer.parseInt(ageStr));
            } catch (NumberFormatException e) {
                throw new BusinessException("年龄格式错误");
            }
        }
        
        // 检查时间（必填）
        String examTimeStr = fields[4].trim();
        if (examTimeStr.isEmpty()) {
            throw new BusinessException("必填字段缺失：检查时间");
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            dto.setExamTime(LocalDateTime.parse(examTimeStr, formatter));
        } catch (DateTimeParseException e) {
            throw new BusinessException("检查时间格式错误，期望格式：yyyy-MM-dd HH:mm:ss");
        }
        
        // 检查部位（必填）
        String bodyPart = fields[5].trim();
        if (bodyPart.isEmpty()) {
            throw new BusinessException("必填字段缺失：检查部位");
        }
        dto.setBodyPart(bodyPart);
        
        // 科室（可选）
        String department = fields[6].trim();
        if (!department.isEmpty()) {
            dto.setDepartment(department);
        }
        
        return dto;
    }
}
