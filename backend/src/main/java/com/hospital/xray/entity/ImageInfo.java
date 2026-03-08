package com.hospital.xray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 影像信息实体类
 * 对应数据库表 image_info
 */
@Data
@TableName("image_info")
public class ImageInfo {

    /**
     * 影像唯一标识
     */
    @TableId(value = "image_id", type = IdType.ASSIGN_ID)
    private Long imageId;

    /**
     * 所属病例ID
     */
    @TableField("case_id")
    private Long caseId;

    /**
     * MinIO中的对象路径
     */
    @TableField("file_path")
    private String filePath;

    /**
     * 原始文件名
     */
    @TableField("file_name")
    private String fileName;

    /**
     * 文件类型：JPG/PNG/DICOM
     */
    @TableField("file_type")
    private String fileType;

    /**
     * 文件大小（字节）
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 投照体位
     */
    @TableField("view_position")
    private String viewPosition;

    /**
     * 图像宽度（像素）
     */
    @TableField("img_width")
    private Integer imgWidth;

    /**
     * 图像高度（像素）
     */
    @TableField("img_height")
    private Integer imgHeight;

    /**
     * 拍摄时间
     */
    @TableField("shoot_time")
    private LocalDateTime shootTime;

    /**
     * DICOM UID，预留PACS对接
     */
    @TableField("dicom_uid")
    private String dicomUid;

    /**
     * Study UID，预留PACS对接
     */
    @TableField("study_uid")
    private String studyUid;

    /**
     * Series UID，预留PACS对接
     */
    @TableField("series_uid")
    private String seriesUid;

    /**
     * Instance UID，预留PACS对接
     */
    @TableField("instance_uid")
    private String instanceUid;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
