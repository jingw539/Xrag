package com.hospital.xray.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 病例视图对象 VO
 */
@Data
public class CaseVO {
    
    /**
     * 病例ID
     */
    private Long caseId;
    
    /**
     * 检查号
     */
    private String examNo;
    
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
     * 检查时间
     */
    private LocalDateTime examTime;
    
    /**
     * 检查部位
     */
    private String bodyPart;
    
    /**
     * 科室
     */
    private String department;

    /**
     * 责任医生ID
     */
    private Long responsibleDoctorId;

    /**
     * 责任医生姓名
     */
    private String responsibleDoctorName;
    
    /**
     * 报告状态
     */
    private String reportStatus;
    
    /**
     * 是否典型病例
     */
    private Integer isTypical;
    
    /**
     * 典型病例标签
     */
    private String typicalTags;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    // --- 来自最新报告的关联字段 ---
    private BigDecimal modelConfidence;
    private String qualityGrade;
    private LocalDateTime signTime;
    private LocalDateTime lastEditTime;
}
