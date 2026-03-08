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

    private final EvalService evalService;
    private ReportService reportService;

    public AsyncTaskService(EvalService evalService) {
        this.evalService = evalService;
    }

    @Autowired
    public void setReportService(@Lazy ReportService reportService) {
        this.reportService = reportService;
    }

    @Async
    public void triggerEvalAsync(Long reportId) {
        try {
            evalService.evaluate(reportId);
        } catch (Exception e) {
            log.warn("异步CheXbert评测失败 reportId={}: {}", reportId, e.getMessage());
        }
    }

    @Async
    public void triggerAutoGenerateReport(Long caseId, Long imageId, Long doctorId) {
        log.info("自动触发AI报告生成: caseId={}, imageId={}", caseId, imageId);
        try {
            ReportGenerateDTO dto = new ReportGenerateDTO();
            dto.setCaseId(caseId);
            dto.setImageId(imageId);
            dto.setTopK(3);
            reportService.generate(dto, doctorId);
            log.info("自动AI报告生成完成: caseId={}", caseId);
        } catch (Exception e) {
            log.warn("自动AI报告生成失败 caseId={}: {}", caseId, e.getMessage());
        }
    }
}
