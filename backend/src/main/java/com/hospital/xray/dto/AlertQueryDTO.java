package com.hospital.xray.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AlertQueryDTO {

    private String alertStatus;
    private String labelType;
    private Long caseId;
    private String startDate;
    private String endDate;
    @Min(value = 1, message = "页码必须大于0")
    private Integer page = 1;
    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer pageSize = 20;
}
