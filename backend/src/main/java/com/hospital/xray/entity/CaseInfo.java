package com.hospital.xray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 病例信息实体类
 * 对应数据库表 case_info
 */
@Data
@TableName("case_info")
public class CaseInfo {

    /**
     * 病例唯一标识，使用雪花算法生成
     */
    @TableId(value = "case_id", type = IdType.ASSIGN_ID)
    private Long caseId;

    /**
     * 检查号，与HIS/RIS对接的关键字段
     */
    @TableField("exam_no")
    private String examNo;

    /**
     * 患者匿名ID，保护隐私
     */
    @TableField("patient_anon_id")
    private String patientAnonId;

    /**
     * 性别：M=男，F=女
     */
    @TableField("gender")
    private String gender;

    /**
     * 年龄
     */
    @TableField("age")
    private Integer age;

    /**
     * 检查时间
     */
    @TableField("exam_time")
    private LocalDateTime examTime;

    /**
     * 检查部位
     */
    @TableField("body_part")
    private String bodyPart;

    /**
     * 科室
     */
    @TableField("department")
    private String department;

    /**
     * 责任医生ID
     */
    @TableField("responsible_doctor_id")
    private Long responsibleDoctorId;

    /**
     * 报告状态：NONE=未生成，AI_DRAFT=AI草稿，EDITING=编辑中，SIGNED=已签发
     */
    @TableField("report_status")
    private String reportStatus;

    /**
     * 是否典型病例：0=否，1=是
     */
    @TableField("is_typical")
    private Integer isTypical;

    /**
     * 典型病例标签，支持多标签（逗号分隔）
     */
    @TableField("typical_tags")
    private String typicalTags;

    /**
     * 典型病例备注说明
     */
    @TableField("typical_remark")
    private String typicalRemark;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
