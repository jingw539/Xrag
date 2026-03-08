package com.hospital.xray.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RetrievalResultVO {

    private Long retrievalId;
    private Long caseId;
    private Integer topK;
    private Integer elapsedMs;
    private Boolean allAboveThreshold;
    private List<SimilarCaseVO> similarCases;
}
