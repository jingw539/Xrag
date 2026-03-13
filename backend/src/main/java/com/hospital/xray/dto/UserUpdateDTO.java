package com.hospital.xray.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UserUpdateDTO {

    private String realName;
    private String department;

    @Pattern(regexp = "^(DOCTOR|ADMIN)$", message = "角色编码必须为 DOCTOR/ADMIN")
    private String roleCode;
}
