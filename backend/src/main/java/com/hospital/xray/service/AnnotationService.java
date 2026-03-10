package com.hospital.xray.service;

import com.hospital.xray.dto.AnnotationCreateDTO;
import com.hospital.xray.dto.AnnotationUpdateDTO;
import com.hospital.xray.dto.AnnotationVO;

import java.util.List;

public interface AnnotationService {

    List<AnnotationVO> listByImage(Long imageId);

    AnnotationVO create(AnnotationCreateDTO dto, Long userId);

    AnnotationVO update(Long annotationId, AnnotationUpdateDTO dto, Long userId);

    void delete(Long annotationId, Long userId);
}
