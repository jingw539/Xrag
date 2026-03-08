package com.hospital.xray.exception;

import lombok.Getter;

/**
 * 业务异常基类
 */
@Getter
public class BusinessException extends RuntimeException {
    
    private int code;
    
    public BusinessException(String message) {
        this(400, message);
    }
    
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
