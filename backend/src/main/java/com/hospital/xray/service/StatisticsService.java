package com.hospital.xray.service;

import com.hospital.xray.dto.StatisticsVO;

import java.util.List;
import java.util.Map;

public interface StatisticsService {

    StatisticsVO getOverview();

    List<Map<String, Object>> getReportTrend(String startDate, String endDate, String groupBy);

    List<Map<String, Object>> getEvalTrend(String startDate, String endDate, String groupBy);

    List<Map<String, Object>> getModelVersionComparison();

    List<Map<String, Object>> getPerLabelStats();

    List<Map<String, Object>> getQualityIssues(int limit);
}
