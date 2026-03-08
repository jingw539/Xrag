package com.hospital.xray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("report_info")
public class ReportInfo {

    @TableId(value = "report_id", type = IdType.ASSIGN_ID)
    private Long reportId;

    @TableField("case_id")
    private Long caseId;

    @TableField("report_status")
    private String reportStatus;

    @TableField("gen_model_id")
    private Long genModelId;

    @TableField("retrieval_log_id")
    private Long retrievalLogId;

    @TableField("ai_findings")
    private String aiFindings;

    @TableField("ai_impression")
    private String aiImpression;

    @TableField("ai_prompt")
    private String aiPrompt;

    @TableField("final_findings")
    private String finalFindings;

    @TableField("final_impression")
    private String finalImpression;

    @TableField("similar_case_ids")
    private String similarCaseIds;

    @TableField("quality_grade")
    private String qualityGrade;

    @TableField("model_confidence")
    private BigDecimal modelConfidence;

    @TableField("doctor_id")
    private Long doctorId;

    @TableField("sign_time")
    private LocalDateTime signTime;

    @TableField("ai_generate_time")
    private LocalDateTime aiGenerateTime;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
