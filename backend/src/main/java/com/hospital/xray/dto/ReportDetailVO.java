package com.hospital.xray.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReportDetailVO {

    private Long reportId;
    private Long caseId;
    private String reportStatus;
    private String aiFindings;
    private String aiImpression;
    private String aiPrompt;
    private String finalFindings;
    private String finalImpression;
    private String qualityGrade;
    private BigDecimal modelConfidence;
    private List<Long> similarCaseIds;
    private Long doctorId;
    private String doctorName;
    private LocalDateTime signTime;
    private LocalDateTime aiGenerateTime;
    private LocalDateTime createdAt;
    private List<ReportEditHistoryVO> editHistory;
    private List<TermCorrectionVO> termCorrections;
}
