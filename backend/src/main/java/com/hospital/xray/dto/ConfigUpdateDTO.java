package com.hospital.xray.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfigUpdateDTO {

    @NotBlank(message = "配置值不能为空")
    private String configValue;
}
