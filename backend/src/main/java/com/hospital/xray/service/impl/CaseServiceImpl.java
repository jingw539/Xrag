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
import com.hospital.xray.entity.ImageAnnotation;
import com.hospital.xray.entity.ReportInfo;
import com.hospital.xray.entity.ReportEditHistory;
import com.hospital.xray.entity.RetrievalLog;
import com.hospital.xray.entity.SysUser;
import com.hospital.xray.entity.TermCorrection;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.exception.CaseHasSignedReportException;
import com.hospital.xray.exception.CaseNotFoundException;
import com.hospital.xray.exception.DuplicateExamNoException;
import com.hospital.xray.mapper.CaseInfoMapper;
import com.hospital.xray.mapper.ImageInfoMapper;
import com.hospital.xray.mapper.ImageAnnotationMapper;
import com.hospital.xray.mapper.ReportInfoMapper;
import com.hospital.xray.mapper.ReportEditHistoryMapper;
import com.hospital.xray.mapper.RetrievalLogMapper;
import com.hospital.xray.mapper.SysUserMapper;
import com.hospital.xray.mapper.TermCorrectionMapper;
import com.hospital.xray.service.CaseService;
import com.hospital.xray.util.SecurityUtils;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
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
    private ReportEditHistoryMapper reportEditHistoryMapper;

    @Autowired
    private TermCorrectionMapper termCorrectionMapper;

    @Autowired
    private RetrievalLogMapper retrievalLogMapper;

    @Autowired
    private ImageAnnotationMapper imageAnnotationMapper;

    @Autowired
    private SysUserMapper sysUserMapper;
    
    @Autowired
    private MinioClient minioClient;
    
    @Autowired
    private String minioBucketName;

    private static final String LOCAL_PREFIX = "local:";

    @Value("${storage.type:minio}")
    private String storageType;

    @Value("${storage.local.root:}")
    private String localRoot;
    
    @Override
    public PageResult<CaseVO> listCases(CaseQueryDTO query) {
        Page<CaseInfo> page = new Page<>(query.getPage(), query.getPageSize());
        
        LambdaQueryWrapper<CaseInfo> wrapper = new LambdaQueryWrapper<>();
        
        wrapper.like(StringUtils.hasText(query.getExamNo()), 
                    CaseInfo::getExamNo, query.getExamNo());
        
        wrapper.like(StringUtils.hasText(query.getPatientAnonId()), 
                    CaseInfo::getPatientAnonId, query.getPatientAnonId());
        
        wrapper.between(query.getStartTime() != null && query.getEndTime() != null,
                       CaseInfo::getExamTime, query.getStartTime(), query.getEndTime());
        
        wrapper.eq(StringUtils.hasText(query.getReportStatus()), 
                  CaseInfo::getReportStatus, query.getReportStatus());
        
        wrapper.eq(StringUtils.hasText(query.getDepartment()), 
                  CaseInfo::getDepartment, query.getDepartment());
        
        wrapper.eq(query.getIsTypical() != null, 
                  CaseInfo::getIsTypical, query.getIsTypical());

        if (Boolean.TRUE.equals(query.getUnassignedOnly())) {
            wrapper.isNull(CaseInfo::getResponsibleDoctorId);
        } else if (SecurityUtils.hasRole("DOCTOR")) {
            wrapper.eq(CaseInfo::getResponsibleDoctorId, SecurityUtils.getCurrentUserId());
        } else if (query.getDoctorId() != null) {
            wrapper.eq(CaseInfo::getResponsibleDoctorId, query.getDoctorId());
        }
        
        if ("asc".equalsIgnoreCase(query.getSortOrder())) {
            wrapper.orderByAsc(CaseInfo::getExamTime);
        } else {
            wrapper.orderByDesc(CaseInfo::getExamTime);
        }
        
        Page<CaseInfo> result = caseInfoMapper.selectPage(page, wrapper);
        
        List<CaseVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        
        populateResponsibleDoctorNames(voList);

        enrichWithReportData(voList);
        
        return PageResult.of(result.getTotal(), voList);
    }
    
    @Override
    public CaseDetailVO getCaseById(Long caseId) {
        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        if (caseInfo == null) {
            throw new CaseNotFoundException(caseId);
        }

        assertCaseReadable(caseInfo);

        if (caseInfo.getReportStatus() != null && !"NONE".equals(caseInfo.getReportStatus())) {
            Long reportCount = reportInfoMapper.selectCount(
                    new LambdaQueryWrapper<ReportInfo>().eq(ReportInfo::getCaseId, caseId));
            if (reportCount == 0) {
                log.warn("病例报告状态异常，已重置为 NONE: caseId={} reportStatus={}", caseId, caseInfo.getReportStatus());
                caseInfo.setReportStatus("NONE");
                caseInfo.setUpdatedAt(LocalDateTime.now());
                caseInfoMapper.updateById(caseInfo);
            }
        }

        CaseDetailVO detailVO = new CaseDetailVO();
        BeanUtils.copyProperties(caseInfo, detailVO);
        fillResponsibleDoctor(detailVO, caseInfo.getResponsibleDoctorId());

        log.info("获取病例详情: caseId={}", caseId);
        return detailVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        LambdaQueryWrapper<CaseInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CaseInfo::getExamNo, dto.getExamNo());
        CaseInfo existing = caseInfoMapper.selectOne(wrapper);
        
        if (existing != null) {
            throw new DuplicateExamNoException(dto.getExamNo());
        }
        
        CaseInfo caseInfo = new CaseInfo();
        BeanUtils.copyProperties(dto, caseInfo);
        caseInfo.setReportStatus("NONE");
        caseInfo.setIsTypical(0);
        if (SecurityUtils.hasRole("DOCTOR")) {
            caseInfo.setResponsibleDoctorId(SecurityUtils.getCurrentUserId());
        }
        caseInfo.setCreatedAt(LocalDateTime.now());
        caseInfo.setUpdatedAt(LocalDateTime.now());
        
        caseInfoMapper.insert(caseInfo);
        
        return caseInfo.getCaseId();
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCase(Long caseId, CaseUpdateDTO dto) {
        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        if (caseInfo == null) {
            throw new CaseNotFoundException(caseId);
        }
        assertCaseWritable(caseInfo);
        
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
        
        caseInfo.setUpdatedAt(LocalDateTime.now());
        
        caseInfoMapper.updateById(caseInfo);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCase(Long caseId) {
        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        if (caseInfo == null) {
            throw new CaseNotFoundException(caseId);
        }
        
        if ("SIGNED".equals(caseInfo.getReportStatus())) {
            throw new CaseHasSignedReportException();
        }

        LambdaQueryWrapper<ImageInfo> imageWrapper = new LambdaQueryWrapper<>();
        imageWrapper.eq(ImageInfo::getCaseId, caseId);
        List<ImageInfo> images = imageInfoMapper.selectList(imageWrapper);

        // Collect report ids for dependent cleanup
        List<ReportInfo> reports = reportInfoMapper.selectList(
                new LambdaQueryWrapper<ReportInfo>().eq(ReportInfo::getCaseId, caseId));
        List<Long> reportIds = reports.stream()
                .map(ReportInfo::getReportId)
                .collect(Collectors.toList());

        if (!reportIds.isEmpty()) {
            reportEditHistoryMapper.delete(
                    new LambdaQueryWrapper<ReportEditHistory>()
                            .in(ReportEditHistory::getReportId, reportIds));
            termCorrectionMapper.delete(
                    new LambdaQueryWrapper<TermCorrection>()
                            .in(TermCorrection::getReportId, reportIds));
            imageAnnotationMapper.delete(
                    new LambdaQueryWrapper<ImageAnnotation>()
                            .in(ImageAnnotation::getReportId, reportIds));
        }

        // Remove reports before retrieval logs to avoid FK conflicts
        reportInfoMapper.delete(
                new LambdaQueryWrapper<ReportInfo>().eq(ReportInfo::getCaseId, caseId));

        // Clean retrieval logs referencing this case or its images
        LambdaQueryWrapper<RetrievalLog> retrievalWrapper = new LambdaQueryWrapper<>();
        retrievalWrapper.eq(RetrievalLog::getCaseId, caseId);
        if (!images.isEmpty()) {
            List<Long> imageIds = images.stream().map(ImageInfo::getImageId).collect(Collectors.toList());
            retrievalWrapper.or().in(RetrievalLog::getQueryImageId, imageIds);
        }
        retrievalLogMapper.delete(retrievalWrapper);
        
        for (ImageInfo image : images) {
            try {
                deleteStorageObject(image.getFilePath());
                log.info("删除影像成功: {}", image.getFilePath());
            } catch (Exception e) {
                log.error("删除影像失败: {}", image.getFilePath(), e);
            }
            try {
                deleteStorageObject(buildThumbnailPath(image.getFilePath()));
            } catch (Exception e) {
                log.warn("删除缩略图失败: {}", e.getMessage());
            }
        }
        
        if (!images.isEmpty()) {
            imageInfoMapper.delete(imageWrapper);
        }
        
        caseInfoMapper.deleteById(caseId);
        
        log.info("病例删除完成: caseId={}, 删除影像数量={}, 删除报告数量={}", caseId, images.size(), reportIds.size());
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markTypical(Long caseId, TypicalMarkDTO dto) {
        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        if (caseInfo == null) {
            throw new CaseNotFoundException(caseId);
        }
        
        caseInfo.setIsTypical(dto.getIsTypical());
        caseInfo.setTypicalTags(dto.getTypicalTags());
        caseInfo.setTypicalRemark(dto.getTypicalRemark());
        caseInfo.setUpdatedAt(LocalDateTime.now());
        
        caseInfoMapper.updateById(caseInfo);
        
        log.info("标记典型病例: caseId={}, isTypical={}, tags={}", caseId, dto.getIsTypical(), dto.getTypicalTags());
    }

    private boolean isLocalPath(String filePath) {
        if (filePath == null) return false;
        if (filePath.startsWith(LOCAL_PREFIX)) return true;
        try {
            if ("local".equalsIgnoreCase(storageType) && !Paths.get(filePath).isAbsolute()) {
                return true;
            }
            return Paths.get(filePath).isAbsolute();
        } catch (Exception e) {
            return false;
        }
    }

    private Path resolveLocalPath(String filePath) {
        String path = filePath;
        if (path.startsWith(LOCAL_PREFIX)) {
            path = path.substring(LOCAL_PREFIX.length());
        }
        Path p = Paths.get(path);
        if (p.isAbsolute()) return p;
        if (!StringUtils.hasText(localRoot)) {
            throw new BusinessException("Local storage root not configured: storage.local.root");
        }
        return Paths.get(localRoot).resolve(path).normalize();
    }

    private String buildThumbnailPath(String filePath) {
        boolean prefixed = filePath != null && filePath.startsWith(LOCAL_PREFIX);
        String raw = prefixed ? filePath.substring(LOCAL_PREFIX.length()) : filePath;
        int dot = raw.lastIndexOf('.');
        String thumb = dot > 0 ? raw.substring(0, dot) + "_thumb.jpg" : raw + "_thumb.jpg";
        return prefixed ? LOCAL_PREFIX + thumb : thumb;
    }

    private void deleteStorageObject(String filePath) throws Exception {
        if (isLocalPath(filePath)) {
            Path path = resolveLocalPath(filePath);
            Files.deleteIfExists(path);
            return;
        }
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(minioBucketName)
                        .object(filePath)
                        .build()
        );
    }
    
    /**
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


    private void assertCaseReadable(CaseInfo caseInfo) {
        if (!SecurityUtils.hasRole("DOCTOR")) {
            return;
        }
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "Not logged in");
        }
        if (caseInfo == null) {
            throw new BusinessException(404, "Case not found");
        }
        // Read-only access is allowed for other doctors' cases.
    }

    private void assertCaseWritable(CaseInfo caseInfo) {
        if (!SecurityUtils.hasRole("DOCTOR")) {
            return;
        }
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "Not logged in");
        }
        if (caseInfo.getResponsibleDoctorId() == null) {
            throw new BusinessException(403, "Case not assigned");
        }
        if (!currentUserId.equals(caseInfo.getResponsibleDoctorId())) {
            throw new BusinessException(403, "Cannot operate on other doctor case");
        }
    }


    /**
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
                vo.setReportStatus("NONE");
                log.warn("病例报告状态异常，已重置为 NONE: caseId={} reportStatus={}",
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
        
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new BusinessException("仅支持 CSV 格式文件");
        }
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new BusinessException("CSV 文件不能为空");
            }
            
            if (!isValidHeader(headerLine)) {
                throw new BusinessException("CSV 文件格式错误，期望表头：检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室");
            }
            
            String line;
            int rowNumber = 1; // 表头为第 1 行，数据从第 2 行开始
            
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                result.setTotalRows(result.getTotalRows() + 1);
                
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    CaseCreateDTO dto = parseCsvLine(line, rowNumber);
                    createCase(dto);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } catch (DuplicateExamNoException e) {
                    result.setFailedCount(result.getFailedCount() + 1);
                    result.getErrors().add(new ImportError(rowNumber, "检查号已存在"));
                    log.warn("CSV 导入重复检查号 - 行号 {}", rowNumber);
                } catch (Exception e) {
                    result.setFailedCount(result.getFailedCount() + 1);
                    result.getErrors().add(new ImportError(rowNumber, e.getMessage()));
                    log.warn("CSV 导入行处理失败 - 行号 {}: {}", rowNumber, e.getMessage());
                }
            }
            
            log.info("CSV 导入完成: 总行数={}, 成功={}, 失败={}",
                    result.getTotalRows(), result.getSuccessCount(), result.getFailedCount());
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("CSV 导入失败", e);
            throw new BusinessException("CSV 导入失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     */
    private boolean isValidHeader(String headerLine) {
        String expectedHeaderEn = "examNo,patientAnonId,gender,age,examTime,bodyPart,department";
        String expectedHeaderZh = "检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室";
        String header = headerLine.trim();
        return header.equals(expectedHeaderEn) || header.equals(expectedHeaderZh);
    }
    
    /**
     */
    private CaseCreateDTO parseCsvLine(String line, int rowNumber) {
        String[] fields = line.split(",", -1);
        
        if (fields.length != 7) {
            throw new BusinessException("CSV 文件格式错误，应为 7 列");
        }
        
        CaseCreateDTO dto = new CaseCreateDTO();
        
        String examNo = fields[0].trim();
        if (examNo.isEmpty()) {
            throw new BusinessException("必填字段缺失：检查号");
        }
        dto.setExamNo(examNo);
        
        String patientAnonId = fields[1].trim();
        if (patientAnonId.isEmpty()) {
            throw new BusinessException("必填字段缺失：患者匿名ID");
        }
        dto.setPatientAnonId(patientAnonId);
        
        String gender = fields[2].trim();
        if (!gender.isEmpty()) {
            dto.setGender(gender);
        }
        
        String ageStr = fields[3].trim();
        if (!ageStr.isEmpty()) {
            try {
                dto.setAge(Integer.parseInt(ageStr));
            } catch (NumberFormatException e) {
            throw new BusinessException("年龄格式错误");
            }
        }
        
        String examTimeStr = fields[4].trim();
        if (examTimeStr.isEmpty()) {
            throw new BusinessException("检查时间格式错误，期望格式：yyyy-MM-dd HH:mm:ss");
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            dto.setExamTime(LocalDateTime.parse(examTimeStr, formatter));
        } catch (DateTimeParseException e) {
            throw new BusinessException("检查时间格式错误，期望格式：yyyy-MM-dd HH:mm:ss");
        }
        
        String bodyPart = fields[5].trim();
        if (bodyPart.isEmpty()) {
            throw new BusinessException("检查部位不能为空");
        }
        dto.setBodyPart(bodyPart);
        
        String department = fields[6].trim();
        if (!department.isEmpty()) {
            dto.setDepartment(department);
        }
        
        return dto;
    }
}
