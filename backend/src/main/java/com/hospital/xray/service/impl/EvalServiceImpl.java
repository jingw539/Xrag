package com.hospital.xray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hospital.xray.client.AiServiceClient;
import com.hospital.xray.dto.EvalResultVO;
import com.hospital.xray.entity.CriticalAlert;
import com.hospital.xray.entity.EvalResult;
import com.hospital.xray.entity.ImageInfo;
import com.hospital.xray.entity.ReportInfo;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.mapper.AiModelInfoMapper;
import com.hospital.xray.mapper.CriticalAlertMapper;
import com.hospital.xray.mapper.EvalResultMapper;
import com.hospital.xray.mapper.ImageInfoMapper;
import com.hospital.xray.mapper.ReportInfoMapper;
import com.hospital.xray.service.AnnotationService;
import com.hospital.xray.service.EvalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvalServiceImpl implements EvalService {

    private final EvalResultMapper evalResultMapper;
    private final ReportInfoMapper reportInfoMapper;
    private final CriticalAlertMapper criticalAlertMapper;
    private final AiModelInfoMapper aiModelInfoMapper;
    private final ImageInfoMapper imageInfoMapper;
    private final AiServiceClient aiServiceClient;
    private final AnnotationService annotationService;

    private static final List<String> CRITICAL_LABELS = List.of("Pneumothorax", "Pleural Effusion");
    private static final double CRITICAL_THRESHOLD = 0.7;

    @Override
    @Transactional
    public EvalResultVO evaluate(Long reportId) {
        ReportInfo report = reportInfoMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(404, "报告不存在");
        }

        String text = report.getFinalFindings() != null ? report.getFinalFindings() : report.getAiFindings();
        if (text == null || text.isBlank()) {
            throw new BusinessException(400, "报告内容为空，无法评测");
        }

        // 去重：如果已存在该报告的评测且评测内容未变化，直接返回已有结果
        EvalResult latest = evalResultMapper.selectOne(
                new LambdaQueryWrapper<EvalResult>()
                        .eq(EvalResult::getReportId, reportId)
                        .orderByDesc(EvalResult::getEvalTime)
                        .last("LIMIT 1"));
        if (latest != null && text.equals(report.getAiFindings())
                && text.equals(report.getFinalFindings())) {
            log.debug("报告 {} 内容未变化，跳过重复评测", reportId);
            return toVO(latest);
        }

        long startMs = System.currentTimeMillis();
        Map<String, Object> aiResult = aiServiceClient.evaluateWithChexbert(text);
        int elapsedMs = (int) (System.currentTimeMillis() - startMs);

        EvalResult eval = buildEvalFromAiResult(reportId, aiResult, elapsedMs);

        var chexbertModel = aiModelInfoMapper.selectActiveByType("CHEXBERT");
        if (chexbertModel != null) eval.setModelId(chexbertModel.getModelId());

        evalResultMapper.insert(eval);

        String grade = eval.getQualityGrade();
        reportInfoMapper.update(
                null,
                new LambdaUpdateWrapper<ReportInfo>()
                        .eq(ReportInfo::getReportId, reportId)
                        .set(ReportInfo::getQualityGrade, grade)
                        .set(ReportInfo::getUpdatedAt, LocalDateTime.now())
        );

        createCriticalAlertsIfNeeded(report, aiResult);

        generateAnnotationsFromEval(report, eval, aiResult);

        return toVO(eval);
    }

    @Override
    public List<EvalResultVO> getByReportId(Long reportId) {
        return evalResultMapper.selectList(
                        new LambdaQueryWrapper<EvalResult>()
                                .eq(EvalResult::getReportId, reportId)
                                .orderByDesc(EvalResult::getEvalTime))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getEvalStatistics() {
        Map<String, Object> stats = new HashMap<>();
        Map<String, Object> avg = evalResultMapper.selectAvgScoresByType("CHEXBERT");
        stats.put("averageScores", avg);
        stats.put("gradeDistribution", evalResultMapper.selectGradeDistribution("CHEXBERT"));
        return stats;
    }

    @SuppressWarnings("unchecked")
    private EvalResult buildEvalFromAiResult(Long reportId, Map<String, Object> aiResult, int elapsedMs) {
        EvalResult eval = new EvalResult();
        eval.setReportId(reportId);
        eval.setEvalType("CHEXBERT");

        Object f1Obj = aiResult.get("f1_score");
        Object precObj = aiResult.get("precision");
        Object recObj = aiResult.get("recall");
        Object bleuObj = aiResult.get("bleu4");
        Object rougeObj = aiResult.get("rouge_l");

        BigDecimal f1 = f1Obj != null ? new BigDecimal(f1Obj.toString()) : BigDecimal.ZERO;
        BigDecimal precision = precObj != null ? new BigDecimal(precObj.toString()) : BigDecimal.ZERO;
        BigDecimal recall = recObj != null ? new BigDecimal(recObj.toString()) : BigDecimal.ZERO;
        BigDecimal bleu4 = bleuObj != null ? new BigDecimal(bleuObj.toString()) : BigDecimal.ZERO;
        BigDecimal rougeL = rougeObj != null ? new BigDecimal(rougeObj.toString()) : BigDecimal.ZERO;

        eval.setF1Score(f1);
        eval.setPrecisionScore(precision);
        eval.setRecallScore(recall);
        eval.setBleu4Score(bleu4);
        eval.setRougeLScore(rougeL);
        eval.setEvalTime(LocalDateTime.now());

        Object aiLabels = aiResult.get("predicted_labels");
        Object refLabels = aiResult.get("reference_labels");
        Object missing = aiResult.get("missing_labels");
        Object extra = aiResult.get("extra_labels");
        if (aiLabels != null) eval.setAiLabels(aiLabels.toString());
        if (refLabels != null) eval.setRefLabels(refLabels.toString());
        if (missing != null) eval.setMissingLabels(missing.toString());
        if (extra != null) eval.setExtraLabels(extra.toString());

        double f1Val = f1.doubleValue();
        String grade = f1Val >= 0.80 ? "A" : f1Val >= 0.70 ? "B" : f1Val >= 0.50 ? "C" : "D";
        eval.setQualityGrade(grade);

        return eval;
    }

    @SuppressWarnings("unchecked")
    private void generateAnnotationsFromEval(ReportInfo report, EvalResult eval, Map<String, Object> aiResult) {
        try {
            ImageInfo image = imageInfoMapper.selectOne(
                    new LambdaQueryWrapper<ImageInfo>()
                            .eq(ImageInfo::getCaseId, report.getCaseId())
                            .orderByDesc(ImageInfo::getCreatedAt)
                            .last("LIMIT 1"));
            if (image == null) {
                log.debug("病例 {} 无影像，跳过标注生成", report.getCaseId());
                return;
            }

            Map<String, Double> labelProbs = new HashMap<>();
            Object probsObj = aiResult.get("label_probabilities");
            if (probsObj instanceof Map) {
                ((Map<String, Object>) probsObj).forEach((k, v) -> {
                    if (v != null) {
                        try { labelProbs.put(k, Double.parseDouble(v.toString())); }
                        catch (NumberFormatException ignored) {}
                    }
                });
            }

            annotationService.deleteAiAnnotations(image.getImageId(), report.getReportId());
            annotationService.generateAiAnnotationsWithConfidence(
                    image.getImageId(), report.getReportId(),
                    eval.getAiLabels(), labelProbs.isEmpty() ? null : labelProbs);
        } catch (Exception e) {
            log.warn("评测后自动生成标注失败 reportId={}: {}", report.getReportId(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void createCriticalAlertsIfNeeded(ReportInfo report, Map<String, Object> aiResult) {
        Object labelsObj = aiResult.get("label_probabilities");
        if (!(labelsObj instanceof Map)) return;

        Map<String, Object> labelProbs = (Map<String, Object>) labelsObj;
        for (String label : CRITICAL_LABELS) {
            Object probObj = labelProbs.get(label);
            if (probObj == null) continue;
            double prob = Double.parseDouble(probObj.toString());
            if (prob > CRITICAL_THRESHOLD) {
                CriticalAlert alert = new CriticalAlert();
                alert.setCaseId(report.getCaseId());
                alert.setReportId(report.getReportId());
                alert.setLabelType(label);
                alert.setLabelProb(BigDecimal.valueOf(prob));
                alert.setAlertStatus("PENDING");
                alert.setAlertTime(LocalDateTime.now());
                criticalAlertMapper.insert(alert);
                log.info("危急值预警已创建: caseId={}, label={}, prob={}", report.getCaseId(), label, prob);
            }
        }
    }

    private EvalResultVO toVO(EvalResult e) {
        EvalResultVO vo = new EvalResultVO();
        vo.setEvalId(e.getEvalId());
        vo.setReportId(e.getReportId());
        vo.setEvalType(e.getEvalType());
        vo.setPrecisionScore(e.getPrecisionScore());
        vo.setRecallScore(e.getRecallScore());
        vo.setF1Score(e.getF1Score());
        vo.setBleu4Score(e.getBleu4Score());
        vo.setRougeLScore(e.getRougeLScore());
        vo.setQualityGrade(e.getQualityGrade());
        vo.setAiLabels(e.getAiLabels());
        vo.setCreatedAt(e.getEvalTime());
        if (e.getMissingLabels() != null && !e.getMissingLabels().isBlank()) {
            vo.setMissingLabels(Arrays.stream(e.getMissingLabels().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
        }
        if (e.getExtraLabels() != null && !e.getExtraLabels().isBlank()) {
            vo.setExtraLabels(Arrays.stream(e.getExtraLabels().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
        }
        return vo;
    }
}
