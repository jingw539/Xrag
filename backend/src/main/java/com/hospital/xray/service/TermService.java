package com.hospital.xray.service;

import com.hospital.xray.dto.TermCorrectionVO;

import java.util.List;

public interface TermService {

    List<TermCorrectionVO> analyzeReport(Long reportId, String draftFindings, String draftImpression);

    List<TermCorrectionVO> getByReportId(Long reportId);

    void acceptCorrection(Long correctionId);

    void dismissCorrection(Long correctionId);
}
