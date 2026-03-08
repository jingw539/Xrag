package com.hospital.xray.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReportGenerateDTO {

    @NotNull(message = "病例ID不能为空")
    private Long caseId;

    @NotNull(message = "影像ID不能为空")
    private Long imageId;

    private Integer topK = 3;
}
