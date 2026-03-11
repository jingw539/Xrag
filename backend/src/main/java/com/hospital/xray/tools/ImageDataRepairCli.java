package com.hospital.xray.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.xray.ChestXrayApplication;
import com.hospital.xray.entity.ImageInfo;
import com.hospital.xray.mapper.ImageInfoMapper;
import com.hospital.xray.service.ImageService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class ImageDataRepairCli {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(ChestXrayApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
        int exitCode = 0;
        try {
            context.getBean(Runner.class).run(args);
        } catch (Exception e) {
            log.error("Image repair failed", e);
            exitCode = 1;
        } finally {
            org.springframework.boot.SpringApplication.exit(context);
            System.exit(exitCode);
        }
    }

    @Component
    static class Runner {
        private final ImageInfoMapper imageInfoMapper;
        private final MinioClient minioClient;
        private final ImageService imageService;

        @Value("${minio.bucket-name}")
        private String bucketName;

        Runner(ImageInfoMapper imageInfoMapper, MinioClient minioClient, ImageService imageService) {
            this.imageInfoMapper = imageInfoMapper;
            this.minioClient = minioClient;
            this.imageService = imageService;
        }

        void run(String[] args) throws Exception {
            List<Long> imageIds = parseImageIds(args);
            if (imageIds.isEmpty()) {
                throw new IllegalArgumentException("Missing --repair.image.ids=... argument");
            }
            for (Long imageId : imageIds) {
                repairImage(imageId);
            }
        }

        private void repairImage(Long imageId) throws Exception {
            ImageInfo target = imageInfoMapper.selectById(imageId);
            if (target == null) {
                log.warn("[SKIP] image not found: {}", imageId);
                return;
            }
            String thumbPath = thumbnailPath(target.getFilePath());
            boolean originalExists = objectExistsSafe(target.getFilePath());
            boolean thumbnailExists = objectExistsSafe(thumbPath);
            log.info("[CHECK] imageId={} original={} thumbnail={} path={}",
                    imageId, originalExists, thumbnailExists, target.getFilePath());
            if (originalExists && thumbnailExists) {
                log.info("[OK] image already complete: {}", imageId);
                return;
            }

            List<ImageInfo> candidates = imageInfoMapper.selectList(new LambdaQueryWrapper<ImageInfo>()
                    .eq(ImageInfo::getCaseId, target.getCaseId())
                    .ne(ImageInfo::getImageId, target.getImageId())
                    .orderByDesc(ImageInfo::getCreatedAt));
            ImageInfo source = candidates.stream().filter(img -> objectExistsSafe(img.getFilePath())).findFirst().orElse(null);
            if (source == null) {
                log.warn("[WARN] no valid source object found in same case for imageId={}", imageId);
                return;
            }

            if (!originalExists) {
                RepairPayload payload = buildPayloadForTarget(source, target);
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(target.getFilePath())
                        .stream(new ByteArrayInputStream(payload.bytes), payload.bytes.length, -1)
                        .contentType(payload.contentType)
                        .build());
                target.setFileSize((long) payload.bytes.length);
                target.setImgWidth(payload.width);
                target.setImgHeight(payload.height);
                imageInfoMapper.updateById(target);
                log.info("[REPAIRED] original restored for imageId={} from source={}", imageId, source.getImageId());
            }

            if (!thumbnailExists) {
                imageService.generateThumbnail(target.getFilePath(), thumbPath);
                log.info("[REPAIRED] thumbnail regenerated for imageId={}", imageId);
            }
        }

        private RepairPayload buildPayloadForTarget(ImageInfo source, ImageInfo target) throws Exception {
            byte[] sourceBytes = readObject(source.getFilePath());
            String targetType = normalizeType(target.getFileType());
            BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(sourceBytes));
            if (sourceImage == null) {
                return new RepairPayload(sourceBytes, guessContentType(source.getFileType()), source.getImgWidth(), source.getImgHeight());
            }
            String writeFormat = "png".equals(targetType) ? "png" : "jpg";
            BufferedImage outputImage = sourceImage;
            if ("jpg".equals(writeFormat) && sourceImage.getType() != BufferedImage.TYPE_INT_RGB) {
                BufferedImage rgb = new BufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), BufferedImage.TYPE_INT_RGB);
                rgb.getGraphics().drawImage(sourceImage, 0, 0, null);
                outputImage = rgb;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(outputImage, writeFormat, out);
            return new RepairPayload(out.toByteArray(), guessContentType(target.getFileType()), outputImage.getWidth(), outputImage.getHeight());
        }

        private byte[] readObject(String objectName) throws Exception {
            try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(objectName).build())) {
                return inputStream.readAllBytes();
            }
        }

        private boolean objectExistsSafe(String objectName) {
            try {
                return objectExists(objectName);
            } catch (Exception e) {
                return false;
            }
        }

        private boolean objectExists(String objectName) throws Exception {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(objectName).build());
            return true;
        }

        private String thumbnailPath(String originalPath) {
            int lastDotIndex = originalPath.lastIndexOf('.');
            return lastDotIndex > 0 ? originalPath.substring(0, lastDotIndex) + "_thumb.jpg" : originalPath + "_thumb.jpg";
        }

        private List<Long> parseImageIds(String[] args) {
            return Arrays.stream(args)
                    .filter(Objects::nonNull)
                    .filter(arg -> arg.startsWith("--repair.image.ids="))
                    .findFirst()
                    .map(arg -> arg.substring("--repair.image.ids=".length()))
                    .stream()
                    .flatMap(text -> Arrays.stream(text.split(",")))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        }

        private String normalizeType(String fileType) {
            if (fileType == null) return "jpg";
            String normalized = fileType.toLowerCase();
            if ("jpeg".equals(normalized) || "jpg".equals(normalized)) return "jpg";
            if ("png".equals(normalized)) return "png";
            return normalized;
        }

        private String guessContentType(String fileType) {
            return switch (normalizeType(fileType)) {
                case "png" -> "image/png";
                default -> "image/jpeg";
            };
        }

        private record RepairPayload(byte[] bytes, String contentType, Integer width, Integer height) {}
    }
}
