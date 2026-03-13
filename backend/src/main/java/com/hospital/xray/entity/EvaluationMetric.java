package com.hospital.xray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("evaluation_metric")
public class EvaluationMetric {

    @TableId(value = "metric_id", type = IdType.ASSIGN_ID)
    private Long metricId;

    @TableField("run_id")
    private Long runId;

    @TableField("scope")
    private String scope;

    @TableField("tag_name")
    private String tagName;

    @TableField("metric_name")
    private String metricName;

    @TableField("metric_value")
    private Double metricValue;

    @TableField("support")
    private Integer support;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
