package com.hospital.xray.dto;

import lombok.Data;

/**
 * 病例更新请求 DTO
 */
@Data
public class CaseUpdateDTO {
    
    /**
     * 患者匿名ID
     */
    private String patientAnonId;
    
    /**
     * 性别
     */
    private String gender;
    
    /**
     * 年龄
     */
    private Integer age;
    
    /**
     * 科室
     */
    private String department;
}
