package com.hospital.xray.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class EvalResultVO {

    private Long evalId;
    private Long reportId;
    private String evalType;
    private String aiLabels;
    private BigDecimal precisionScore;
    private BigDecimal recallScore;
    private BigDecimal f1Score;
    private BigDecimal bleu4Score;
    private BigDecimal rougeLScore;
    private String qualityGrade;
    private List<String> missingLabels;
    private List<String> extraLabels;
    private Integer elapsedMs;
    private LocalDateTime createdAt;
}
