package com.hospital.xray.dto;

import lombok.Data;

@Data
public class ReportSaveDTO {

    private String finalFindings;
    private String finalImpression;
    private String editNote;
}
