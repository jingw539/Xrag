package com.hospital.xray.dto;

import lombok.Data;

@Data
public class EvaluationMetricVO {
    private String scope;
    private String tagName;
    private String metricName;
    private Double metricValue;
    private Integer support;
}
