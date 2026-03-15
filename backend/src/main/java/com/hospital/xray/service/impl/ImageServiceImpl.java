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
import com.hospital.xray.util.SecurityUtils;
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
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
    private static final String LOCAL_PREFIX = "local:";

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

    @Value("${storage.type:minio}")
    private String storageType;

    @Value("${storage.local.root:}")
    private String localRoot;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImageUploadResult uploadImage(MultipartFile file, Long caseId, String viewPosition, Long uploadUserId,
                                         Double pixelSpacingXmm, Double pixelSpacingYmm) {
        log.info("Start upload image, caseId={}, filename={}", caseId, file.getOriginalFilename());

        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的影像文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件过大，请压缩后重试");
        }
        if (caseInfoMapper.selectById(caseId) == null) {
            throw new BusinessException(404, "Case not found: " + caseId);
        }
        assertCaseWritable(caseId);

        String contentType = file.getContentType();
        if (contentType == null || !isValidImageType(contentType, file.getOriginalFilename())) {
            throw new BusinessException("不支持的文件格式");
        }

        String fileExtension = getFileExtension(file.getOriginalFilename());
        String objectName = generateObjectName(caseId, fileExtension);
        String thumbnailObjectName = generateThumbnailObjectName(objectName);

        try {
            ImageMetadata metadata = extractImageMetadata(file);
            String storedPath;
            if (isLocalStorageEnabled()) {
                storedPath = LOCAL_PREFIX + objectName;
                Path target = resolveLocalPath(storedPath);
                Files.createDirectories(target.getParent());
                try (InputStream in = file.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                try {
                    generateThumbnail(objectName, thumbnailObjectName);
                } catch (Exception e) {
                    log.warn("Thumbnail generation failed, keep original: {}", e.getMessage());
                }
            } else {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .stream(file.getInputStream(), file.getSize(), -1)
                                .contentType(contentType)
                                .build()
                );
                storedPath = objectName;
                try {
                    generateThumbnail(objectName, thumbnailObjectName);
                } catch (Exception e) {
                    log.warn("Thumbnail generation failed, keep original: {}", e.getMessage());
                }
            }

            ImageInfo imageInfo = new ImageInfo();
            imageInfo.setCaseId(caseId);
            imageInfo.setFilePath(storedPath);
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
                    .filePath(storedPath)
                    .fullUrl("/api/images/" + imageInfo.getImageId() + "/content")
                    .thumbnailUrl("/api/images/" + imageInfo.getImageId() + "/thumbnail")
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Upload image failed", e);
            throw new BusinessException("Upload image failed: " + e.getMessage());
        }
    }
    @Override
    public List<ImageVO> listImagesByCaseId(Long caseId) {
        assertCaseReadable(caseId);
        return imageInfoMapper.selectList(new LambdaQueryWrapper<ImageInfo>()
                        .eq(ImageInfo::getCaseId, caseId)
                        .orderByAsc(ImageInfo::getCreatedAt))
                .stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ImageVO> listPriorImages(Long caseId, Long currentImageId) {
        assertCaseReadable(caseId);
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
        assertImageWritable(imageInfo);
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
        assertImageWritable(imageInfo);

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
        List<Long> imageIds = images.stream()
                .map(ImageInfo::getImageId)
                .collect(Collectors.toList());
        if (!imageIds.isEmpty()) {
            LambdaUpdateWrapper<RetrievalLog> clearRef = new LambdaUpdateWrapper<>();
            clearRef.in(RetrievalLog::getQueryImageId, imageIds)
                    .set(RetrievalLog::getQueryImageId, null);
            retrievalLogMapper.update(null, clearRef);
        }
        for (ImageInfo image : images) {
            try {
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
        if (isLocalStorageEnabled() || isLocalPath(originalPath) || isLocalPath(thumbnailPath)) {
            Path original = resolveLocalPath(prefixLocalIfNeeded(originalPath));
            Path thumb = resolveLocalPath(prefixLocalIfNeeded(thumbnailPath));
            generateLocalThumbnail(original, thumb);
            return;
        }
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(originalPath).build())) {
            BufferedImage original = ImageIO.read(inputStream);
            if (original == null) {
                log.warn("Unable to read image, maybe DICOM: {}", originalPath);
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
            throw new BusinessException("Generate thumbnail failed: " + e.getMessage());
        }
    }
    @Override
    public String getImageAsDataUrl(Long imageId) {
        ImageInfo imageInfo = imageInfoMapper.selectById(imageId);
        if (imageInfo == null) {
            throw new BusinessException("Image not found: " + imageId);
        }
        try {
            byte[] bytes;
            if (isLocalPath(imageInfo.getFilePath())) {
                bytes = readLocalBytes(imageInfo.getFilePath(), false);
            } else {
                try (InputStream is = minioClient.getObject(
                        GetObjectArgs.builder().bucket(bucketName).object(imageInfo.getFilePath()).build())) {
                    bytes = is.readAllBytes();
                }
            }
            String ext = imageInfo.getFileType() != null ? imageInfo.getFileType().toLowerCase() : guessExtension(imageInfo.getFilePath());
            String mime = ext.equals("png") ? "image/png" : "image/jpeg";
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new BusinessException("Read image data URL failed: " + e.getMessage());
        }
    }
    @Override
    public byte[] getImageContent(Long imageId, boolean thumbnail) {
        ImageInfo imageInfo = imageInfoMapper.selectById(imageId);
        if (imageInfo == null) {
            throw new BusinessException("Image not found: " + imageId);
        }
        assertImageReadable(imageInfo);
        if (isLocalPath(imageInfo.getFilePath())) {
            return readLocalBytes(imageInfo.getFilePath(), thumbnail);
        }
        String objectName = thumbnail ? generateThumbnailObjectName(imageInfo.getFilePath()) : imageInfo.getFilePath();
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(objectName).build())) {
            return inputStream.readAllBytes();
        } catch (Exception e) {
            throw new BusinessException("Read image failed: " + e.getMessage());
        }
    }
    @Override
    public String getImageContentType(Long imageId, boolean thumbnail) {
        ImageInfo imageInfo = imageInfoMapper.selectById(imageId);
        if (imageInfo == null) {
            throw new BusinessException("Image not found: " + imageId);
        }
        assertImageReadable(imageInfo);
        if (thumbnail) {
            return "image/jpeg";
        }
        String fileType = imageInfo.getFileType() == null ? guessExtension(imageInfo.getFilePath()) : imageInfo.getFileType().toLowerCase();
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
    private boolean isLocalStorageEnabled() {
        return "local".equalsIgnoreCase(storageType);
    }

    private boolean isLocalPath(String filePath) {
        if (filePath == null) return false;
        if (filePath.startsWith(LOCAL_PREFIX)) return true;
        try {
            if ("local".equalsIgnoreCase(storageType) && !Paths.get(filePath).isAbsolute()) {
                return true;
            }
            return Paths.get(filePath).isAbsolute();
        } catch (Exception e) {
            return false;
        }
    }

    private String prefixLocalIfNeeded(String path) {
        if (path == null) return null;
        if (path.startsWith(LOCAL_PREFIX)) return path;
        try {
            if (Paths.get(path).isAbsolute()) return path;
        } catch (Exception e) {
            return path;
        }
        return LOCAL_PREFIX + path;
    }

    private Path resolveLocalPath(String filePath) {
        String path = filePath;
        if (path.startsWith(LOCAL_PREFIX)) {
            path = path.substring(LOCAL_PREFIX.length());
        }
        Path p = Paths.get(path);
        if (p.isAbsolute()) return p;
        if (!StringUtils.hasText(localRoot)) {
            throw new BusinessException("Local storage root not configured: storage.local.root");
        }
        return Paths.get(localRoot).resolve(path).normalize();
    }

    private String buildThumbnailPath(String filePath) {
        boolean prefixed = filePath != null && filePath.startsWith(LOCAL_PREFIX);
        String raw = prefixed ? filePath.substring(LOCAL_PREFIX.length()) : filePath;
        String thumb = generateThumbnailObjectName(raw);
        return prefixed ? LOCAL_PREFIX + thumb : thumb;
    }

    private byte[] readLocalBytes(String filePath, boolean thumbnail) {
        try {
            Path original = resolveLocalPath(filePath);
            if (!thumbnail) {
                return Files.readAllBytes(original);
            }
            String thumbPath = buildThumbnailPath(filePath);
            Path thumb = resolveLocalPath(thumbPath);
            if (Files.exists(thumb)) {
                return Files.readAllBytes(thumb);
            }
            byte[] bytes = generateThumbnailBytes(original);
            try {
                Files.createDirectories(thumb.getParent());
                Files.write(thumb, bytes);
            } catch (Exception e) {
                log.warn("Local thumbnail cache failed: {}", e.getMessage());
            }
            return bytes;
        } catch (Exception e) {
            throw new BusinessException("Read local image failed: " + e.getMessage());
        }
    }

    private void generateLocalThumbnail(Path original, Path thumbnail) {
        try {
            Files.createDirectories(thumbnail.getParent());
            BufferedImage originalImage = ImageIO.read(original.toFile());
            if (originalImage == null) {
                log.warn("Unable to read image, maybe DICOM: {}", original);
                return;
            }
            Thumbnails.of(originalImage).size(THUMBNAIL_SIZE, THUMBNAIL_SIZE).outputFormat("jpg").toFile(thumbnail.toFile());
        } catch (Exception e) {
            throw new BusinessException("Generate local thumbnail failed: " + e.getMessage());
        }
    }

    private byte[] generateThumbnailBytes(Path original) throws IOException {
        BufferedImage originalImage = ImageIO.read(original.toFile());
        if (originalImage == null) {
            return Files.readAllBytes(original);
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Thumbnails.of(originalImage).size(THUMBNAIL_SIZE, THUMBNAIL_SIZE).outputFormat("jpg").toOutputStream(os);
        return os.toByteArray();
    }

    private String guessExtension(String filePath) {
        if (filePath == null) return "jpg";
        String path = filePath.startsWith(LOCAL_PREFIX) ? filePath.substring(LOCAL_PREFIX.length()) : filePath;
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return "jpg";
        return path.substring(dot + 1).toLowerCase();
    }


    private void assertCaseReadable(Long caseId) {
        if (!SecurityUtils.hasRole("DOCTOR")) {
            return;
        }
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "Not logged in");
        }
        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        if (caseInfo == null) {
            throw new BusinessException(404, "Case not found: " + caseId);
        }
        // Read-only access is allowed for other doctors' cases.
    }

    private void assertCaseWritable(Long caseId) {
        if (!SecurityUtils.hasRole("DOCTOR")) {
            return;
        }
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "Not logged in");
        }
        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        if (caseInfo == null) {
            throw new BusinessException(404, "Case not found: " + caseId);
        }
        if (caseInfo.getResponsibleDoctorId() == null) {
            throw new BusinessException(403, "Case not assigned");
        }
        if (!currentUserId.equals(caseInfo.getResponsibleDoctorId())) {
            throw new BusinessException(403, "Cannot operate on other doctor case");
        }
    }

    private void assertImageReadable(ImageInfo imageInfo) {
        if (imageInfo == null) return;
        assertCaseReadable(imageInfo.getCaseId());
    }

    private void assertImageWritable(ImageInfo imageInfo) {
        if (imageInfo == null) return;
        assertCaseWritable(imageInfo.getCaseId());
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
        if (isLocalPath(objectName)) {
            Path path = resolveLocalPath(objectName);
            Files.deleteIfExists(path);
            return;
        }
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









