package com.hospital.xray.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SimilarCaseVO {

    private Long caseId;
    private String examNo;
    private BigDecimal similarityScore;
    private String thumbnailUrl;
    private String findings;
    private String impression;
    private String cheXpertLabels;
}
