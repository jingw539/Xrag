package com.hospital.xray.controller;

import com.hospital.xray.annotation.OperationLog;
import com.hospital.xray.common.Result;
import com.hospital.xray.dto.ImageMetadataUpdateDTO;
import com.hospital.xray.dto.ImageUploadResult;
import com.hospital.xray.dto.ImageVO;
import com.hospital.xray.service.ImageService;
import com.hospital.xray.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Tag(name = "影像管理", description = "影像上传、浏览、缩略图与元数据维护接口")
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @Operation(summary = "上传影像")
    @PostMapping("/upload")
    @OperationLog(type = "IMAGE_UPLOAD", detail = "上传影像")
    public Result<ImageUploadResult> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("caseId") String caseId,
            @RequestParam(value = "viewPosition", required = false) String viewPosition,
            @RequestParam(value = "pixelSpacingXmm", required = false) Double pixelSpacingXmm,
            @RequestParam(value = "pixelSpacingYmm", required = false) Double pixelSpacingYmm) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        ImageUploadResult data = imageService.uploadImage(file, Long.parseLong(caseId), viewPosition, currentUserId,
                pixelSpacingXmm, pixelSpacingYmm);
        return Result.success(data, "上传成功");
    }

    @Operation(summary = "查询病例影像")
    @GetMapping
    public Result<List<ImageVO>> listImages(@RequestParam("caseId") String caseId) {
        return Result.success(imageService.listImagesByCaseId(Long.parseLong(caseId)));
    }

    @Operation(summary = "获取影像原图")
    @GetMapping("/{imageId}/content")
    public ResponseEntity<byte[]> getImageContent(@PathVariable String imageId) {
        Long id = Long.parseLong(imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imageService.getImageContentType(id, false)))
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(imageService.getImageContent(id, false));
    }

    @Operation(summary = "获取影像缩略图")
    @GetMapping("/{imageId}/thumbnail")
    public ResponseEntity<byte[]> getImageThumbnail(@PathVariable String imageId) {
        Long id = Long.parseLong(imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imageService.getImageContentType(id, true)))
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(imageService.getImageContent(id, true));
    }

    @Operation(summary = "查询历史影像")
    @GetMapping("/prior")
    public Result<List<ImageVO>> listPriorImages(@RequestParam("caseId") String caseId,
                                                 @RequestParam(value = "currentImageId", required = false) String currentImageId) {
        return Result.success(imageService.listPriorImages(Long.parseLong(caseId),
                currentImageId != null ? Long.parseLong(currentImageId) : null));
    }

    @Operation(summary = "更新影像元数据")
    @PutMapping("/{imageId}/metadata")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR')")
    @OperationLog(type = "IMAGE_METADATA_UPDATE", detail = "更新影像元数据")
    public Result<ImageVO> updateMetadata(@PathVariable String imageId, @RequestBody ImageMetadataUpdateDTO dto) {
        return Result.success(imageService.updateImageMetadata(Long.parseLong(imageId), dto));
    }

    @Operation(summary = "删除影像")
    @DeleteMapping("/{imageId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR')")
    @OperationLog(type = "IMAGE_DELETE", detail = "删除影像")
    public Result<Void> deleteImage(@PathVariable String imageId) {
        imageService.deleteImage(Long.parseLong(imageId));
        return Result.success(null, "删除成功");
    }
}
