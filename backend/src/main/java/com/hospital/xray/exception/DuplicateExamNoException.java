package com.hospital.xray.exception;

/**
 * 检查号重复异常
 */
public class DuplicateExamNoException extends BusinessException {
    
    public DuplicateExamNoException(String examNo) {
        super(400, "检查号已存在，请检查是否重复录入: " + examNo);
    }
}
