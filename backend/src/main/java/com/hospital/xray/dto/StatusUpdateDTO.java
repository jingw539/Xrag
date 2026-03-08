package com.hospital.xray.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StatusUpdateDTO {

    @NotNull(message = "状态值不能为空")
    @Min(value = 0, message = "状态值只能为 0（禁用）或 1（启用）")
    @Max(value = 1, message = "状态值只能为 0（禁用）或 1（启用）")
    private Integer status;
}
