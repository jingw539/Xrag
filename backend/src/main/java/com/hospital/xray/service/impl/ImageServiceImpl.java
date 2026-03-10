package com.hospital.xray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hospital.xray.dto.ImageMetadata;
import com.hospital.xray.dto.ImageMetadataUpdateDTO;
import com.hospital.xray.dto.ImageUploadResult;
import com.hospital.xray.dto.ImageVO;
import com.hospital.xray.entity.CaseInfo;
import com.hospital.xray.entity.ImageInfo;
import com.hospital.xray.entity.RetrievalLog;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.mapper.CaseInfoMapper;
import com.hospital.xray.mapper.ImageInfoMapper;
import com.hospital.xray.mapper.RetrievalLogMapper;
import com.hospital.xray.service.ImageService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
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
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ImageServiceImpl implements ImageService {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final int THUMBNAIL_SIZE = 200;
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/jpeg", "image/jpg", "image/png", "application/dicom", "application/octet-stream"
    );

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private ImageInfoMapper imageInfoMapper;

    @Autowired
    private RetrievalLogMapper retrievalLogMapper;

    @Autowired
    private CaseInfoMapper caseInfoMapper;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImageUploadResult uploadImage(MultipartFile file, Long caseId, String viewPosition, Long uploadUserId,
                                         Double pixelSpacingXmm, Double pixelSpacingYmm) {
        log.info("开始上传影像，病例ID: {}, 文件名: {}", caseId, file.getOriginalFilename());

        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择需要上传的影像文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件过大，请压缩后重新上传");
        }
        if (caseInfoMapper.selectById(caseId) == null) {
            throw new BusinessException(404, "病例不存在: " + caseId);
        }

        String contentType = file.getContentType();
        if (contentType == null || !isValidImageType(contentType, file.getOriginalFilename())) {
            throw new BusinessException("不支持的文件格式，仅支持 JPG、PNG、DICOM");
        }

        String fileExtension = getFileExtension(file.getOriginalFilename());
        String objectName = generateObjectName(caseId, fileExtension);
        String thumbnailObjectName = generateThumbnailObjectName(objectName);

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );

            ImageMetadata metadata = extractImageMetadata(file);

            try {
                generateThumbnail(objectName, thumbnailObjectName);
            } catch (Exception e) {
                log.warn("生成缩略图失败，继续保留原图: {}", e.getMessage());
            }

            ImageInfo imageInfo = new ImageInfo();
            imageInfo.setCaseId(caseId);
            imageInfo.setFilePath(objectName);
            imageInfo.setFileName(file.getOriginalFilename());
            imageInfo.setFileType(fileExtension.toUpperCase());
            imageInfo.setFileSize(file.getSize());
            imageInfo.setViewPosition(viewPosition);
            imageInfo.setImgWidth(metadata.getWidth());
            imageInfo.setImgHeight(metadata.getHeight());
            imageInfo.setPixelSpacingXmm(pixelSpacingXmm != null ? pixelSpacingXmm : metadata.getPixelSpacingXmm());
            imageInfo.setPixelSpacingYmm(pixelSpacingYmm != null ? pixelSpacingYmm : metadata.getPixelSpacingYmm());
            imageInfo.setCreatedAt(LocalDateTime.now());
            imageInfoMapper.insert(imageInfo);

            return ImageUploadResult.builder()
                    .imageId(imageInfo.getImageId())
                    .filePath(objectName)
                    .fullUrl("/api/images/" + imageInfo.getImageId() + "/content")
                    .thumbnailUrl("/api/images/" + imageInfo.getImageId() + "/thumbnail")
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("上传影像失败", e);
            throw new BusinessException("上传影像失败: " + e.getMessage());
        }
    }

    @Override
    public List<ImageVO> listImagesByCaseId(Long caseId) {
        return imageInfoMapper.selectList(new LambdaQueryWrapper<ImageInfo>()
                        .eq(ImageInfo::getCaseId, caseId)
                        .orderByAsc(ImageInfo::getCreatedAt))
                .stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ImageVO> listPriorImages(Long caseId, Long currentImageId) {
        CaseInfo currentCase = caseInfoMapper.selectById(caseId);
        if (currentCase == null || currentCase.getPatientAnonId() == null) {
            return List.of();
        }
        List<CaseInfo> priorCases = caseInfoMapper.selectList(new LambdaQueryWrapper<CaseInfo>()
                .eq(CaseInfo::getPatientAnonId, currentCase.getPatientAnonId())
                .ne(CaseInfo::getCaseId, caseId)
                .lt(currentCase.getExamTime() != null, CaseInfo::getExamTime, currentCase.getExamTime())
                .orderByDesc(CaseInfo::getExamTime)
                .last("LIMIT 5"));
        if (priorCases.isEmpty()) {
            return List.of();
        }
        List<Long> caseIds = priorCases.stream().map(CaseInfo::getCaseId).collect(Collectors.toList());
        return imageInfoMapper.selectList(new LambdaQueryWrapper<ImageInfo>()
                        .in(ImageInfo::getCaseId, caseIds)
                        .ne(currentImageId != null, ImageInfo::getImageId, currentImageId)
                        .orderByDesc(ImageInfo::getCreatedAt))
                .stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public ImageVO updateImageMetadata(Long imageId, ImageMetadataUpdateDTO dto) {
        ImageInfo imageInfo = imageInfoMapper.selectById(imageId);
        if (imageInfo == null) {
            throw new BusinessException("影像不存在");
        }
        imageInfo.setPixelSpacingXmm(dto.getPixelSpacingXmm());
        imageInfo.setPixelSpacingYmm(dto.getPixelSpacingYmm());
        imageInfoMapper.updateById(imageInfo);
        return convertToVO(imageInfoMapper.selectById(imageId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteImage(Long imageId) {
        ImageInfo imageInfo = imageInfoMapper.selectById(imageId);
        if (imageInfo == null) {
            throw new BusinessException("影像不存在");
        }

        LambdaUpdateWrapper<RetrievalLog> clearRef = new LambdaUpdateWrapper<>();
        clearRef.eq(RetrievalLog::getQueryImageId, imageId)
                .set(RetrievalLog::getQueryImageId, null);
        retrievalLogMapper.update(null, clearRef);

        try {
            deleteMinioObject(imageInfo.getFilePath());
        } catch (Exception e) {
            log.warn("删除 MinIO 原图失败，继续删除数据库记录: {}", e.getMessage());
        }
        try {
            deleteMinioObject(generateThumbnailObjectName(imageInfo.getFilePath()));
        } catch (Exception e) {
            log.warn("删除 MinIO 缩略图失败，继续删除数据库记录: {}", e.getMessage());
        }

        imageInfoMapper.deleteById(imageId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteImagesByCaseId(Long caseId) {
        List<ImageInfo> images = imageInfoMapper.selectList(new LambdaQueryWrapper<ImageInfo>()
                .eq(ImageInfo::getCaseId, caseId));
        for (ImageInfo image : images) {
            try {
                LambdaUpdateWrapper<RetrievalLog> clearRef = new LambdaUpdateWrapper<>();
                clearRef.eq(RetrievalLog::getQueryImageId, image.getImageId())
                        .set(RetrievalLog::getQueryImageId, null);
                retrievalLogMapper.update(null, clearRef);
                deleteMinioObject(image.getFilePath());
            } catch (Exception e) {
                log.warn("删除原图失败: {}", e.getMessage());
            }
            try {
                deleteMinioObject(generateThumbnailObjectName(image.getFilePath()));
            } catch (Exception e) {
                log.warn("删除缩略图失败: {}", e.getMessage());
            }
        }
        imageInfoMapper.delete(new LambdaQueryWrapper<ImageInfo>().eq(ImageInfo::getCaseId, caseId));
    }

    @Override
    public void generateThumbnail(String originalPath, String thumbnailPath) {
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(originalPath).build())) {
            BufferedImage original = ImageIO.read(inputStream);
            if (original == null) {
                log.warn("无法读取图像，可能是 DICOM 格式: {}", originalPath);
                return;
            }
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            Thumbnails.of(original).size(THUMBNAIL_SIZE, THUMBNAIL_SIZE).outputFormat("jpg").toOutputStream(os);
            byte[] bytes = os.toByteArray();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(thumbnailPath)
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .contentType("image/jpeg")
                            .build()
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("生成缩略图失败: " + e.getMessage());
        }
    }

    @Override
    public String getImageAsDataUrl(Long imageId) {
        ImageInfo imageInfo = imageInfoMapper.selectById(imageId);
        if (imageInfo == null) {
            throw new BusinessException("影像不存在: " + imageId);
        }
        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(imageInfo.getFilePath()).build())) {
            byte[] bytes = is.readAllBytes();
            String ext = imageInfo.getFileType() != null ? imageInfo.getFileType().toLowerCase() : "jpeg";
            String mime = ext.equals("png") ? "image/png" : "image/jpeg";
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new BusinessException("读取影像数据 URL 失败: " + e.getMessage());
        }
    }

    @Override
    public byte[] getImageContent(Long imageId, boolean thumbnail) {
        ImageInfo imageInfo = imageInfoMapper.selectById(imageId);
        if (imageInfo == null) {
            throw new BusinessException("影像不存在: " + imageId);
        }
        String objectName = thumbnail ? generateThumbnailObjectName(imageInfo.getFilePath()) : imageInfo.getFilePath();
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(objectName).build())) {
            return inputStream.readAllBytes();
        } catch (Exception e) {
            throw new BusinessException("读取影像失败: " + e.getMessage());
        }
    }

    @Override
    public String getImageContentType(Long imageId, boolean thumbnail) {
        ImageInfo imageInfo = imageInfoMapper.selectById(imageId);
        if (imageInfo == null) {
            throw new BusinessException("影像不存在: " + imageId);
        }
        if (thumbnail) {
            return "image/jpeg";
        }
        String fileType = imageInfo.getFileType() == null ? "jpg" : imageInfo.getFileType().toLowerCase();
        return switch (fileType) {
            case "png" -> "image/png";
            case "dcm", "dicom" -> "application/dicom";
            default -> "image/jpeg";
        };
    }

    private boolean isValidImageType(String contentType, String filename) {
        if (ALLOWED_CONTENT_TYPES.stream().anyMatch(type -> type.equalsIgnoreCase(contentType))) {
            return true;
        }
        String ext = getFileExtension(filename);
        return List.of("jpg", "jpeg", "png", "dcm", "dicom").contains(ext.toLowerCase());
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "jpg";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private String generateObjectName(Long caseId, String extension) {
        LocalDate now = LocalDate.now();
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return String.format("cases/%s/%d/%02d/%02d/%s.%s",
                caseId, now.getYear(), now.getMonthValue(), now.getDayOfMonth(), uuid, extension);
    }

    private String generateThumbnailObjectName(String originalPath) {
        int dot = originalPath.lastIndexOf('.');
        return dot > 0 ? originalPath.substring(0, dot) + "_thumb.jpg" : originalPath + "_thumb.jpg";
    }

    private ImageMetadata extractImageMetadata(MultipartFile file) {
        String format = getFileExtension(file.getOriginalFilename());
        try {
            if (isDicomFile(file, format)) {
                return extractDicomMetadata(file, format);
            }
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image != null) {
                return ImageMetadata.builder()
                        .width(image.getWidth())
                        .height(image.getHeight())
                        .format(format)
                        .build();
            }
        } catch (Exception e) {
            log.warn("提取影像元数据失败: {}", e.getMessage());
        }
        return ImageMetadata.builder().width(0).height(0).format(format).build();
    }

    private boolean isDicomFile(MultipartFile file, String format) {
        String contentType = file.getContentType();
        return (contentType != null && contentType.toLowerCase().contains("dicom"))
                || "dcm".equalsIgnoreCase(format)
                || "dicom".equalsIgnoreCase(format);
    }

    private ImageMetadata extractDicomMetadata(MultipartFile file, String format) throws IOException {
        try (DicomInputStream dicomInputStream = new DicomInputStream(file.getInputStream())) {
            Attributes attributes = dicomInputStream.readDataset(-1, -1);
            int width = attributes.getInt(Tag.Columns, 0);
            int height = attributes.getInt(Tag.Rows, 0);
            double[] spacing = attributes.getDoubles(Tag.PixelSpacing);
            if (spacing == null || spacing.length == 0) {
                spacing = attributes.getDoubles(Tag.ImagerPixelSpacing);
            }
            Double pixelSpacingYmm = null;
            Double pixelSpacingXmm = null;
            if (spacing != null && spacing.length > 0) {
                pixelSpacingYmm = spacing[0];
                pixelSpacingXmm = spacing.length > 1 ? spacing[1] : spacing[0];
            }
            return ImageMetadata.builder()
                    .width(width)
                    .height(height)
                    .format(format)
                    .pixelSpacingXmm(pixelSpacingXmm)
                    .pixelSpacingYmm(pixelSpacingYmm)
                    .build();
        }
    }

    private void deleteMinioObject(String objectName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
    }

    private ImageVO convertToVO(ImageInfo imageInfo) {
        ImageVO vo = new ImageVO();
        BeanUtils.copyProperties(imageInfo, vo);
        vo.setFullUrl("/api/images/" + imageInfo.getImageId() + "/content");
        vo.setThumbnailUrl("/api/images/" + imageInfo.getImageId() + "/thumbnail");
        return vo;
    }
}
