package com.hospital.xray.service;

import com.hospital.xray.dto.*;

import java.util.List;
import java.util.Map;

public interface ReportService {

    ReportDetailVO generate(ReportGenerateDTO dto, Long doctorId);

    ReportDetailVO regenerate(Long reportId, Long doctorId);

    void saveDraft(Long reportId, ReportSaveDTO dto, Long editorId);

    void sign(Long reportId, Long doctorId);

    ReportDetailVO getById(Long reportId);

    PageResult<ReportVO> listReports(ReportQueryDTO queryDTO);

    List<ReportEditHistoryVO> getEditHistory(Long reportId);

    Map<String, Object> polishReport(String findings, String impression);

    void revertToEdit(Long reportId, Long doctorId);

    Map<String, Object> getAiAdvice(Long reportId);
}
