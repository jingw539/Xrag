package com.hospital.xray.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AlertRespondDTO {

    @NotBlank(message = "处理动作不能为空")
    @Pattern(regexp = "^(ACKNOWLEDGED|ESCALATED|DISMISSED)$",
            message = "处理动作必须为 ACKNOWLEDGED/ESCALATED/DISMISSED")
    private String action;

    private String note;
}
