package com.hospital.xray.exception;

/**
 * 病例已有签发报告异常
 */
public class CaseHasSignedReportException extends BusinessException {
    
    public CaseHasSignedReportException() {
        super(400, "已签发报告的病例不可删除");
    }
}
