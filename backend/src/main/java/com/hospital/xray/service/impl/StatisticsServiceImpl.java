package com.hospital.xray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.xray.dto.StatisticsVO;
import com.hospital.xray.entity.CaseInfo;
import com.hospital.xray.entity.EvalResult;
import com.hospital.xray.entity.ReportInfo;
import com.hospital.xray.mapper.*;
import com.hospital.xray.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final CaseInfoMapper caseInfoMapper;
    private final ReportInfoMapper reportInfoMapper;
    private final EvalResultMapper evalResultMapper;
    private final CriticalAlertMapper criticalAlertMapper;

    @Override
    public StatisticsVO getOverview() {
        long totalCases = caseInfoMapper.selectCount(null);
        long totalReports = reportInfoMapper.selectCount(null);
        long signedReports = reportInfoMapper.selectCount(
                new LambdaQueryWrapper<ReportInfo>().eq(ReportInfo::getReportStatus, "SIGNED"));
        long pendingAlerts = criticalAlertMapper.countPending();

        Map<String, Object> avgScores = evalResultMapper.selectAvgScoresByType("CHEXBERT");
        BigDecimal avgF1 = avgScores != null && avgScores.get("avgF1") != null
                ? new BigDecimal(avgScores.get("avgF1").toString()) : BigDecimal.ZERO;
        BigDecimal avgBleu4 = avgScores != null && avgScores.get("avgBleu4") != null
                ? new BigDecimal(avgScores.get("avgBleu4").toString()) : BigDecimal.ZERO;
        BigDecimal avgRougeL = avgScores != null && avgScores.get("avgRougeL") != null
                ? new BigDecimal(avgScores.get("avgRougeL").toString()) : BigDecimal.ZERO;

        List<Map<String, Object>> gradeDist = evalResultMapper.selectGradeDistribution("CHEXBERT");
        Map<String, Long> gradeMap = new HashMap<>();
        if (gradeDist != null) {
            gradeDist.forEach(row -> gradeMap.put(
                    String.valueOf(row.get("quality_grade")),
                    Long.parseLong(row.get("cnt").toString())));
        }

        List<Map<String, Object>> alertDist = criticalAlertMapper.selectAlertTypeStats();
        Map<String, Long> alertMap = new HashMap<>();
        if (alertDist != null) {
            alertDist.forEach(row -> alertMap.put(
                    String.valueOf(row.get("label_type")),
                    Long.parseLong(row.get("cnt").toString())));
        }

        Map<String, Long> statusMap = new HashMap<>();
        for (String status : List.of("NONE", "AI_DRAFT", "EDITING", "SIGNED")) {
            long cnt = reportInfoMapper.selectCount(
                    new LambdaQueryWrapper<ReportInfo>().eq(ReportInfo::getReportStatus, status));
            statusMap.put(status, cnt);
        }

        Map<String, Object> adoptionStats = evalResultMapper.selectAdoptionStats("CHEXBERT");
        BigDecimal adoptionRate = BigDecimal.ZERO;
        if (adoptionStats != null && adoptionStats.get("totalEvals") != null) {
            long total = Long.parseLong(adoptionStats.get("totalEvals").toString());
            long adopted = adoptionStats.get("adoptedCount") != null
                    ? Long.parseLong(adoptionStats.get("adoptedCount").toString()) : 0;
            if (total > 0) adoptionRate = new BigDecimal(adopted).divide(new BigDecimal(total), 4, java.math.RoundingMode.HALF_UP);
        }

        Map<String, Object> genTimeStats = evalResultMapper.selectAvgGenTimeMs();
        Long avgGenTimeMs = null;
        if (genTimeStats != null && genTimeStats.get("avgGenMs") != null) {
            avgGenTimeMs = Math.round(Double.parseDouble(genTimeStats.get("avgGenMs").toString()));
        }

        return StatisticsVO.builder()
                .totalCases(totalCases)
                .totalReports(totalReports)
                .signedReports(signedReports)
                .pendingAlerts(pendingAlerts)
                .avgF1Score(avgF1)
                .avgBleu4Score(avgBleu4)
                .avgRougeLScore(avgRougeL)
                .adoptionRate(adoptionRate)
                .avgGenTimeMs(avgGenTimeMs)
                .gradeDistribution(gradeMap)
                .alertTypeStats(alertMap)
                .reportStatusStats(statusMap)
                .build();
    }

    @Override
    public List<Map<String, Object>> getReportTrend(String startDate, String endDate, String groupBy) {
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();

        List<ReportInfo> reports = reportInfoMapper.selectList(
                new LambdaQueryWrapper<ReportInfo>()
                        .ge(ReportInfo::getCreatedAt, start.atStartOfDay())
                        .le(ReportInfo::getCreatedAt, end.atTime(23, 59, 59))
                        .orderByAsc(ReportInfo::getCreatedAt));

        Map<String, Long> grouped = reports.stream().collect(Collectors.groupingBy(
                r -> {
                    LocalDate d = r.getCreatedAt().toLocalDate();
                    if ("month".equals(groupBy)) return d.withDayOfMonth(1).toString();
                    if ("week".equals(groupBy))  return d.with(java.time.DayOfWeek.MONDAY).toString();
                    return d.toString();
                },
                Collectors.counting()));

        List<Map<String, Object>> trend = new ArrayList<>();
        grouped.forEach((date, count) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("date", date);
            item.put("count", count);
            trend.add(item);
        });
        trend.sort((a, b) -> a.get("date").toString().compareTo(b.get("date").toString()));
        return trend;
    }

    @Override
    public List<Map<String, Object>> getEvalTrend(String startDate, String endDate, String groupBy) {
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusMonths(6);
        LocalDate end   = endDate   != null ? LocalDate.parse(endDate)   : LocalDate.now();

        List<Map<String, Object>> raw;
        String dateKey;
        if ("month".equals(groupBy)) {
            raw = evalResultMapper.selectEvalTrendByMonth(start.atStartOfDay(), end.atTime(23, 59, 59));
            dateKey = "eval_month";
        } else if ("week".equals(groupBy)) {
            raw = evalResultMapper.selectEvalTrendByWeek(start.atStartOfDay(), end.atTime(23, 59, 59));
            dateKey = "eval_week";
        } else {
            raw = evalResultMapper.selectEvalTrend(start.atStartOfDay(), end.atTime(23, 59, 59));
            dateKey = "eval_date";
        }
        if (raw == null) return List.of();

        List<Map<String, Object>> trend = new ArrayList<>();
        raw.forEach(row -> {
            Map<String, Object> item = new HashMap<>();
            Object dateVal = row.get(dateKey);
            if ("month".equals(groupBy) && dateVal != null) {
                item.put("date", dateVal.toString() + "-01");
            } else {
                item.put("date", String.valueOf(dateVal));
            }
            item.put("avgF1",  row.get("avg_f1")   != null ? new BigDecimal(row.get("avg_f1").toString()) : BigDecimal.ZERO);
            item.put("avgBleu4", row.get("avg_bleu4") != null ? new BigDecimal(row.get("avg_bleu4").toString()) : BigDecimal.ZERO);
            trend.add(item);
        });
        trend.sort((a, b) -> a.get("date").toString().compareTo(b.get("date").toString()));
        return trend;
    }

    private static final List<String> CHEXPERT_LABELS = List.of(
            "Atelectasis", "Cardiomegaly", "Consolidation", "Edema",
            "Enlarged Cardiomediastinum", "Fracture", "Lung Lesion", "Lung Opacity",
            "No Finding", "Pleural Effusion", "Pleural Other", "Pneumonia",
            "Pneumothorax", "Support Devices");

    @Override
    public List<Map<String, Object>> getPerLabelStats() {
        List<com.hospital.xray.entity.EvalResult> evals = evalResultMapper.selectList(
                new LambdaQueryWrapper<com.hospital.xray.entity.EvalResult>()
                        .eq(com.hospital.xray.entity.EvalResult::getEvalType, "CHEXBERT")
                        .isNotNull(com.hospital.xray.entity.EvalResult::getAiLabels));

        int total = evals.size();
        Map<String, Integer> positiveCount = new HashMap<>();
        Map<String, Integer> missingCount = new HashMap<>();
        Map<String, Integer> extraCount = new HashMap<>();
        for (String label : CHEXPERT_LABELS) {
            positiveCount.put(label, 0);
            missingCount.put(label, 0);
            extraCount.put(label, 0);
        }

        for (com.hospital.xray.entity.EvalResult e : evals) {
            String raw = e.getAiLabels();
            if (raw == null) continue;
            String cleaned = raw.replaceAll("^\\[|]$", "");
            List<String> predicted = Arrays.stream(cleaned.split(","))
                    .map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toList());
            for (String label : CHEXPERT_LABELS) {
                boolean found = predicted.stream().anyMatch(p -> p.equalsIgnoreCase(label));
                if (found) positiveCount.merge(label, 1, Integer::sum);
            }
            if (e.getMissingLabels() != null && !e.getMissingLabels().isBlank()) {
                Arrays.stream(e.getMissingLabels().split(",")).map(String::trim).filter(s -> !s.isBlank())
                        .forEach(l -> missingCount.merge(l, 1, Integer::sum));
            }
            if (e.getExtraLabels() != null && !e.getExtraLabels().isBlank()) {
                Arrays.stream(e.getExtraLabels().split(",")).map(String::trim).filter(s -> !s.isBlank())
                        .forEach(l -> extraCount.merge(l, 1, Integer::sum));
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String label : CHEXPERT_LABELS) {
            int tp = positiveCount.getOrDefault(label, 0);
            int fn = missingCount.getOrDefault(label, 0);
            int fp = extraCount.getOrDefault(label, 0);
            double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0.0;
            double recall    = (tp + fn) > 0 ? (double) tp / (tp + fn) : (total > 0 ? (double) tp / total : 0.0);
            double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0.0;
            Map<String, Object> item = new HashMap<>();
            item.put("label", label);
            item.put("precision", new BigDecimal(String.format("%.4f", precision)));
            item.put("recall",    new BigDecimal(String.format("%.4f", recall)));
            item.put("f1",        new BigDecimal(String.format("%.4f", f1)));
            item.put("count", tp);
            item.put("total", total);
            result.add(item);
        }
        result.sort((a, b) -> Double.compare(
                ((BigDecimal) b.get("f1")).doubleValue(),
                ((BigDecimal) a.get("f1")).doubleValue()));
        return result;
    }

    @Override
    public List<Map<String, Object>> getModelVersionComparison() {
        List<Map<String, Object>> raw = evalResultMapper.selectModelVersionComparison();
        if (raw == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : raw) {
            Map<String, Object> item = new HashMap<>();
            item.put("modelVersion", String.valueOf(row.get("model_version")));
            item.put("evalCount",    row.get("eval_count") != null ? Long.parseLong(row.get("eval_count").toString()) : 0L);
            for (String[] pair : new String[][]{{"avgPrecision","avg_precision"},{"avgRecall","avg_recall"},{"avgF1","avg_f1"},{"avgBleu4","avg_bleu4"}}) {
                Object val = row.get(pair[1]);
                item.put(pair[0], val != null ? new BigDecimal(val.toString()).setScale(4, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO);
            }
            result.add(item);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getQualityIssues(int limit) {
        List<EvalResult> lowQuality = evalResultMapper.selectList(
                new LambdaQueryWrapper<EvalResult>()
                        .in(EvalResult::getQualityGrade, "C", "D")
                        .orderByDesc(EvalResult::getEvalTime)
                        .last("LIMIT " + Math.min(limit, 50)));

        List<Map<String, Object>> issues = new ArrayList<>();
        for (EvalResult eval : lowQuality) {
            ReportInfo report = reportInfoMapper.selectById(eval.getReportId());
            if (report == null) continue;
            CaseInfo caseInfo = caseInfoMapper.selectById(report.getCaseId());

            Map<String, Object> item = new HashMap<>();
            item.put("evalId", eval.getEvalId());
            item.put("reportId", eval.getReportId());
            item.put("caseId", report.getCaseId());
            item.put("examNo", caseInfo != null ? caseInfo.getExamNo() : "—");
            item.put("department", caseInfo != null ? caseInfo.getDepartment() : "—");
            item.put("qualityGrade", eval.getQualityGrade());
            item.put("f1Score", eval.getF1Score());
            item.put("reportStatus", report.getReportStatus());
            item.put("evalTime", eval.getEvalTime());

            List<String> problems = new ArrayList<>();
            if (eval.getMissingLabels() != null && !eval.getMissingLabels().isBlank()) {
                problems.add("漏诊: " + eval.getMissingLabels());
            }
            if (eval.getExtraLabels() != null && !eval.getExtraLabels().isBlank()) {
                problems.add("误诊: " + eval.getExtraLabels());
            }
            if (eval.getF1Score() != null && eval.getF1Score().doubleValue() < 0.5) {
                problems.add("F1低于50%");
            }
            item.put("problems", problems);
            issues.add(item);
        }
        return issues;
    }
}
