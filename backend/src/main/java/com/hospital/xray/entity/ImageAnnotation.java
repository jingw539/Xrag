package com.hospital.xray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("image_annotation")
public class ImageAnnotation {

    @TableId(value = "annotation_id", type = IdType.ASSIGN_ID)
    private Long annotationId;

    @TableField("image_id")
    private Long imageId;

    @TableField("report_id")
    private Long reportId;

    @TableField("source")
    private String source;

    @TableField("anno_type")
    private String annoType;

    @TableField("label")
    private String label;

    @TableField("remark")
    private String remark;

    @TableField("x")
    private Double x;

    @TableField("y")
    private Double y;

    @TableField("width")
    private Double width;

    @TableField("height")
    private Double height;

    @TableField("measured_width_mm")
    private Double measuredWidthMm;

    @TableField("measured_height_mm")
    private Double measuredHeightMm;

    @TableField("compare_status")
    private String compareStatus;

    @TableField("compare_note")
    private String compareNote;

    @TableField("color")
    private String color;

    @TableField("confidence")
    private Double confidence;

    @TableField("created_by")
    private Long createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
