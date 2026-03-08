package com.hospital.xray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("eval_result")
public class EvalResult {

    @TableId(value = "eval_id", type = IdType.ASSIGN_ID)
    private Long evalId;

    @TableField("report_id")
    private Long reportId;

    @TableField("model_id")
    private Long modelId;

    @TableField("eval_type")
    private String evalType;

    @TableField("ai_labels")
    private String aiLabels;

    @TableField("ref_labels")
    private String refLabels;

    @TableField("precision_score")
    private BigDecimal precisionScore;

    @TableField("recall_score")
    private BigDecimal recallScore;

    @TableField("f1_score")
    private BigDecimal f1Score;

    @TableField("bleu4_score")
    private BigDecimal bleu4Score;

    @TableField("rouge_l_score")
    private BigDecimal rougeLScore;

    @TableField("quality_grade")
    private String qualityGrade;

    @TableField("missing_labels")
    private String missingLabels;

    @TableField("extra_labels")
    private String extraLabels;

    @TableField(exist = false)
    private Integer elapsedMs;

    @TableField("eval_time")
    private LocalDateTime evalTime;
}
