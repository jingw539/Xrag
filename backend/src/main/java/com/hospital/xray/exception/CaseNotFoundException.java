package com.hospital.xray.exception;

/**
 * 病例不存在异常
 */
public class CaseNotFoundException extends BusinessException {
    
    public CaseNotFoundException(Long caseId) {
        super(404, "病例不存在: " + caseId);
    }
}
