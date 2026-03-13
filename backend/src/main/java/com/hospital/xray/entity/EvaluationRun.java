package com.hospital.xray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("evaluation_run")
public class EvaluationRun {

    @TableId(value = "run_id", type = IdType.ASSIGN_ID)
    private Long runId;

    @TableField("run_name")
    private String runName;

    @TableField("model_name")
    private String modelName;

    @TableField("dataset_name")
    private String datasetName;

    @TableField("task_type")
    private String taskType;

    @TableField("status")
    private String status;

    @TableField("notes")
    private String notes;

    @TableField("params_json")
    private String paramsJson;

    @TableField("created_by")
    private Long createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
