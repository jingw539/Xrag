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

    /** AI 或 DOCTOR */
    @TableField("source")
    private String source;

    /** RECTANGLE / CIRCLE */
    @TableField("anno_type")
    private String annoType;

    @TableField("label")
    private String label;

    @TableField("remark")
    private String remark;

    /** 标注框左上角 X，归一化 0-1 */
    @TableField("x")
    private Double x;

    /** 标注框左上角 Y，归一化 0-1 */
    @TableField("y")
    private Double y;

    @TableField("width")
    private Double width;

    @TableField("height")
    private Double height;

    /** 显示颜色，如 #ff7875 */
    @TableField("color")
    private String color;

    /** AI 置信度 0-1，医生标注为 null */
    @TableField("confidence")
    private Double confidence;

    @TableField("created_by")
    private Long createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
