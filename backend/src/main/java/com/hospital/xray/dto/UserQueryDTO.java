package com.hospital.xray.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UserQueryDTO {

    private String username;
    private String realName;
    private String roleCode;
    private String department;
    private Integer status;
    @Min(value = 1, message = "页码必须大于0")
    private Integer page = 1;
    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer pageSize = 20;
}
