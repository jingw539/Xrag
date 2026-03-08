package com.hospital.xray.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportEditHistoryVO {

    private Long historyId;
    private Long reportId;
    private Long editorId;
    private String editorName;
    private String findingsBefore;
    private String findingsAfter;
    private String impressionBefore;
    private String impressionAfter;
    private String editNote;
    private LocalDateTime editTime;
}
