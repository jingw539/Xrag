package com.hospital.xray.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AlertVO {

    private Long alertId;
    private Long caseId;
    private Long reportId;
    private String labelType;
    private BigDecimal labelProb;
    private String alertStatus;
    private Long responderId;
    private String responderName;
    private String responseAction;
    private LocalDateTime responseTime;
    private String responseNote;
    private LocalDateTime alertTime;
}
