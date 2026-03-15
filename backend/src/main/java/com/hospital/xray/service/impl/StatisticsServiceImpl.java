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
    public StatisticsVO getOverview(Long doctorId) {
        LambdaQueryWrapper<com.hospital.xray.entity.CaseInfo> caseWrapper = new LambdaQueryWrapper<>();
        if (doctorId != null) {
            caseWrapper.eq(com.hospital.xray.entity.CaseInfo::getResponsibleDoctorId, doctorId);
        }
        long totalCases = caseInfoMapper.selectCount(caseWrapper);

        LambdaQueryWrapper<ReportInfo> reportWrapper = new LambdaQueryWrapper<>();
        if (doctorId != null) {
            reportWrapper.eq(ReportInfo::getDoctorId, doctorId);
        }
        long totalReports = reportInfoMapper.selectCount(reportWrapper);

        LambdaQueryWrapper<ReportInfo> signedWrapper = new LambdaQueryWrapper<>();
        signedWrapper.eq(ReportInfo::getReportStatus, "SIGNED");
        if (doctorId != null) {
            signedWrapper.eq(ReportInfo::getDoctorId, doctorId);
        }
        long signedReports = reportInfoMapper.selectCount(signedWrapper);

        Map<String, Long> statusMap = new HashMap<>();
        for (String status : List.of("NONE", "AI_DRAFT", "EDITING", "SIGNED")) {
            LambdaQueryWrapper<ReportInfo> statusWrapper = new LambdaQueryWrapper<>();
            statusWrapper.eq(ReportInfo::getReportStatus, status);
            if (doctorId != null) {
                statusWrapper.eq(ReportInfo::getDoctorId, doctorId);
            }
            long cnt = reportInfoMapper.selectCount(statusWrapper);
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
    public List<Map<String, Object>> getReportTrend(String startDate, String endDate, String groupBy, Long doctorId) {
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();

        LambdaQueryWrapper<ReportInfo> wrapper = new LambdaQueryWrapper<ReportInfo>()
                .ge(ReportInfo::getCreatedAt, start.atStartOfDay())
                .le(ReportInfo::getCreatedAt, end.atTime(23, 59, 59))
                .isNotNull(ReportInfo::getCreatedAt)
                .orderByAsc(ReportInfo::getCreatedAt);
        if (doctorId != null) {
            wrapper.eq(ReportInfo::getDoctorId, doctorId);
        }

        List<ReportInfo> reports = reportInfoMapper.selectList(wrapper);

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
