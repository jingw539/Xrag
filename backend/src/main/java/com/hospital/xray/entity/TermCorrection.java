package com.hospital.xray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("term_correction")
public class TermCorrection {

    @TableId(value = "correction_id", type = IdType.ASSIGN_ID)
    private Long correctionId;

    @TableField("report_id")
    private Long reportId;

    @TableField("original_term")
    private String originalTerm;

    @TableField("suggested_term")
    private String suggestedTerm;

    @TableField("context_sentence")
    private String contextSentence;

    @TableField("is_accepted")
    private Integer isAccepted;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
