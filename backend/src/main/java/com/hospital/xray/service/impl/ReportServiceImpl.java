package com.hospital.xray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.xray.client.AiServiceClient;
import com.hospital.xray.dto.*;
import com.hospital.xray.entity.*;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.mapper.*;
import com.hospital.xray.service.ImageService;
import com.hospital.xray.service.ReportService;
import com.hospital.xray.service.RetrievalService;
import com.hospital.xray.service.TermService;
import com.hospital.xray.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportInfoMapper reportInfoMapper;
    private final ReportEditHistoryMapper editHistoryMapper;
    private final SysUserMapper sysUserMapper;
    private final AiModelInfoMapper aiModelInfoMapper;
    private final RetrievalLogMapper retrievalLogMapper;
    private final CaseInfoMapper caseInfoMapper;
    private final AiServiceClient aiServiceClient;
    private final ImageService imageService;
    private final RetrievalService retrievalService;
    private final TermService termService;

    @Override
    @Transactional
    public ReportDetailVO generate(ReportGenerateDTO dto, Long doctorId) {
        assertDoctorOwnsCase(dto.getCaseId(), true);
        RetrievalResultVO retrieval = retrievalService.search(dto.getCaseId(), dto.getImageId(),
                dto.getTopK() != null ? dto.getTopK() : 3);

        List<Map<String, Object>> similarCasesForPrompt = retrieval.getSimilarCases().stream()
                .map(sc -> Map.<String, Object>of(
                        "case_id", sc.getCaseId() != null ? sc.getCaseId() : 0,
                        "findings", sc.getFindings() != null ? sc.getFindings() : "",
                        "impression", sc.getImpression() != null ? sc.getImpression() : "",
                        "similarity_score", sc.getSimilarityScore() != null ? sc.getSimilarityScore() : 0))
                .collect(Collectors.toList());

        String imageDataUrl;
        try {
            imageDataUrl = imageService.getImageAsDataUrl(dto.getImageId());
        } catch (Exception e) {
            log.warn("获取影像 Data URL 失败，生成报告时将不附带图像内容: {}", e.getMessage());
            imageDataUrl = null;
        }

        long startMs = System.currentTimeMillis();
        Map<String, Object> genResult;
        try {
            genResult = aiServiceClient.generateReport(imageDataUrl, similarCasesForPrompt);
        } catch (BusinessException e) {
            log.warn("AI 报告生成失败，已回退为人工撰写模式: {}", e.getMessage());
            genResult = Map.of(
                    "findings", "AI service unavailable - please write manually.",
                    "impression", "",
                    "prompt", "",
                    "confidence", 0.0);
        }
        int elapsedMs = (int) (System.currentTimeMillis() - startMs);

        ReportInfo report = new ReportInfo();
        report.setCaseId(dto.getCaseId());
        report.setReportStatus("AI_DRAFT");
        report.setRetrievalLogId(retrieval.getRetrievalId());
        report.setAiFindings(getStr(genResult, "findings"));
        report.setAiImpression(getStr(genResult, "impression"));
        report.setAiPrompt(getStr(genResult, "prompt"));
        report.setFinalFindings(report.getAiFindings());
        report.setFinalImpression(report.getAiImpression());

        Object confObj = genResult.get("confidence");
        if (confObj != null) {
            try {
                report.setModelConfidence(new BigDecimal(confObj.toString()));
            } catch (NumberFormatException ignored) {}
        }

        String caseIds = retrieval.getSimilarCases().stream()
                .map(sc -> String.valueOf(sc.getCaseId()))
                .collect(Collectors.joining(","));
        report.setSimilarCaseIds(caseIds);
        report.setDoctorId(doctorId);
        report.setAiGenerateTime(LocalDateTime.now());
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());

        AiModelInfo genModel = aiModelInfoMapper.selectActiveByType("REPORT_GEN");
        if (genModel != null) report.setGenModelId(genModel.getModelId());

        reportInfoMapper.insert(report);

        CaseInfo caseInfo = caseInfoMapper.selectById(dto.getCaseId());
        if (caseInfo != null) {
            caseInfo.setReportStatus("AI_DRAFT");
            caseInfo.setUpdatedAt(LocalDateTime.now());
            caseInfoMapper.updateById(caseInfo);
        }

        return getById(report.getReportId());
    }

    @Override
    @Transactional
    public ReportDetailVO regenerate(Long reportId, Long doctorId) {
        ReportInfo existing = reportInfoMapper.selectById(reportId);
        assertDoctorOwnsCase(existing != null ? existing.getCaseId() : null, false);
        if (existing == null) throw new BusinessException(404, "Report not found");
        if ("SIGNED".equals(existing.getReportStatus())) {
            throw new BusinessException(400, "Signed report cannot be regenerated");
        }
        Long imageId = null;
        if (existing.getRetrievalLogId() != null) {
            RetrievalLog retrievalLog = retrievalLogMapper.selectById(existing.getRetrievalLogId());
            if (retrievalLog != null) imageId = retrievalLog.getQueryImageId();
        }
        if (imageId == null) throw new BusinessException(400, "Source image not found for regeneration");

        // 删除旧报告关联数据后再重新生成，避免脏数据残留

        ReportGenerateDTO dto = new ReportGenerateDTO();
        dto.setCaseId(existing.getCaseId());
        dto.setImageId(imageId);
        dto.setTopK(3);
        return generate(dto, doctorId);
    }

    @Override
    @Transactional
    public void saveDraft(Long reportId, ReportSaveDTO dto, Long editorId) {
        ReportInfo report = reportInfoMapper.selectById(reportId);
        assertDoctorOwnsCase(report != null ? report.getCaseId() : null, false);
        if (report == null) throw new BusinessException(404, "Report not found");
        if ("SIGNED".equals(report.getReportStatus())) {
            throw new BusinessException(400, "Signed report cannot be edited");
        }

        boolean findingsChanged = StringUtils.hasText(dto.getFinalFindings())
                && !dto.getFinalFindings().equals(report.getFinalFindings());
        boolean impressionChanged = StringUtils.hasText(dto.getFinalImpression())
                && !dto.getFinalImpression().equals(report.getFinalImpression());

        String oldFindings = report.getFinalFindings();
        String oldImpression = report.getFinalImpression();

        if (findingsChanged) {
            report.setFinalFindings(dto.getFinalFindings());
        }
        if (impressionChanged) {
            report.setFinalImpression(dto.getFinalImpression());
        }

        report.setReportStatus("EDITING");
        report.setUpdatedAt(LocalDateTime.now());
        reportInfoMapper.updateById(report);

        CaseInfo caseInfo = caseInfoMapper.selectById(report.getCaseId());
        if (caseInfo != null && !"SIGNED".equals(caseInfo.getReportStatus())) {
            caseInfo.setReportStatus("EDITING");
            caseInfo.setUpdatedAt(LocalDateTime.now());
            caseInfoMapper.updateById(caseInfo);
        }

        if (findingsChanged || impressionChanged) {
            ReportEditHistory history = new ReportEditHistory();
            history.setReportId(reportId);
            history.setEditorId(editorId);
            history.setFindingsBefore(oldFindings);
            history.setFindingsAfter(findingsChanged ? dto.getFinalFindings() : oldFindings);
            history.setImpressionBefore(oldImpression);
            history.setImpressionAfter(impressionChanged ? dto.getFinalImpression() : oldImpression);
            history.setEditNote(dto.getEditNote());
            history.setEditTime(LocalDateTime.now());
            editHistoryMapper.insert(history);
        }
    }

    @Override
    @Transactional
    public void sign(Long reportId, Long doctorId) {
        ReportInfo report = reportInfoMapper.selectById(reportId);
        assertDoctorOwnsCase(report != null ? report.getCaseId() : null, false);
        if (report == null) throw new BusinessException(404, "Report not found");
        if ("SIGNED".equals(report.getReportStatus())) {
            throw new BusinessException(400, "Report already signed");
        }
        report.setReportStatus("SIGNED");
        report.setDoctorId(doctorId);
        report.setSignTime(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());
        reportInfoMapper.updateById(report);

        CaseInfo caseInfo = caseInfoMapper.selectById(report.getCaseId());
        if (caseInfo != null) {
            caseInfo.setReportStatus("SIGNED");
            caseInfo.setUpdatedAt(LocalDateTime.now());
            caseInfoMapper.updateById(caseInfo);
        }
    }

    @Override
    public ReportDetailVO getById(Long reportId) {
        ReportInfo report = reportInfoMapper.selectById(reportId);
        assertDoctorOwnsCase(report != null ? report.getCaseId() : null, false);
        if (report == null) throw new BusinessException(404, "Report not found");
        return toDetailVO(report);
    }

    @Override
    public PageResult<ReportVO> listReports(ReportQueryDTO queryDTO) {
        if (queryDTO.getCaseId() != null) {
            assertDoctorOwnsCase(queryDTO.getCaseId(), false);
        } else if (SecurityUtils.hasRole("DOCTOR")) {
            queryDTO.setDoctorId(SecurityUtils.getCurrentUserId());
        }
        LambdaQueryWrapper<ReportInfo> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getCaseId() != null) wrapper.eq(ReportInfo::getCaseId, queryDTO.getCaseId());
        if (StringUtils.hasText(queryDTO.getReportStatus())) wrapper.eq(ReportInfo::getReportStatus, queryDTO.getReportStatus());
        if (StringUtils.hasText(queryDTO.getQualityGrade())) wrapper.eq(ReportInfo::getQualityGrade, queryDTO.getQualityGrade());
        if (queryDTO.getDoctorId() != null) wrapper.eq(ReportInfo::getDoctorId, queryDTO.getDoctorId());
        if (StringUtils.hasText(queryDTO.getStartDate())) {
            wrapper.ge(ReportInfo::getCreatedAt, LocalDate.parse(queryDTO.getStartDate()).atStartOfDay());
        }
        if (StringUtils.hasText(queryDTO.getEndDate())) {
            wrapper.le(ReportInfo::getCreatedAt, LocalDate.parse(queryDTO.getEndDate()).atTime(23, 59, 59));
        }
        wrapper.orderByDesc(ReportInfo::getCreatedAt);

        Page<ReportInfo> page = new Page<>(queryDTO.getPage(), queryDTO.getPageSize());
        Page<ReportInfo> result = reportInfoMapper.selectPage(page, wrapper);

        List<Long> doctorIds = result.getRecords().stream()
                .map(ReportInfo::getDoctorId).filter(java.util.Objects::nonNull)
                .distinct().collect(Collectors.toList());
        Map<Long, String> doctorNameMap = doctorIds.isEmpty() ? Collections.emptyMap() :
                sysUserMapper.selectBatchIds(doctorIds).stream()
                        .collect(Collectors.toMap(u -> u.getUserId(), u -> u.getRealName() != null ? u.getRealName() : ""));

        List<ReportVO> list = result.getRecords().stream()
                .map(r -> toVO(r, doctorNameMap)).collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public List<ReportEditHistoryVO> getEditHistory(Long reportId) {
        ReportInfo report = reportInfoMapper.selectById(reportId);
        if (report == null) throw new BusinessException(404, "Report not found");
        assertDoctorOwnsCase(report.getCaseId(), false);
        return editHistoryMapper.selectList(
                        new LambdaQueryWrapper<ReportEditHistory>()
                                .eq(ReportEditHistory::getReportId, reportId)
                                .orderByDesc(ReportEditHistory::getEditTime))
                .stream().map(this::toHistoryVO).collect(Collectors.toList());
    }

    private ReportVO toVO(ReportInfo r) {
        return toVO(r, null);
    }

    private ReportVO toVO(ReportInfo r, Map<Long, String> doctorNameCache) {
        ReportVO vo = new ReportVO();
        vo.setReportId(r.getReportId());
        vo.setCaseId(r.getCaseId());
        vo.setReportStatus(r.getReportStatus());
        vo.setAiFindings(r.getAiFindings());
        vo.setAiImpression(r.getAiImpression());
        vo.setFinalFindings(r.getFinalFindings());
        vo.setFinalImpression(r.getFinalImpression());
        vo.setQualityGrade(r.getQualityGrade());
        vo.setModelConfidence(r.getModelConfidence());
        vo.setSimilarCaseIds(r.getSimilarCaseIds());
        vo.setDoctorId(r.getDoctorId());
        vo.setSignTime(r.getSignTime());
        vo.setAiGenerateTime(r.getAiGenerateTime());
        vo.setCreatedAt(r.getCreatedAt());
        if (r.getDoctorId() != null) {
            if (doctorNameCache != null) {
                vo.setDoctorName(doctorNameCache.get(r.getDoctorId()));
            } else {
                var doctor = sysUserMapper.selectById(r.getDoctorId());
                if (doctor != null) vo.setDoctorName(doctor.getRealName());
            }
        }
        return vo;
    }

    private ReportDetailVO toDetailVO(ReportInfo r) {
        ReportDetailVO vo = new ReportDetailVO();
        vo.setReportId(r.getReportId());
        vo.setCaseId(r.getCaseId());
        vo.setReportStatus(r.getReportStatus());
        vo.setAiFindings(r.getAiFindings());
        vo.setAiImpression(r.getAiImpression());
        vo.setAiPrompt(r.getAiPrompt());
        vo.setFinalFindings(r.getFinalFindings());
        vo.setFinalImpression(r.getFinalImpression());
        vo.setQualityGrade(r.getQualityGrade());
        vo.setModelConfidence(r.getModelConfidence());
        vo.setDoctorId(r.getDoctorId());
        vo.setSignTime(r.getSignTime());
        vo.setAiGenerateTime(r.getAiGenerateTime());
        vo.setCreatedAt(r.getCreatedAt());
        if (r.getSimilarCaseIds() != null && !r.getSimilarCaseIds().isBlank()) {
            vo.setSimilarCaseIds(Arrays.stream(r.getSimilarCaseIds().split(","))
                    .map(Long::parseLong).collect(Collectors.toList()));
        } else {
            vo.setSimilarCaseIds(Collections.emptyList());
        }
        if (r.getDoctorId() != null) {
            var doctor = sysUserMapper.selectById(r.getDoctorId());
            if (doctor != null) vo.setDoctorName(doctor.getRealName());
        }
        vo.setEditHistory(getEditHistory(r.getReportId()));
        try {
            vo.setTermCorrections(termService.getByReportId(r.getReportId()));
        } catch (Exception e) {
            log.warn("Failed to get term corrections for report {}: {}", r.getReportId(), e.getMessage());
        }
        return vo;
    }

    private ReportEditHistoryVO toHistoryVO(ReportEditHistory h) {
        ReportEditHistoryVO vo = new ReportEditHistoryVO();
        vo.setHistoryId(h.getHistoryId());
        vo.setReportId(h.getReportId());
        vo.setEditorId(h.getEditorId());
        vo.setFindingsBefore(h.getFindingsBefore());
        vo.setFindingsAfter(h.getFindingsAfter());
        vo.setImpressionBefore(h.getImpressionBefore());
        vo.setImpressionAfter(h.getImpressionAfter());
        vo.setEditNote(h.getEditNote());
        vo.setEditTime(h.getEditTime());
        if (h.getEditorId() != null) {
            var editor = sysUserMapper.selectById(h.getEditorId());
            if (editor != null) vo.setEditorName(editor.getRealName());
        }
        return vo;
    }

    @Override
    public Map<String, Object> polishReport(String findings, String impression) {
        return aiServiceClient.polishReport(
                findings != null ? findings : "",
                impression != null ? impression : "");
    }

    @Override
    @Transactional
    public void revertToEdit(Long reportId, Long doctorId) {
        ReportInfo report = reportInfoMapper.selectById(reportId);
        assertDoctorOwnsCase(report != null ? report.getCaseId() : null, false);
        if (report == null) throw new BusinessException(404, "Report not found");
        if (!"SIGNED".equals(report.getReportStatus())) {
            throw new BusinessException(400, "Only signed reports can be reverted");
        }
        report.setReportStatus("EDITING");
        report.setSignTime(null);
        report.setUpdatedAt(LocalDateTime.now());
        reportInfoMapper.updateById(report);

        CaseInfo caseInfo = caseInfoMapper.selectById(report.getCaseId());
        if (caseInfo != null) {
            caseInfo.setReportStatus("EDITING");
            caseInfo.setUpdatedAt(LocalDateTime.now());
            caseInfoMapper.updateById(caseInfo);
        }
    }

    @Override
    public Map<String, Object> getAiAdvice(Long reportId) {
        ReportInfo report = reportInfoMapper.selectById(reportId);
        assertDoctorOwnsCase(report != null ? report.getCaseId() : null, false);
        if (report == null) throw new BusinessException(404, "Report not found");

        return aiServiceClient.getReviewAdvice(
                report.getFinalFindings(),
                report.getFinalImpression(),
                report.getQualityGrade(),
                null,
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    private String getStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }

    private void assertDoctorOwnsCase(Long caseId, boolean allowClaimPrompt) {
        if (!SecurityUtils.hasRole("DOCTOR")) {
            return;
        }
        if (caseId == null) {
            throw new BusinessException(404, "Case not found");
        }

        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        if (caseInfo == null) {
            throw new BusinessException(404, "Case not found");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (caseInfo.getResponsibleDoctorId() == null) {
            throw new BusinessException(403, allowClaimPrompt ? "Please claim this case first" : "Cannot operate on unclaimed case");
        }
        if (!caseInfo.getResponsibleDoctorId().equals(currentUserId)) {
            throw new BusinessException(403, "Cannot operate on another doctor case");
        }
    }
}
