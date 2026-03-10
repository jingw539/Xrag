package com.hospital.xray.service;

import com.hospital.xray.dto.AnnotationCreateDTO;
import com.hospital.xray.dto.AnnotationUpdateDTO;
import com.hospital.xray.dto.AnnotationVO;

import java.util.List;
import java.util.Map;

public interface AnnotationService {

    List<AnnotationVO> listByImage(Long imageId);

    AnnotationVO create(AnnotationCreateDTO dto, Long userId);

    AnnotationVO update(Long annotationId, AnnotationUpdateDTO dto, Long userId);

    void delete(Long annotationId, Long userId);

    void generateAiAnnotations(Long imageId, Long reportId, String aiLabels);

    void generateAiAnnotationsWithConfidence(Long imageId, Long reportId,
                                             String aiLabels, Map<String, Double> labelProbabilities);

    void deleteAiAnnotations(Long imageId, Long reportId);
}
