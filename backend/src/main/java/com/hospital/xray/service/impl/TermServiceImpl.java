package com.hospital.xray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.xray.client.AiServiceClient;
import com.hospital.xray.dto.TermCorrectionVO;
import com.hospital.xray.entity.ReportInfo;
import com.hospital.xray.entity.TermCorrection;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.mapper.ReportInfoMapper;
import com.hospital.xray.mapper.TermCorrectionMapper;
import com.hospital.xray.service.TermService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TermServiceImpl implements TermService {

    private final TermCorrectionMapper termCorrectionMapper;
    private final ReportInfoMapper reportInfoMapper;
    private final AiServiceClient aiServiceClient;

    @Override
    @Transactional
    public List<TermCorrectionVO> analyzeReport(Long reportId, String draftFindings, String draftImpression) {
        ReportInfo report = reportInfoMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(404, "报告不存在");
        }
        
        // Use draft text if provided, otherwise use database text
        String findingsText = draftFindings != null ? draftFindings : 
            (report.getFinalFindings() != null ? report.getFinalFindings() : report.getAiFindings());
        String impressionText = draftImpression != null ? draftImpression : 
            (report.getFinalImpression() != null ? report.getFinalImpression() : report.getAiImpression());
        
        // Combine both texts for analysis
        String text = "";
        if (findingsText != null && !findingsText.isBlank()) {
            text += findingsText;
        }
        if (impressionText != null && !impressionText.isBlank()) {
            if (!text.isEmpty()) text += "\n";
            text += impressionText;
        }
        
        if (text.isBlank()) {
            return Collections.emptyList();
        }

        termCorrectionMapper.delete(new LambdaQueryWrapper<TermCorrection>()
                .eq(TermCorrection::getReportId, reportId));

        Map<String, Object> result;
        try {
            result = aiServiceClient.analyzeTerms(text);
        } catch (BusinessException e) {
            log.warn("术语分析AI服务不可用，跳过: {}", e.getMessage());
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> corrections = (List<Map<String, Object>>) result.get("corrections");
        if (corrections == null || corrections.isEmpty()) {
            return Collections.emptyList();
        }

        List<TermCorrection> entities = corrections.stream().map(c -> {
            TermCorrection tc = new TermCorrection();
            tc.setReportId(reportId);
            tc.setOriginalTerm(String.valueOf(c.get("original_term")));
            tc.setSuggestedTerm(String.valueOf(c.get("suggested_term")));
            tc.setContextSentence(c.get("context") != null ? String.valueOf(c.get("context")) : null);
            tc.setIsAccepted(0);
            tc.setCreatedAt(LocalDateTime.now());
            return tc;
        }).collect(Collectors.toList());

        entities.forEach(termCorrectionMapper::insert);
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<TermCorrectionVO> getByReportId(Long reportId) {
        return termCorrectionMapper.selectList(
                new LambdaQueryWrapper<TermCorrection>().eq(TermCorrection::getReportId, reportId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void acceptCorrection(Long correctionId) {
        updateAccepted(correctionId, 1);
    }

    @Override
    @Transactional
    public void dismissCorrection(Long correctionId) {
        updateAccepted(correctionId, -1);
    }

    private void updateAccepted(Long correctionId, int value) {
        TermCorrection tc = termCorrectionMapper.selectById(correctionId);
        if (tc == null) {
            throw new BusinessException(404, "术语校正记录不存在");
        }
        tc.setIsAccepted(value);
        termCorrectionMapper.updateById(tc);
    }

    private TermCorrectionVO toVO(TermCorrection tc) {
        TermCorrectionVO vo = new TermCorrectionVO();
        vo.setCorrectionId(tc.getCorrectionId());
        vo.setReportId(tc.getReportId());
        vo.setOriginalTerm(tc.getOriginalTerm());
        vo.setSuggestedTerm(tc.getSuggestedTerm());
        vo.setContextSentence(tc.getContextSentence());
        vo.setIsAccepted(tc.getIsAccepted());
        vo.setCreatedAt(tc.getCreatedAt());
        return vo;
    }
}
