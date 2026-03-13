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

@Tag(name = "Annotations", description = "Manual annotations APIs")
@RestController
@RequestMapping("/api/annotations")
@RequiredArgsConstructor
public class AnnotationController {

    private final AnnotationService annotationService;

    @Operation(summary = "List annotations by image")
    @GetMapping("/image/{imageId}")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'ADMIN')")
    public Result<List<AnnotationVO>> listByImage(@PathVariable Long imageId) {
        return Result.success(annotationService.listByImage(imageId));
    }

    @Operation(summary = "Create manual annotation")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'ADMIN')")
    public Result<AnnotationVO> create(@RequestBody AnnotationCreateDTO dto) {
        return Result.success(annotationService.create(dto, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "Update manual annotation")
    @PutMapping("/{annotationId}")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'ADMIN')")
    public Result<AnnotationVO> update(@PathVariable Long annotationId, @RequestBody AnnotationUpdateDTO dto) {
        return Result.success(annotationService.update(annotationId, dto, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "Delete manual annotation")
    @DeleteMapping("/{annotationId}")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'ADMIN')")
    public Result<Void> delete(@PathVariable Long annotationId) {
        annotationService.delete(annotationId, SecurityUtils.getCurrentUserId());
        return Result.success(null);
    }
}
