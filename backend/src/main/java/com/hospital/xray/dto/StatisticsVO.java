package com.hospital.xray.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class StatisticsVO {

    private Long totalCases;
    private Long totalReports;
    private Long signedReports;
    private Map<String, Long> reportStatusStats;
}
