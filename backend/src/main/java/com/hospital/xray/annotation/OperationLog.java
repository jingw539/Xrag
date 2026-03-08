package com.hospital.xray.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 * 用于标记需要记录操作日志的方法
 * 
 * 使用示例：
 * @OperationLog(type = "CASE_DELETE", detail = "删除病例")
 * public void deleteCase(Long caseId) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    
    /**
     * 操作类型
     * 如：LOGIN, CASE_VIEW, CASE_CREATE, CASE_UPDATE, CASE_DELETE, IMAGE_UPLOAD, TYPICAL_MARK
     */
    String type();
    
    /**
     * 操作详情描述
     */
    String detail() default "";
}
