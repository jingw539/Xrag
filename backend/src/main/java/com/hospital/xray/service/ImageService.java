package com.hospital.xray.service;

import com.hospital.xray.dto.ImageUploadResult;
import com.hospital.xray.dto.ImageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 影像服务接口
 * 负责影像文件的上传、存储、查询和删除
 */
public interface ImageService {
    
    /**
     * 上传影像
     * 
     * @param file 影像文件
     * @param caseId 所属病例ID
     * @param viewPosition 投照体位（可选）
     * @return 上传结果
     */
    ImageUploadResult uploadImage(MultipartFile file, Long caseId, String viewPosition, Long uploadUserId);
    
    /**
     * 查询病例的所有影像
     * 
     * @param caseId 病例ID
     * @return 影像列表
     */
    List<ImageVO> listImagesByCaseId(Long caseId);
    
    /**
     * 删除影像
     * 
     * @param imageId 影像ID
     */
    void deleteImage(Long imageId);
    
    /**
     * 批量删除病例的所有影像
     * 
     * @param caseId 病例ID
     */
    void deleteImagesByCaseId(Long caseId);
    
    /**
     * 生成缩略图
     * 
     * @param originalPath 原始图像路径
     * @param thumbnailPath 缩略图路径
     */
    void generateThumbnail(String originalPath, String thumbnailPath);

    /**
     * 从 MinIO 下载影像并返回 Base64 Data URL，供外部 AI 服务直接内联使用
     *
     * @param imageId 影像 ID
     * @return data:image/jpeg;base64,... 格式字符串
     */
    String getImageAsDataUrl(Long imageId);
}
