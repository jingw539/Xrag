package com.hospital.xray.service;

import com.hospital.xray.dto.ReportGenerateDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AsyncTaskService {

    private ReportService reportService;

    @Autowired
    public void setReportService(@Lazy ReportService reportService) {
        this.reportService = reportService;
    }

    @Async
    public void triggerAutoGenerateReport(Long caseId, Long imageId, Long doctorId) {
        log.info("Auto-generate report: caseId={}, imageId={}", caseId, imageId);
        try {
            ReportGenerateDTO dto = new ReportGenerateDTO();
            dto.setCaseId(caseId);
            dto.setImageId(imageId);
            dto.setTopK(3);
            reportService.generate(dto, doctorId);
            log.info("Auto-generate report done: caseId={}", caseId);
        } catch (Exception e) {
            log.warn("Auto-generate report failed caseId={}: {}", caseId, e.getMessage());
        }
    }
}
