package com.hospital.xray.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ReportVO {

    private Long reportId;
    private Long caseId;
    private String reportStatus;
    private String aiFindings;
    private String aiImpression;
    private String finalFindings;
    private String finalImpression;
    private String qualityGrade;
    private BigDecimal modelConfidence;
    private String similarCaseIds;
    private Long doctorId;
    private String doctorName;
    private LocalDateTime signTime;
    private LocalDateTime aiGenerateTime;
    private LocalDateTime createdAt;
}
