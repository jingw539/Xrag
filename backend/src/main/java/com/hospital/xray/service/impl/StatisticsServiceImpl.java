package com.hospital.xray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.xray.dto.StatisticsVO;
import com.hospital.xray.entity.ReportInfo;
import com.hospital.xray.mapper.CaseInfoMapper;
import com.hospital.xray.mapper.ReportInfoMapper;
import com.hospital.xray.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final CaseInfoMapper caseInfoMapper;
    private final ReportInfoMapper reportInfoMapper;

    @Override
    public StatisticsVO getOverview() {
        long totalCases = caseInfoMapper.selectCount(null);
        long totalReports = reportInfoMapper.selectCount(null);
        long signedReports = reportInfoMapper.selectCount(
                new LambdaQueryWrapper<ReportInfo>().eq(ReportInfo::getReportStatus, "SIGNED"));

        Map<String, Long> statusMap = new HashMap<>();
        for (String status : List.of("NONE", "AI_DRAFT", "EDITING", "SIGNED")) {
            long cnt = reportInfoMapper.selectCount(
                    new LambdaQueryWrapper<ReportInfo>().eq(ReportInfo::getReportStatus, status));
            statusMap.put(status, cnt);
        }

        return StatisticsVO.builder()
                .totalCases(totalCases)
                .totalReports(totalReports)
                .signedReports(signedReports)
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
                        .isNotNull(ReportInfo::getCreatedAt)
                        .orderByAsc(ReportInfo::getCreatedAt));

        Map<String, Long> grouped = reports.stream()
            .filter(r -> r.getCreatedAt() != null)
            .collect(Collectors.groupingBy(
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
}
