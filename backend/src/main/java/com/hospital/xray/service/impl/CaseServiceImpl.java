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
 * 闂佹眹鍎遍幊宥囨閵夆晛瀚夌€广儱鎳庨～銈夋倵閸︻厼浠ф鐐叉川閻?
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
        // 闂佸憡甯楃粙鎴犵磽閹捐绀嗛柛鈩冪◤閳ь剙顦遍埀顒傛暩椤㈠﹪鎸?
        Page<CaseInfo> page = new Page<>(query.getPage(), query.getPageSize());
        
        // 闂佸搫顑呯€氼剛绱撻幘璇茶摕闁靛鐓堥崵鍕煛婢跺棌鍋撻崣澶樺仺
        LambdaQueryWrapper<CaseInfo> wrapper = new LambdaQueryWrapper<>();
        
        // 濠碘槅鍋€閸嬫捇鏌＄仦璇插姎鐟滄澘鐏濊灒闁挎稑瀚棟闂佸搫琚崕鎾敋?
        wrapper.like(StringUtils.hasText(query.getExamNo()), 
                    CaseInfo::getExamNo, query.getExamNo());
        
        // 闂佽鍣紞鈧柍褜鍓欓幊搴ｄ焊閸洖瑙︾€光偓缁洦淇婇銈囩？缁绢叏绠撳濠氬Ψ椤垵娈?
        wrapper.like(StringUtils.hasText(query.getPatientAnonId()), 
                    CaseInfo::getPatientAnonId, query.getPatientAnonId());
        
        // 闂佸搫鍟悥鐓幬涢崸妤佸殤闁告劑鍔嶇痪顖炴煛鐏炶鍔ユい?
        wrapper.between(query.getStartTime() != null && query.getEndTime() != null,
                       CaseInfo::getExamTime, query.getStartTime(), query.getEndTime());
        
        // 闂佺缈伴崕閬嶅箟閿熺姵鍋愰柤鍝ヮ暯閸嬫挻鎷呴搹鍦嫎缂佺虎鍙庨崰妤呮偂閿涘嫭瀚?
        wrapper.eq(StringUtils.hasText(query.getReportStatus()), 
                  CaseInfo::getReportStatus, query.getReportStatus());
        
        // 缂備礁顦伴崹鐢割敇閼姐倕鍨濋柛鎾椻偓閳ь剚锕㈠濠氬Ψ椤垵娈?
        wrapper.eq(StringUtils.hasText(query.getDepartment()), 
                  CaseInfo::getDepartment, query.getDepartment());
        
        // 闂佸搫瀚烽崹浼村箚娓氣偓瀹曟绮欑捄銊㈠亾閻戣姤鍎夐柛娑卞墰娴兼劙鏌＄仦璇插姤妞?
        wrapper.eq(query.getIsTypical() != null, 
                  CaseInfo::getIsTypical, query.getIsTypical());

        if (Boolean.TRUE.equals(query.getUnassignedOnly())) {
            wrapper.isNull(CaseInfo::getResponsibleDoctorId);
        } else if (SecurityUtils.hasRole("DOCTOR")) {
            wrapper.eq(CaseInfo::getResponsibleDoctorId, SecurityUtils.getCurrentUserId());
        } else if (query.getDoctorId() != null) {
            wrapper.eq(CaseInfo::getResponsibleDoctorId, query.getDoctorId());
        }
        
        // 闂佸湱鍎ょ敮鎺旇姳?
        if ("asc".equalsIgnoreCase(query.getSortOrder())) {
            wrapper.orderByAsc(CaseInfo::getExamTime);
        } else {
            wrapper.orderByDesc(CaseInfo::getExamTime);
        }
        
        // 闂佸湱鐟抽崱鈺傛杸闂佸憡甯掑Λ婵嬪Υ婢舵劕钃熼柕澶樼厛閸?
        Page<CaseInfo> result = caseInfoMapper.selectPage(page, wrapper);
        
        // 闁哄鍎愰崜姘暦閸欏鈻?VO
        List<CaseVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        
        populateResponsibleDoctorNames(voList);

        // 闂佸綊娼х紞濠囧闯濞差亜绀傞悗鍦С缁捇鏌￠崼姘壕闂佸搫鍊绘晶妤佹叏閵堝宸濆┑鐘插暞濞堝爼鏌?
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
                log.warn("闂佽桨鑳舵晶妤€鐣垫笟鈧幊娑㈩敂閸涱厼鈷? caseId={} reportStatus={} 婵炶揪绲藉Λ娆徫涢妶澶婄闁靛鍎查崯鐐烘偣娴ｈ绶茬紓宥呯Ч閺佸秴鐣濋埀顒傜尵閸屾凹娼伴柨婵嗗缁€濠睴NE", caseId, caseInfo.getReportStatus());
                caseInfo.setReportStatus("NONE");
                caseInfo.setUpdatedAt(LocalDateTime.now());
                caseInfoMapper.updateById(caseInfo);
            }
        }

        CaseDetailVO detailVO = new CaseDetailVO();
        BeanUtils.copyProperties(caseInfo, detailVO);
        fillResponsibleDoctor(detailVO, caseInfo.getResponsibleDoctorId());

        log.info("闂佸搫琚崕鎾敋濡ゅ懏鍎夐柛娑卞墰娴兼劙鎮归崶鐑芥闁? caseId={}", caseId);
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
        // 1. 闂佸搫绋勭换婵嬫偘濞嗗精娑㈠焵椤掑嫬钃熼柕澶堝劚婵炲洭鏌涢悜鍡楃仧缂佹梹鎸抽獮鈧?
        LambdaQueryWrapper<CaseInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CaseInfo::getExamNo, dto.getExamNo());
        CaseInfo existing = caseInfoMapper.selectOne(wrapper);
        
        if (existing != null) {
            throw new DuplicateExamNoException(dto.getExamNo());
        }
        
        // 2. 闂佸憡甯楃粙鎴犵磽閹剧粯鍎夐柛娑卞墰娴兼劙鎮楅崷顓炰粧缂傚秴顑夐弫宥夊醇濠婂啠鏋忛梺娲绘娇閸旀垵煤閳哄懏鍤嶉弶鍫氭櫇閺嗩剚绻涙径瀣闁搞倖绮撳畷婵嬪Ω瑜庨弲鎼佹煙?caseId闂?
        CaseInfo caseInfo = new CaseInfo();
        BeanUtils.copyProperties(dto, caseInfo);
        caseInfo.setReportStatus("NONE");
        caseInfo.setIsTypical(0);
        if (SecurityUtils.hasRole("DOCTOR")) {
            caseInfo.setResponsibleDoctorId(SecurityUtils.getCurrentUserId());
        }
        caseInfo.setCreatedAt(LocalDateTime.now());
        caseInfo.setUpdatedAt(LocalDateTime.now());
        
        // 3. 闂佸湱绮敮鎺楀矗閸℃稑鏋侀柣妤€鐗嗙粊锕傚箹?
        caseInfoMapper.insert(caseInfo);
        
        // 4. 闁哄鏅滈弻銊ッ洪弽顓熷仺闁绘梻顭堥悘鍥煟?caseId
        return caseInfo.getCaseId();
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCase(Long caseId, CaseUpdateDTO dto) {
        // 1. 闂佸搫琚崕鎾敋濡ゅ懏鍎夐柛娑卞墰娴兼劙鏌￠崟闈涚仩闁诡垯鑳堕埀顒佺⊕閿氭繝鈧?
        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        if (caseInfo == null) {
            throw new CaseNotFoundException(caseId);
        }
        
        // 2. 闂佸搫娲ら悺銊╁蓟婵犲洤绀傚ù锝囩摂閸熷懎菐閸ヨ泛鏋熼柡浣搞偢閹啴宕熼銈嗘喕濠?
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
        
        // 3. 闂佸搫娲ら悺銊╁蓟婵犲洤绫嶉柛顐ｆ礃閿涚喖鏌?
        caseInfo.setUpdatedAt(LocalDateTime.now());
        
        // 4. 婵烇絽娲︾换鍌炴偤閵娾晛绀嗛柣妤€鐗婂▓鍫曟煙鐠団€虫灈缂?
        caseInfoMapper.updateById(caseInfo);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCase(Long caseId) {
        // 1. 闂佸搫琚崕鎾敋濡ゅ懏鍎夐柛娑卞墰娴兼劙鏌￠崟闈涚仩闁诡垯鑳堕埀顒佺⊕閿氭繝鈧?
        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        if (caseInfo == null) {
            throw new CaseNotFoundException(caseId);
        }
        
        // 2. 濠碘槅鍋€閸嬫捇鏌＄仦璇插姕婵″弶鎮傚畷銉╂晝閳ь剟鎮洪妸鈺佹嵍闁靛鍎遍崵鎺旂磼濞戞瑥鍔ょ憸鏉垮€块獮搴ㄥΨ閵夛箑鏆?
        if ("SIGNED".equals(caseInfo.getReportStatus())) {
            throw new CaseHasSignedReportException();
        }
        
        // 3. 闂佸搫琚崕鎾敋濡ゅ懎绀傞悗鍦С缁捇鏌ｉ妸銉ヮ仾濠⒀冪Ч瀵灚寰勬繝鍌ゆ闂佺绉寸换妤咁敊閸ヮ亗浜?
        LambdaQueryWrapper<ImageInfo> imageWrapper = new LambdaQueryWrapper<>();
        imageWrapper.eq(ImageInfo::getCaseId, caseId);
        List<ImageInfo> images = imageInfoMapper.selectList(imageWrapper);
        
        // 4. 闂佸憡甯炴繛鈧繛?MinIO 婵炴垶鎼╅崢鎯р枔閹达絻浜归柛妤冨仜閸撳ジ鏌￠崒姘煑婵?
        for (ImageInfo image : images) {
            try {
                minioClient.removeObject(
                    RemoveObjectArgs.builder()
                        .bucket(minioBucketName)
                        .object(image.getFilePath())
                        .build()
                );
                log.info("闂佸憡甯炴繛鈧繛鍛噦inIO闂佸搫鍊稿ú锝呪枎閵忋倕绠ｉ柟閭﹀墮椤? {}", image.getFilePath());
            } catch (Exception e) {
                log.error("闂佸憡甯炴繛鈧繛鍛噦inIO闂佸搫鍊稿ú锝呪枎閵忊€崇窞閺夊牜鍋夎: {}", image.getFilePath(), e);
                // 缂傚倷缍€閸涱垱鏆伴梺鍛婂笧婵炩偓婵炲懎閰ｅ畷妤呭嫉閻㈢敻鎼ㄩ梺鍝勫€稿ú锝呪枎閵忋倖鏅悘鐐跺亹閻熸繂鈽夐幙鍐ㄥ箺闁哄苯娲ら湁濞达綀銆€閺?
            }
        }
        
        // 5. 闂佸憡甯炴繛鈧繛鍛唉閵囨劙宕￠悙鎻掑闂佺绻愰崯顖炲汲閻旂厧绠叉い鏃€顑欓崬鍓佹喐?
        if (!images.isEmpty()) {
            imageInfoMapper.delete(imageWrapper);
        }
        
        // 6. 闂佸憡甯炴繛鈧繛鍛叄閹偓闁告侗鍓涙导鎰版偣娴ｈ绶茬紓?
        caseInfoMapper.deleteById(caseId);
        
        log.info("闂佸憡甯炴繛鈧繛鍛叄閹偓闁告侗鍓涙导鎰版煙鐎涙ê濮囧┑? caseId={}, 闂佸憡甯炴繛鈧繛鍛唉閵囨劙宕￠悙鎻掑闂佽桨妞掗崡鎶藉闯?{}", caseId, images.size());
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markTypical(Long caseId, TypicalMarkDTO dto) {
        // 1. 闂佸搫琚崕鎾敋濡ゅ懏鍎夐柛娑卞墰娴兼劙鏌￠崟闈涚仩闁诡垯鑳堕埀顒佺⊕閿氭繝鈧?
        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        if (caseInfo == null) {
            throw new CaseNotFoundException(caseId);
        }
        
        // 2. 闂佸搫娲ら悺銊╁蓟婵犲洤绀傜紒瀣硶閳ь剛鍏橀幆鈧柛娑卞墰娴兼劙鏌″鍛┛妞?
        caseInfo.setIsTypical(dto.getIsTypical());
        caseInfo.setTypicalTags(dto.getTypicalTags());
        caseInfo.setTypicalRemark(dto.getTypicalRemark());
        caseInfo.setUpdatedAt(LocalDateTime.now());
        
        // 3. 婵烇絽娲︾换鍌炴偤閵娾晛绀嗛柣妤€鐗婂▓鍫曟煙鐠団€虫灈缂?
        caseInfoMapper.updateById(caseInfo);
        
        log.info("闂佸搫绉村ú鈺咁敊閸ヮ剙绀傜紒瀣硶閳ь剛鍏橀幆鈧柛娑卞墰娴? caseId={}, isTypical={}, tags={}", 
                caseId, dto.getIsTypical(), dto.getTypicalTags());
    }
    
    /**
     * 闁诲繐绻愬Λ妤呮偪閸曨剚濯撮柟鎹愵潐缁侇噣鏌熺拠鈩冪窔閻?VO
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
            throw new BusinessException(403, "瑜版挸澧犻惀鍛伐鐏忔碍婀崚鍡涘帳鐠愶絼鎹㈤崠鑽ゆ晸");
        }
        if (!currentUserId.equals(caseInfo.getResponsibleDoctorId())) {
            throw new BusinessException(403, "无权操作其他医生负责的病例");
        }
    }

    /**
     * 闂佸綊娼х紞濠囧闯濞差亜绀傞悗鍦С缁捇鏌￠崼姘壕闂佸搫鍊绘晶妤佹叏閵堝宸濆┑鐘插暞濞堝爼鏌熺拠鈥虫灈闁?CaseVO
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
                // 闂佽桨鑳舵晶妤€鐣垫笟鈧幊娑㈩敂閸涱厼鈷愰梺鎸庣⊕閻＄挦se_info 闂佸搫绉村ú鈺咁敊閸ャ劎顩查柛鈩冪⊕婵?NONE 闂佺粯顭堥崺鏍焵椤戞寧顦风紒?report_info 闂佸搫鍟版慨楣冾敊閸ヮ亗浜归柡鍥朵簽缁€澶岀磼閸撗冃ｆい鎺撶矋缁?NONE
                vo.setReportStatus("NONE");
                log.warn("闂佽桨鑳舵晶妤€鐣垫担鍦枖鐎广儱瀚閬嶆煠? caseId={} reportStatus={} 婵炶揪绲藉Λ娆徫涢妶澶婄闁靛鍎查崯鐐烘偣娴ｈ绶茬紓宥呯Ч閺佸秶浠﹂挊澶婃缂備礁澧芥慨鐢割敆濠婂嫮鈻旂紒鈩冦偟NE",
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
        
        // 1. 闂佸搫绋勭换婵嬫偘濞嗘挸妫橀柛銉檮椤愪粙鏌″鍥у付缂?
        if (file == null || file.isEmpty()) {
            throw new BusinessException("涓婁紶鏂囦欢涓嶈兘涓虹┖");
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new BusinessException("璇蜂笂浼?CSV 鏍煎紡鏂囦欢");
        }
        
        // 2. 闁荤喐鐟辩徊楣冩倵?CSV 闂佸搫鍊稿ú锝呪枎?
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            
            // 闁荤姴娲╅褑銇愰崶鈺傚仒闁靛鍎禒?
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new BusinessException("CSV 鏂囦欢涓嶈兘涓虹┖");
            }
            
            // 闂佸搫绋勭换婵嬫偘濞嗘垶鍋橀柕濞垮劘娴犲牓鏌″鍥у付缂?
            if (!isValidHeader(headerLine)) {
                throw new BusinessException("CSV 琛ㄥご涓嶆纭紝搴斾负 examNo,patientAnonId,gender,age,examTime,bodyPart,department");
            }
            
            // 闂備緡鍋呴崝姗€銆侀幋鐘冲珰閻犲洦褰冪徊鍧楁煛娴ｅ搫顣肩€?
            String line;
            int rowNumber = 1; // 婵炲濮村ù鐑筋敄?闁荤偞绋戦懟顖滄閹寸偞鍙忛悗锝呭缁€鍕槈閹惧磭孝閻庡灚锕㈤獮蹇涱敆閸愶腹鍋撻崘鈺佺窞閺夊牆澧界粈?
            
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                result.setTotalRows(result.getTotalRows() + 1);
                
                // 闁荤姴鎼悿鍥╂崲閸愵亞鐭氬Δ锔筋儥閺€?
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                // 闁荤喐鐟辩徊楣冩倵娴犲宓侀悹杞拌閸ゃ倝鏌涜箛瀣姎鐎规洜鍠撻幃鎵沪閻愵剚顔嶉梺?
                try {
                    CaseCreateDTO dto = parseCsvLine(line, rowNumber);
                    createCase(dto);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } catch (DuplicateExamNoException e) {
                    result.setFailedCount(result.getFailedCount() + 1);
                    result.getErrors().add(new ImportError(rowNumber, "濡偓閺屻儱褰块柌宥咁槻"));
                    log.warn("CSV 鐎电厧鍙嗙捄瀹犵箖 - 缁楃憡}鐞? 濡偓閺屻儱褰块柌宥咁槻", rowNumber);
                } catch (Exception e) {
                    result.setFailedCount(result.getFailedCount() + 1);
                    result.getErrors().add(new ImportError(rowNumber, e.getMessage()));
                    log.warn("闁诲海鏁搁崢褔宕ｉ崱妯虹窞閺夊牜鍋夎 - 闁荤偞绋戦惁娈? {}", rowNumber, e.getMessage());
                }
            }
            
            log.info("闂佸綊娼х紞濠囧闯閾忓厜鍋撻悽闈涘付闁告瑥妫涢埀顒傛嚀閺堫剟宕? 闂佽鍓氬Σ鎺椼€侀幋锕€鏋?{}, 闂佺懓鐡ㄩ崝鏇熸叏?{}, 婵犮垺鍎肩划鍓ф喆?{}", 
                    result.getTotalRows(), result.getSuccessCount(), result.getFailedCount());
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("CSV 导入失败", e);
            throw new BusinessException("闂佸綊娼х紞濠囧闯閾忓厜鍋撻悽闈涘付闁告瑥妫欏鍕綇椤愩儛? " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 闂佸搫绋勭换婵嬫偘?CSV 闁荤偞绋忛崝宀勫Φ閺冨牆鍐€闁绘挸娴风涵鈧?
     */
    private boolean isValidHeader(String headerLine) {
        String expectedHeader = "examNo,patientAnonId,gender,age,examTime,bodyPart,department";
        return headerLine.trim().equals(expectedHeader);
    }
    
    /**
     * 闁荤喐鐟辩徊楣冩倵?CSV 闁荤偞绋戦張顒勫汲閻旂厧绠?
     */
    private CaseCreateDTO parseCsvLine(String line, int rowNumber) {
        String[] fields = line.split(",", -1); // -1 婵烇絽娲︾换鍕汲閳ь剛绱掔仦鑺ユ儓闁烩姍鍐ｆ灁?
        
        if (fields.length != 7) {
            throw new BusinessException("CSV 文件格式错误，应为 7 列");
        }
        
        CaseCreateDTO dto = new CaseCreateDTO();
        
        // 濠碘槅鍋€閸嬫捇鏌＄仦璇插姎鐟滄澘娼￠弫宥夊醇濠靛牏鐣辨繝闈涱樈閸嬫挾妲?
        String examNo = fields[0].trim();
        if (examNo.isEmpty()) {
            throw new BusinessException("检查号不能为空");
        }
        dto.setExamNo(examNo);
        
        // 闂佽鍣紞鈧柍褜鍓欓幊搴ｄ焊閸洖瑙︾€光偓缁洭鏌ㄥ☉妯煎缂佺儵鍋撴繝闈涱樈閸嬫挾妲?
        String patientAnonId = fields[1].trim();
        if (patientAnonId.isEmpty()) {
            throw new BusinessException("闂婎偄娲ら幊搴ㄦ晲閻愮鍋撳☉娆樻畷妞ゆ柨鐬肩槐鎾诲传閸曗晙鏉梺鎸庣⊕绾板秹宕戦崨瀛樺殌闁告侗鍘煎畝鎼佹煕濮橆兛绗塂");
        }
        dto.setPatientAnonId(patientAnonId);
        
        // 闂佽鍎搁崘銊у姸闂佹寧绋戦悧鍡氥亹閺屻儲鐒诲鑸电〒缁€?
        String gender = fields[2].trim();
        if (!gender.isEmpty()) {
            dto.setGender(gender);
        }
        
        // 濡ょ姷鍋為幐宕囨閻愮儤鏅柛顐ｇ箓鐠佹煡姊洪銏╂缂?
        String ageStr = fields[3].trim();
        if (!ageStr.isEmpty()) {
            try {
                dto.setAge(Integer.parseInt(ageStr));
            } catch (NumberFormatException e) {
                throw new BusinessException("骞撮緞瀛楁鏍煎紡閿欒");
            }
        }
        
        // 濠碘槅鍋€閸嬫捇鏌＄仦璇插姕婵＄偛鍊垮缁樻綇閸撗咁槱闂婎偄娲ら幊搴ㄦ晲閻愮儤鏅?
        String examTimeStr = fields[4].trim();
        if (examTimeStr.isEmpty()) {
            throw new BusinessException("检查时间格式错误，正确格式为 yyyy-MM-dd HH:mm:ss");
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            dto.setExamTime(LocalDateTime.parse(examTimeStr, formatter));
        } catch (DateTimeParseException e) {
            throw new BusinessException("濠碘槅鍋€閸嬫捇鏌＄仦璇插姕婵＄偛鍊垮鑽も偓娑欘焽婢规劗鈧鍠栫换姗€寮繝鍕珰妞ゆ牓鍊楃粈澶愭煛閸垹鏋戞繝鈧鍫濆唨闁绘挸娴风涵鈧梺鎸庣⊕椤掔yy-MM-dd HH:mm:ss");
        }
        
        // 濠碘槅鍋€閸嬫捇鏌＄仦璇插姦闁稿骸鐡ㄩ幏鍛吋韫囨洜顦╅棅顐㈡搐閹冲酣鏁愰悙鐑樻櫖?
        String bodyPart = fields[5].trim();
        if (bodyPart.isEmpty()) {
            throw new BusinessException("检查部位不能为空");
        }
        dto.setBodyPart(bodyPart);
        
        // 缂備礁顦伴崹鐢割敇婵犳碍鏅柛顐ｇ箓鐠佹煡姊洪銏╂缂?
        String department = fields[6].trim();
        if (!department.isEmpty()) {
            dto.setDepartment(department);
        }
        
        return dto;
    }
}
