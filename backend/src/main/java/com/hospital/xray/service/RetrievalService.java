package com.hospital.xray.service;

import com.hospital.xray.dto.RetrievalResultVO;

import java.util.List;

public interface RetrievalService {

    RetrievalResultVO search(Long caseId, Long imageId, Integer topK);

    RetrievalResultVO getById(Long retrievalId);

    List<RetrievalResultVO> listByCaseId(Long caseId);
}
