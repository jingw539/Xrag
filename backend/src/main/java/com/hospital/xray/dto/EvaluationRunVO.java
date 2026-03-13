package com.hospital.xray.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvaluationRunVO {
    private Long runId;
    private String runName;
    private String modelName;
    private String datasetName;
    private String taskType;
    private String status;
    private String notes;
    private String paramsJson;
    private Long createdBy;
    private LocalDateTime createdAt;
}
