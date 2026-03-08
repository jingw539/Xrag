package com.hospital.xray.controller;

import com.hospital.xray.annotation.OperationLog;
import com.hospital.xray.common.Result;
import com.hospital.xray.dto.ImageUploadResult;
import com.hospital.xray.dto.ImageVO;
import com.hospital.xray.service.ImageService;
import com.hospital.xray.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 影像管理控制器
 */
@Tag(name = "影像管理", description = "影像的上传、查询、删除等功能")
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;
    
    /**
     * 上传影像
     * 
     * @param file 影像文件
     * @param caseId 所属病例ID
     * @param viewPosition 投照体位（可选）
     * @return 上传结果
     */
    @Operation(summary = "上传影像", description = "上传医学影像文件到MinIO存储")
    @PostMapping("/upload")
    @OperationLog(type = "IMAGE_UPLOAD", detail = "上传影像")
    public Result<ImageUploadResult> uploadImage(
            @Parameter(description = "影像文件", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "所属病例ID", required = true) @RequestParam("caseId") String caseId,
            @Parameter(description = "投照体位") @RequestParam(value = "viewPosition", required = false) String viewPosition) {
        
        Long currentUserId = SecurityUtils.getCurrentUserId();
        ImageUploadResult data = imageService.uploadImage(file, Long.parseLong(caseId), viewPosition, currentUserId);
        return Result.success(data, "上传成功");
    }
    
    /**
     * 查询病例的所有影像
     * 
     * @param caseId 病例ID
     * @return 影像列表
     */
    @Operation(summary = "查询病例影像", description = "获取指定病例的所有影像列表")
    @GetMapping
    public Result<List<ImageVO>> listImages(
            @Parameter(description = "病例ID", required = true) @RequestParam("caseId") String caseId) {
        List<ImageVO> images = imageService.listImagesByCaseId(Long.parseLong(caseId));
        
        return Result.success(images);
    }
    
    /**
     * 删除影像
     * 
     * @param imageId 影像ID
     * @return 删除结果
     */
    @Operation(summary = "删除影像", description = "删除指定的影像文件及其元数据")
    @DeleteMapping("/{imageId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR')")
    @OperationLog(type = "IMAGE_DELETE", detail = "删除影像")
    public Result<Void> deleteImage(
            @Parameter(description = "影像ID", required = true) @PathVariable String imageId) {
        imageService.deleteImage(Long.parseLong(imageId));
        
        return Result.success(null, "删除成功");
    }
}
