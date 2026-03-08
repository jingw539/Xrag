package com.hospital.xray.service;

import com.hospital.xray.dto.AnnotationCreateDTO;
import com.hospital.xray.dto.AnnotationVO;

import java.util.List;
import java.util.Map;

public interface AnnotationService {

    List<AnnotationVO> listByImage(Long imageId);

    AnnotationVO create(AnnotationCreateDTO dto, Long userId);

    void delete(Long annotationId, Long userId);

    /** 根据 CheXbert 预测标签自动生成 AI 区域标注 */
    void generateAiAnnotations(Long imageId, Long reportId, String aiLabels);

    /** 根据 CheXbert 预测标签和概率自动生成带置信度的 AI 区域标注 */
    void generateAiAnnotationsWithConfidence(Long imageId, Long reportId,
                                             String aiLabels, Map<String, Double> labelProbabilities);

    /** 删除指定影像+报告的 AI 标注（重新生成前清理旧标注） */
    void deleteAiAnnotations(Long imageId, Long reportId);
}
