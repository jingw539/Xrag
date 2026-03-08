package com.hospital.xray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("retrieval_log")
public class RetrievalLog {

    @TableId(value = "retrieval_id", type = IdType.ASSIGN_ID)
    private Long retrievalId;

    @TableField("case_id")
    private Long caseId;

    @TableField("query_image_id")
    private Long queryImageId;

    @TableField("retriever_model_id")
    private Long retrieverModelId;

    @TableField("top_k")
    private Integer topK;

    @TableField("similar_case_ids")
    private String similarCaseIds;

    @TableField("similarity_scores")
    private String similarityScores;

    @TableField("all_above_threshold")
    private Integer allAboveThreshold;

    @TableField("elapsed_ms")
    private Integer elapsedMs;

    @TableField("retrieval_time")
    private LocalDateTime retrievalTime;
}
