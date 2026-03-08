package com.hospital.xray.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 病例创建请求 DTO
 */
@Data
public class CaseCreateDTO {
    
    /**
     * 检查号（必填）
     */
    @NotBlank(message = "检查号不能为空")
    private String examNo;
    
    /**
     * 患者匿名ID（必填）
     */
    @NotBlank(message = "患者匿名ID不能为空")
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
     * 检查时间（必填）
     */
    @NotNull(message = "检查时间不能为空")
    private LocalDateTime examTime;
    
    /**
     * 检查部位（必填）
     */
    @NotBlank(message = "检查部位不能为空")
    private String bodyPart;
    
    /**
     * 科室
     */
    private String department;
}
