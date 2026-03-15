package com.hospital.xray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.xray.dto.AnnotationCreateDTO;
import com.hospital.xray.dto.AnnotationUpdateDTO;
import com.hospital.xray.dto.AnnotationVO;
import com.hospital.xray.entity.CaseInfo;
import com.hospital.xray.entity.ImageInfo;
import com.hospital.xray.entity.ImageAnnotation;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.mapper.CaseInfoMapper;
import com.hospital.xray.mapper.ImageInfoMapper;
import com.hospital.xray.mapper.ImageAnnotationMapper;
import com.hospital.xray.mapper.SysUserMapper;
import com.hospital.xray.service.AnnotationService;
import com.hospital.xray.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnotationServiceImpl implements AnnotationService {

    private final ImageAnnotationMapper annotationMapper;
    private final ImageInfoMapper imageInfoMapper;
    private final CaseInfoMapper caseInfoMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public List<AnnotationVO> listByImage(Long imageId) {
        assertImageReadable(imageId);
        List<ImageAnnotation> annotations = annotationMapper.selectList(
                        new LambdaQueryWrapper<ImageAnnotation>()
                                .eq(ImageAnnotation::getImageId, imageId)
                                .orderByAsc(ImageAnnotation::getSource)
                                .orderByAsc(ImageAnnotation::getCreatedAt));
        List<Long> userIds = annotations.stream()
                .map(ImageAnnotation::getCreatedBy)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> userNameMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(u -> u.getUserId(), u -> u.getRealName() != null ? u.getRealName() : ""));
        return annotations.stream().map(a -> toVO(a, userNameMap)).collect(Collectors.toList());
    }

    @Override
    public AnnotationVO create(AnnotationCreateDTO dto, Long userId) {
        assertImageWritable(dto.getImageId(), userId);
        ImageAnnotation anno = new ImageAnnotation();
        anno.setImageId(dto.getImageId());
        anno.setReportId(dto.getReportId());
        anno.setSource("DOCTOR");
        anno.setAnnoType(dto.getAnnoType() != null ? dto.getAnnoType() : "RECTANGLE");
        anno.setLabel(dto.getLabel());
        anno.setRemark(dto.getRemark());
        anno.setX(dto.getX());
        anno.setY(dto.getY());
        anno.setWidth(dto.getWidth());
        anno.setHeight(dto.getHeight());
        anno.setMeasuredWidthMm(dto.getMeasuredWidthMm());
        anno.setMeasuredHeightMm(dto.getMeasuredHeightMm());
        anno.setCompareStatus(dto.getCompareStatus());
        anno.setCompareNote(dto.getCompareNote());
        anno.setColor(dto.getColor() != null ? dto.getColor() : "#52c41a");
        anno.setConfidence(null);
        anno.setCreatedBy(userId);
        anno.setCreatedAt(LocalDateTime.now());
        annotationMapper.insert(anno);
        return toVO(anno, null);
    }

    @Override
    public AnnotationVO update(Long annotationId, AnnotationUpdateDTO dto, Long userId) {
        ImageAnnotation anno = annotationMapper.selectById(annotationId);
        if (anno == null) throw new BusinessException("Annotation not found");
        assertImageWritable(anno.getImageId(), userId);
        if (!"DOCTOR".equals(anno.getSource())) throw new BusinessException(403, "AI annotations cannot be edited");
        if (dto.getAnnoType() != null) anno.setAnnoType(dto.getAnnoType());
        if (dto.getLabel() != null) anno.setLabel(dto.getLabel());
        if (dto.getRemark() != null) anno.setRemark(dto.getRemark());
        if (dto.getX() != null) anno.setX(dto.getX());
        if (dto.getY() != null) anno.setY(dto.getY());
        if (dto.getWidth() != null) anno.setWidth(dto.getWidth());
        if (dto.getHeight() != null) anno.setHeight(dto.getHeight());
        if (dto.getMeasuredWidthMm() != null) anno.setMeasuredWidthMm(dto.getMeasuredWidthMm());
        if (dto.getMeasuredHeightMm() != null) anno.setMeasuredHeightMm(dto.getMeasuredHeightMm());
        if (dto.getCompareStatus() != null) anno.setCompareStatus(dto.getCompareStatus());
        if (dto.getCompareNote() != null) anno.setCompareNote(dto.getCompareNote());
        if (dto.getColor() != null) anno.setColor(dto.getColor());
        annotationMapper.updateById(anno);
        return toVO(annotationMapper.selectById(annotationId), null);
    }

    @Override
    public void delete(Long annotationId, Long userId) {
        ImageAnnotation anno = annotationMapper.selectById(annotationId);
        if (anno == null) throw new BusinessException(404, "Annotation not found");
        assertImageWritable(anno.getImageId(), userId);
        if (!"DOCTOR".equals(anno.getSource())) throw new BusinessException(403, "AI annotations cannot be deleted");
        annotationMapper.deleteById(annotationId);
    }

    private void assertImageReadable(Long imageId) {
        if (!SecurityUtils.hasRole("DOCTOR")) {
            return;
        }
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "Not logged in");
        }
        ImageInfo imageInfo = imageInfoMapper.selectById(imageId);
        if (imageInfo == null) {
            throw new BusinessException(404, "Image not found");
        }
        CaseInfo caseInfo = caseInfoMapper.selectById(imageInfo.getCaseId());
        if (caseInfo == null) {
            throw new BusinessException(404, "Case not found");
        }
        // Read-only access is allowed for other doctors' cases.
    }

    private void assertImageWritable(Long imageId, Long userId) {
        if (SecurityUtils.isAdmin()) {
            return;
        }
        if (!SecurityUtils.hasRole("DOCTOR")) {
            return;
        }
        Long currentUserId = userId != null ? userId : SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "Not logged in");
        }
        ImageInfo imageInfo = imageInfoMapper.selectById(imageId);
        if (imageInfo == null) {
            throw new BusinessException(404, "Image not found");
        }
        CaseInfo caseInfo = caseInfoMapper.selectById(imageInfo.getCaseId());
        if (caseInfo == null) {
            throw new BusinessException(404, "Case not found");
        }
        if (caseInfo.getResponsibleDoctorId() == null) {
            throw new BusinessException(403, "Case not assigned");
        }
        if (!currentUserId.equals(caseInfo.getResponsibleDoctorId())) {
            throw new BusinessException(403, "Cannot operate on other doctor case");
        }
    }

    private AnnotationVO toVO(ImageAnnotation a, Map<Long, String> userNameMap) {
        AnnotationVO vo = new AnnotationVO();
        vo.setAnnotationId(a.getAnnotationId());
        vo.setImageId(a.getImageId());
        vo.setReportId(a.getReportId());
        vo.setSource(a.getSource());
        vo.setAnnoType(a.getAnnoType());
        vo.setLabel(a.getLabel());
        vo.setRemark(a.getRemark());
        vo.setX(a.getX());
        vo.setY(a.getY());
        vo.setWidth(a.getWidth());
        vo.setHeight(a.getHeight());
        vo.setMeasuredWidthMm(a.getMeasuredWidthMm());
        vo.setMeasuredHeightMm(a.getMeasuredHeightMm());
        vo.setCompareStatus(a.getCompareStatus());
        vo.setCompareNote(a.getCompareNote());
        vo.setColor(a.getColor());
        vo.setConfidence(a.getConfidence());
        vo.setCreatedAt(a.getCreatedAt());
        if (a.getCreatedBy() != null) {
            if (userNameMap != null) {
                vo.setCreatedByName(userNameMap.get(a.getCreatedBy()));
            } else {
                var user = sysUserMapper.selectById(a.getCreatedBy());
                if (user != null) vo.setCreatedByName(user.getRealName());
            }
        }
        return vo;
    }
}
