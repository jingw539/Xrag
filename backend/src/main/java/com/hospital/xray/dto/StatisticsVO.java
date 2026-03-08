package com.hospital.xray.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class StatisticsVO {

    private Long totalCases;
    private Long totalReports;
    private Long signedReports;
    private Long pendingAlerts;
    private BigDecimal avgF1Score;
    private BigDecimal avgBleu4Score;
    private BigDecimal avgRougeLScore;
    private BigDecimal adoptionRate;
    private Long avgGenTimeMs;
    private Map<String, Long> gradeDistribution;
    private Map<String, Long> alertTypeStats;
    private Map<String, Long> reportStatusStats;
}
