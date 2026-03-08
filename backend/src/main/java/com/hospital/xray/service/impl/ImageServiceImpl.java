package com.hospital.xray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hospital.xray.dto.ImageMetadata;
import com.hospital.xray.dto.ImageUploadResult;
import com.hospital.xray.dto.ImageVO;
import com.hospital.xray.entity.ImageInfo;
import com.hospital.xray.entity.RetrievalLog;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.mapper.ImageInfoMapper;
import com.hospital.xray.mapper.RetrievalLogMapper;
import com.hospital.xray.service.ImageService;
import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 影像服务实现类
 */
@Slf4j
@Service
public class ImageServiceImpl implements ImageService {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private ImageInfoMapper imageInfoMapper;

    @Autowired
    private RetrievalLogMapper retrievalLogMapper;

    @Value("${minio.bucket-name}")
    private String bucketName;

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
        "image/jpeg", "image/jpg", "image/png", "application/dicom"
    );
    private static final int THUMBNAIL_SIZE = 200;
    private static final int URL_EXPIRY_HOURS = 24;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImageUploadResult uploadImage(MultipartFile file, Long caseId, String viewPosition, Long uploadUserId) {
        log.info("开始上传影像，病例ID: {}, 文件名: {}", caseId, file.getOriginalFilename());

        // 1. 校验文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件过大，请压缩或分批上传");
        }

        // 2. 校验文件格式
        String contentType = file.getContentType();
        if (contentType == null || !isValidImageType(contentType)) {
            throw new BusinessException("不支持的文件格式，仅支持 JPG、PNG、DICOM");
        }

        // 3. 生成对象路径
        String fileExtension = getFileExtension(file.getOriginalFilename());
        String objectName = generateObjectName(caseId, fileExtension);
        String thumbnailObjectName = generateThumbnailObjectName(objectName);

        try {
            // 4. 上传到 MinIO
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(contentType)
                    .build()
            );

            log.info("文件上传成功: {}", objectName);

            // 5. 提取图像元数据
            ImageMetadata metadata = extractImageMetadata(file);

            // 6. 生成缩略图（异步处理，不阻塞主流程）
            try {
                generateThumbnail(objectName, thumbnailObjectName);
            } catch (Exception e) {
                log.warn("缩略图生成失败，但不影响主流程: {}", e.getMessage());
            }

            // 7. 保存到数据库
            ImageInfo imageInfo = new ImageInfo();
            imageInfo.setCaseId(caseId);
            imageInfo.setFilePath(objectName);
            imageInfo.setFileName(file.getOriginalFilename());
            imageInfo.setFileType(fileExtension.toUpperCase());
            imageInfo.setFileSize(file.getSize());
            imageInfo.setViewPosition(viewPosition);
            imageInfo.setImgWidth(metadata.getWidth());
            imageInfo.setImgHeight(metadata.getHeight());
            imageInfo.setCreatedAt(LocalDateTime.now());

            imageInfoMapper.insert(imageInfo);

            log.info("影像记录保存成功，影像ID: {}", imageInfo.getImageId());

            // 8. 生成预签名URL
            String fullUrl = generatePresignedUrl(objectName);
            String thumbnailUrl = generatePresignedUrl(thumbnailObjectName);

            return ImageUploadResult.builder()
                .imageId(imageInfo.getImageId())
                .filePath(objectName)
                .fullUrl(fullUrl)
                .thumbnailUrl(thumbnailUrl)
                .build();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("影像上传失败", e);
            throw new BusinessException("影像上传失败: " + e.getMessage());
        }
    }

    @Override
    public List<ImageVO> listImagesByCaseId(Long caseId) {
        log.info("查询病例影像列表，病例ID: {}", caseId);

        LambdaQueryWrapper<ImageInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImageInfo::getCaseId, caseId)
               .orderByAsc(ImageInfo::getCreatedAt);

        List<ImageInfo> imageInfoList = imageInfoMapper.selectList(wrapper);

        return imageInfoList.stream()
            .map(this::convertToVO)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteImage(Long imageId) {
        log.info("删除影像，影像ID: {}", imageId);

        // 1. 查询影像记录
        ImageInfo imageInfo = imageInfoMapper.selectById(imageId);
        if (imageInfo == null) {
            throw new BusinessException("影像不存在");
        }

        // 0. 清理检索日志中对该影像的引用，否则会触发外键约束 fk_retrieval_image
        LambdaUpdateWrapper<RetrievalLog> clearRef = new LambdaUpdateWrapper<>();
        clearRef.eq(RetrievalLog::getQueryImageId, imageId)
                .set(RetrievalLog::getQueryImageId, null);
        retrievalLogMapper.update(null, clearRef);

        // 2. 删除 MinIO 中的文件（文件不存在时只记录警告，不阻塞 DB 删除）
        try {
            deleteMinioObject(imageInfo.getFilePath());
        } catch (Exception e) {
            log.warn("删除 MinIO 原图失败（可能文件不存在），继续删除 DB 记录: {}", e.getMessage());
        }
        try {
            String thumbnailPath = generateThumbnailObjectName(imageInfo.getFilePath());
            deleteMinioObject(thumbnailPath);
        } catch (Exception e) {
            log.warn("删除 MinIO 缩略图失败（可能文件不存在），继续删除 DB 记录: {}", e.getMessage());
        }

        // 3. 删除数据库记录
        imageInfoMapper.deleteById(imageId);

        log.info("影像删除成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteImagesByCaseId(Long caseId) {
        log.info("批量删除病例影像，病例ID: {}", caseId);

        // 查询所有影像
        LambdaQueryWrapper<ImageInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImageInfo::getCaseId, caseId);
        List<ImageInfo> imageInfoList = imageInfoMapper.selectList(wrapper);

        // 逐个删除
        for (ImageInfo imageInfo : imageInfoList) {
            try {
                // 清理 retrieval_log 引用
                LambdaUpdateWrapper<RetrievalLog> clearRef = new LambdaUpdateWrapper<>();
                clearRef.eq(RetrievalLog::getQueryImageId, imageInfo.getImageId())
                        .set(RetrievalLog::getQueryImageId, null);
                retrievalLogMapper.update(null, clearRef);

                deleteMinioObject(imageInfo.getFilePath());
                String thumbnailPath = generateThumbnailObjectName(imageInfo.getFilePath());
                deleteMinioObject(thumbnailPath);
            } catch (Exception e) {
                log.warn("删除影像文件失败: {}, 错误: {}", imageInfo.getFilePath(), e.getMessage());
            }
        }

        // 删除数据库记录
        imageInfoMapper.delete(wrapper);

        log.info("批量删除完成，共删除 {} 个影像", imageInfoList.size());
    }

    
    @Override
    public void generateThumbnail(String originalPath, String thumbnailPath) {
        log.info("生成缩略图: {} -> {}", originalPath, thumbnailPath);
        
        try {
            // 从 MinIO 下载原图
            InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(originalPath)
                    .build()
            );
            
            // 生成缩略图
            BufferedImage original = ImageIO.read(inputStream);
            if (original == null) {
                log.warn("无法读取图像，可能是 DICOM 格式: {}", originalPath);
                return;
            }
            
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            Thumbnails.of(original)
                .size(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
                .outputFormat("jpg")
                .toOutputStream(os);
            
            // 上传缩略图到 MinIO
            byte[] thumbnailBytes = os.toByteArray();
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(thumbnailPath)
                    .stream(new ByteArrayInputStream(thumbnailBytes), thumbnailBytes.length, -1)
                    .contentType("image/jpeg")
                    .build()
            );
            
            log.info("缩略图生成成功: {}", thumbnailPath);
            
        } catch (Exception e) {
            log.error("生成缩略图失败", e);
            throw new BusinessException("生成缩略图失败: " + e.getMessage());
        }
    }
    
    /**
     * 校验文件类型
     */
    private boolean isValidImageType(String contentType) {
        return ALLOWED_CONTENT_TYPES.stream()
            .anyMatch(type -> type.equalsIgnoreCase(contentType));
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
    
    /**
     * 生成对象存储路径
     * 格式: cases/YYYY/MM/DD/uuid.ext
     */
    private String generateObjectName(Long caseId, String extension) {
        LocalDate now = LocalDate.now();
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return String.format("cases/%d/%02d/%02d/%s.%s",
            now.getYear(), now.getMonthValue(), now.getDayOfMonth(), uuid, extension);
    }
    
    /**
     * 生成缩略图路径
     */
    private String generateThumbnailObjectName(String originalPath) {
        int lastDotIndex = originalPath.lastIndexOf(".");
        if (lastDotIndex > 0) {
            return originalPath.substring(0, lastDotIndex) + "_thumb.jpg";
        }
        return originalPath + "_thumb.jpg";
    }
    
    /**
     * 提取图像元数据
     */
    private ImageMetadata extractImageMetadata(MultipartFile file) {
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image != null) {
                return ImageMetadata.builder()
                    .width(image.getWidth())
                    .height(image.getHeight())
                    .format(getFileExtension(file.getOriginalFilename()))
                    .build();
            }
        } catch (Exception e) {
            log.warn("无法提取图像元数据，可能是 DICOM 格式: {}", e.getMessage());
        }
        
        // 如果无法提取，返回默认值
        return ImageMetadata.builder()
            .width(0)
            .height(0)
            .format(getFileExtension(file.getOriginalFilename()))
            .build();
    }
    
    /**
     * 生成预签名URL
     */
    private String generatePresignedUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(objectName)
                    .expiry(URL_EXPIRY_HOURS, TimeUnit.HOURS)
                    .build()
            );
        } catch (Exception e) {
            log.error("生成预签名URL失败: {}", objectName, e);
            return "";
        }
    }
    
    /**
     * 从 MinIO 下载影像并转为 Base64 Data URL，供外部 AI 服务（Qwen-VL 等）直接内联使用
     */
    public String getImageAsDataUrl(Long imageId) {
        ImageInfo imageInfo = imageInfoMapper.selectById(imageId);
        if (imageInfo == null) {
            throw new BusinessException("影像不存在: " + imageId);
        }
        try {
            InputStream is = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(imageInfo.getFilePath())
                    .build()
            );
            byte[] bytes = is.readAllBytes();
            String ext = imageInfo.getFileType() != null ? imageInfo.getFileType().toLowerCase() : "jpeg";
            String mime = ext.equals("png") ? "image/png" : "image/jpeg";
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.error("读取影像数据失败: {}", imageInfo.getFilePath(), e);
            throw new BusinessException("读取影像数据失败: " + e.getMessage());
        }
    }

    /**
     * 删除 MinIO 对象
     */
    private void deleteMinioObject(String objectName) throws Exception {
        minioClient.removeObject(
            RemoveObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build()
        );
        log.info("MinIO 对象删除成功: {}", objectName);
    }
    
    /**
     * 转换为 VO
     */
    private ImageVO convertToVO(ImageInfo imageInfo) {
        ImageVO vo = new ImageVO();
        BeanUtils.copyProperties(imageInfo, vo);
        
        // 生成预签名URL
        vo.setFullUrl(generatePresignedUrl(imageInfo.getFilePath()));
        String thumbnailPath = generateThumbnailObjectName(imageInfo.getFilePath());
        vo.setThumbnailUrl(generatePresignedUrl(thumbnailPath));
        
        return vo;
    }
}
