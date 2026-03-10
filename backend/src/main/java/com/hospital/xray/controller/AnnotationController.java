package com.hospital.xray.controller;

import com.hospital.xray.common.Result;
import com.hospital.xray.dto.AnnotationCreateDTO;
import com.hospital.xray.dto.AnnotationUpdateDTO;
import com.hospital.xray.dto.AnnotationVO;
import com.hospital.xray.service.AnnotationService;
import com.hospital.xray.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "标注管理", description = "医生标注与 AI 自动标注相关接口")
@RestController
@RequestMapping("/api/annotations")
@RequiredArgsConstructor
public class AnnotationController {

    private final AnnotationService annotationService;

    @Operation(summary = "查询影像标注")
    @GetMapping("/image/{imageId}")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'QC', 'ADMIN')")
    public Result<List<AnnotationVO>> listByImage(@PathVariable Long imageId) {
        return Result.success(annotationService.listByImage(imageId));
    }

    @Operation(summary = "创建人工标注")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'ADMIN')")
    public Result<AnnotationVO> create(@RequestBody AnnotationCreateDTO dto) {
        return Result.success(annotationService.create(dto, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "更新人工标注")
    @PutMapping("/{annotationId}")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'ADMIN')")
    public Result<AnnotationVO> update(@PathVariable Long annotationId, @RequestBody AnnotationUpdateDTO dto) {
        return Result.success(annotationService.update(annotationId, dto, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "删除人工标注")
    @DeleteMapping("/{annotationId}")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'ADMIN')")
    public Result<Void> delete(@PathVariable Long annotationId) {
        annotationService.delete(annotationId, SecurityUtils.getCurrentUserId());
        return Result.success(null);
    }

    @Operation(summary = "根据 CheXbert 结果生成 AI 标注")
    @PostMapping("/ai/{imageId}/{reportId}")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'QC', 'ADMIN')")
    public Result<Void> generateAiAnnotations(@PathVariable Long imageId,
                                              @PathVariable Long reportId,
                                              @RequestParam String aiLabels) {
        annotationService.deleteAiAnnotations(imageId, reportId);
        annotationService.generateAiAnnotations(imageId, reportId, aiLabels);
        return Result.success(null);
    }
}
