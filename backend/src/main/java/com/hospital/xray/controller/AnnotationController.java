package com.hospital.xray.controller;

import com.hospital.xray.common.Result;
import com.hospital.xray.dto.AnnotationCreateDTO;
import com.hospital.xray.dto.AnnotationVO;
import com.hospital.xray.util.SecurityUtils;
import com.hospital.xray.service.AnnotationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "病灶标注", description = "影像病灶标注管理：AI自动标注与医生手动标注")
@RestController
@RequestMapping("/api/annotations")
@RequiredArgsConstructor
public class AnnotationController {

    private final AnnotationService annotationService;

    @Operation(summary = "获取影像的所有标注")
    @GetMapping("/image/{imageId}")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'QC', 'ADMIN')")
    public Result<List<AnnotationVO>> listByImage(@PathVariable Long imageId) {
        return Result.success(annotationService.listByImage(imageId));
    }

    @Operation(summary = "医生创建手动标注")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'QC', 'ADMIN')")
    public Result<AnnotationVO> create(@RequestBody AnnotationCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(annotationService.create(dto, userId));
    }

    @Operation(summary = "删除标注（仅限医生标注）")
    @DeleteMapping("/{annotationId}")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'QC', 'ADMIN')")
    public Result<Void> delete(@PathVariable Long annotationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        annotationService.delete(annotationId, userId);
        return Result.success(null);
    }

    @Operation(summary = "根据CheXbert标签生成AI标注（评测完成后调用）")
    @PostMapping("/ai/{imageId}/{reportId}")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'QC', 'ADMIN')")
    public Result<Void> generateAiAnnotations(
            @PathVariable Long imageId,
            @PathVariable Long reportId,
            @RequestParam String aiLabels) {
        annotationService.deleteAiAnnotations(imageId, reportId);
        annotationService.generateAiAnnotations(imageId, reportId, aiLabels);
        return Result.success(null);
    }
}
