package com.hospital.xray.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TermCorrectionVO {

    private Long correctionId;
    private Long reportId;
    private String originalTerm;
    private String suggestedTerm;
    private String contextSentence;
    private Integer isAccepted;
    private LocalDateTime createdAt;
}
