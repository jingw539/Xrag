package com.hospital.xray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("image_info")
public class ImageInfo {

    @TableId(value = "image_id", type = IdType.ASSIGN_ID)
    private Long imageId;

    @TableField("case_id")
    private Long caseId;

    @TableField("file_path")
    private String filePath;

    @TableField("file_name")
    private String fileName;

    @TableField("file_type")
    private String fileType;

    @TableField("file_size")
    private Long fileSize;

    @TableField("view_position")
    private String viewPosition;

    @TableField("img_width")
    private Integer imgWidth;

    @TableField("img_height")
    private Integer imgHeight;

    @TableField("pixel_spacing_x_mm")
    private Double pixelSpacingXmm;

    @TableField("pixel_spacing_y_mm")
    private Double pixelSpacingYmm;

    @TableField("shoot_time")
    private LocalDateTime shootTime;

    @TableField("dicom_uid")
    private String dicomUid;

    @TableField("study_uid")
    private String studyUid;

    @TableField("series_uid")
    private String seriesUid;

    @TableField("instance_uid")
    private String instanceUid;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
