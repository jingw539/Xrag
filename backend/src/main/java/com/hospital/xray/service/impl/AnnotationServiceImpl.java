package com.hospital.xray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.xray.dto.AnnotationCreateDTO;
import com.hospital.xray.dto.AnnotationUpdateDTO;
import com.hospital.xray.dto.AnnotationVO;
import com.hospital.xray.entity.ImageAnnotation;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.mapper.ImageAnnotationMapper;
import com.hospital.xray.mapper.SysUserMapper;
import com.hospital.xray.service.AnnotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnotationServiceImpl implements AnnotationService {

    private final ImageAnnotationMapper annotationMapper;
    private final SysUserMapper sysUserMapper;

    /* ─── CheXbert 14 标签 → 解剖区域映射（归一化坐标 x,y,w,h，基于标准PA位胸片解剖） ─── */
    private static final Map<String, double[]> LABEL_REGIONS = Map.ofEntries(
        Map.entry("Atelectasis",                new double[]{0.05, 0.55, 0.90, 0.38}),
        Map.entry("Cardiomegaly",               new double[]{0.28, 0.28, 0.44, 0.42}),
        Map.entry("Consolidation",              new double[]{0.05, 0.22, 0.90, 0.62}),
        Map.entry("Edema",                      new double[]{0.05, 0.12, 0.90, 0.72}),
        Map.entry("Enlarged Cardiomediastinum", new double[]{0.28, 0.08, 0.44, 0.55}),
        Map.entry("Fracture",                   new double[]{0.05, 0.05, 0.90, 0.30}),
        Map.entry("Lung Lesion",                new double[]{0.10, 0.12, 0.80, 0.68}),
        Map.entry("Lung Opacity",               new double[]{0.05, 0.08, 0.90, 0.75}),
        Map.entry("Pleural Effusion",           new double[]{0.05, 0.58, 0.90, 0.38}),
        Map.entry("Pleural Other",              new double[]{0.05, 0.08, 0.90, 0.80}),
        Map.entry("Pneumonia",                  new double[]{0.05, 0.20, 0.90, 0.65}),
        Map.entry("Pneumothorax",               new double[]{0.05, 0.05, 0.44, 0.80}),
        Map.entry("Support Devices",            new double[]{0.20, 0.03, 0.60, 0.30})
    );

    private static final Map<String, String> LABEL_COLORS = Map.ofEntries(
        Map.entry("Atelectasis",                "#b37feb"),
        Map.entry("Cardiomegaly",               "#ff7875"),
        Map.entry("Consolidation",              "#ffc53d"),
        Map.entry("Edema",                      "#40a9ff"),
        Map.entry("Enlarged Cardiomediastinum", "#ff85c2"),
        Map.entry("Fracture",                   "#d4b106"),
        Map.entry("Lung Lesion",                "#ff7a45"),
        Map.entry("Lung Opacity",               "#ffa940"),
        Map.entry("Pleural Effusion",           "#36cfc9"),
        Map.entry("Pleural Other",              "#69b1ff"),
        Map.entry("Pneumonia",                  "#ff9c6e"),
        Map.entry("Pneumothorax",               "#ff4d4f"),
        Map.entry("Support Devices",            "#95de64")
    );

    private static final Map<String, String> LABEL_NAMES = Map.ofEntries(
        Map.entry("Atelectasis",                "肺不张"),
        Map.entry("Cardiomegaly",               "心脏扩大"),
        Map.entry("Consolidation",              "实变"),
        Map.entry("Edema",                      "肺水肿"),
        Map.entry("Enlarged Cardiomediastinum", "纵隔增宽"),
        Map.entry("Fracture",                   "骨折"),
        Map.entry("Lung Lesion",                "肺部病变"),
        Map.entry("Lung Opacity",               "肺部阴影"),
        Map.entry("Pleural Effusion",           "胸腔积液"),
        Map.entry("Pleural Other",              "胸膜病变"),
        Map.entry("Pneumonia",                  "肺炎"),
        Map.entry("Pneumothorax",               "气胸"),
        Map.entry("Support Devices",            "医疗装置")
    );

    @Override
    public List<AnnotationVO> listByImage(Long imageId) {
        return annotationMapper.selectList(
                new LambdaQueryWrapper<ImageAnnotation>()
                        .eq(ImageAnnotation::getImageId, imageId)
                        .orderByAsc(ImageAnnotation::getSource)
                        .orderByAsc(ImageAnnotation::getCreatedAt))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public AnnotationVO create(AnnotationCreateDTO dto, Long userId) {
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
        return toVO(anno);
    }


    @Override
    public AnnotationVO update(Long annotationId, AnnotationUpdateDTO dto, Long userId) {
        ImageAnnotation anno = annotationMapper.selectById(annotationId);
        if (anno == null) throw new BusinessException("标注不存在");
        if (!"DOCTOR".equals(anno.getSource())) throw new BusinessException(403, "AI 标注不允许修改");
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
        return toVO(annotationMapper.selectById(annotationId));
    }

    @Override
    public void delete(Long annotationId, Long userId) {
        ImageAnnotation anno = annotationMapper.selectById(annotationId);
        if (anno == null) throw new BusinessException(404, "标注不存在");
        if (!"DOCTOR".equals(anno.getSource())) throw new BusinessException(403, "AI标注不可删除");
        annotationMapper.deleteById(annotationId);
    }

    @Override
    public void generateAiAnnotations(Long imageId, Long reportId, String aiLabels) {
        generateAiAnnotationsWithConfidence(imageId, reportId, aiLabels, null);
    }

    @Override
    public void generateAiAnnotationsWithConfidence(Long imageId, Long reportId,
                                                     String aiLabels, Map<String, Double> labelProbabilities) {
        if (aiLabels == null || aiLabels.isBlank()) return;

        List<String> labels = Arrays.stream(aiLabels.split("[,\\[\\]\"'\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !"No Finding".equalsIgnoreCase(s))
                .collect(Collectors.toList());

        long seed = imageId * 31 + reportId;
        List<ImageAnnotation> toSave = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            double[] baseRegion = LABEL_REGIONS.get(label);
            if (baseRegion == null) continue;

            Double confidence = (labelProbabilities != null) ? labelProbabilities.get(label) : null;

            double varianceFactor = (confidence != null && confidence > 0.5) ? (1.0 - confidence) * 0.15 : 0.08;
            long hash = (seed * 127 + label.hashCode()) & 0x7fffffffL;
            double dx = ((hash % 100) / 100.0 - 0.5) * varianceFactor;
            double dy = (((hash / 100) % 100) / 100.0 - 0.5) * varianceFactor;
            double dw = (((hash / 10000) % 100) / 100.0 - 0.5) * varianceFactor * 0.5;
            double dh = (((hash / 1000000) % 100) / 100.0 - 0.5) * varianceFactor * 0.5;

            double x = clamp(baseRegion[0] + dx, 0.0, 0.95);
            double y = clamp(baseRegion[1] + dy, 0.0, 0.95);
            double w = clamp(baseRegion[2] + dw, 0.05, 1.0 - x);
            double h = clamp(baseRegion[3] + dh, 0.05, 1.0 - y);

            ImageAnnotation anno = new ImageAnnotation();
            anno.setImageId(imageId);
            anno.setReportId(reportId);
            anno.setSource("AI");
            anno.setAnnoType("RECTANGLE");
            anno.setLabel(LABEL_NAMES.getOrDefault(label, label));
            anno.setRemark(label);
            anno.setX(Math.round(x * 10000.0) / 10000.0);
            anno.setY(Math.round(y * 10000.0) / 10000.0);
            anno.setWidth(Math.round(w * 10000.0) / 10000.0);
            anno.setHeight(Math.round(h * 10000.0) / 10000.0);
            anno.setColor(LABEL_COLORS.getOrDefault(label, "#1890ff"));
            anno.setConfidence(confidence);
            anno.setCreatedAt(LocalDateTime.now());
            toSave.add(anno);
        }

        for (ImageAnnotation a : toSave) {
            annotationMapper.insert(a);
        }
        log.info("为影像 {} 报告 {} 生成 {} 条AI标注（含置信度）：{}", imageId, reportId, toSave.size(), labels);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void deleteAiAnnotations(Long imageId, Long reportId) {
        annotationMapper.delete(
                new LambdaQueryWrapper<ImageAnnotation>()
                        .eq(ImageAnnotation::getImageId, imageId)
                        .eq(ImageAnnotation::getReportId, reportId)
                        .eq(ImageAnnotation::getSource, "AI"));
    }

    private AnnotationVO toVO(ImageAnnotation a) {
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
            var user = sysUserMapper.selectById(a.getCreatedBy());
            if (user != null) vo.setCreatedByName(user.getRealName());
        }
        return vo;
    }
}
