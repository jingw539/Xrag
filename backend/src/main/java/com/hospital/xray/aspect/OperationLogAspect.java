package com.hospital.xray.aspect;

import com.hospital.xray.annotation.OperationLog;
import com.hospital.xray.entity.SysOperationLog;
import com.hospital.xray.service.OperationLogService;
import com.hospital.xray.util.RequestUtils;
import com.hospital.xray.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 操作日志 AOP 切面
 * 自动记录带有 @OperationLog 注解的方法的操作日志
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {
    
    private final OperationLogService operationLogService;
    
    /**
     * 环绕通知，拦截带有 @OperationLog 注解的方法
     */
    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        try {
            // 执行目标方法
            Object result = joinPoint.proceed();
            
            // 计算耗时
            long elapsedMs = System.currentTimeMillis() - startTime;
            
            // 记录成功日志
            recordLog(joinPoint, operationLog, elapsedMs, null);
            
            return result;
        } catch (Exception e) {
            // 计算耗时
            long elapsedMs = System.currentTimeMillis() - startTime;
            
            // 记录失败日志
            recordLog(joinPoint, operationLog, elapsedMs, e.getMessage());
            
            // 重新抛出异常
            throw e;
        }
    }
    
    /**
     * 记录操作日志
     */
    private void recordLog(ProceedingJoinPoint joinPoint, OperationLog operationLog, 
                          long elapsedMs, String errorMessage) {
        try {
            // 创建日志实体
            SysOperationLog logEntity = new SysOperationLog();
            logEntity.setUserId(SecurityUtils.getCurrentUserId());
            logEntity.setOperationType(operationLog.type());
            logEntity.setTargetId(extractTargetId(joinPoint));
            
            // 设置详情信息
            String detail = operationLog.detail();
            if (errorMessage != null) {
                detail = detail + " - 失败: " + errorMessage;
            }
            logEntity.setDetail(detail);
            
            logEntity.setClientIp(RequestUtils.getClientIp());
            logEntity.setApiPath(RequestUtils.getRequestPath());
            logEntity.setElapsedMs((int) elapsedMs);
            logEntity.setCreatedAt(LocalDateTime.now());
            
            // 异步保存日志
            operationLogService.saveLog(logEntity);
            
        } catch (Exception e) {
            // 日志记录失败不应影响主业务
            log.error("AOP记录操作日志失败", e);
        }
    }
    
    /**
     * 从方法参数中提取目标ID。
     * 策略：优先 Long/Integer 参数；次优取看起来像 ID 的短 String（全数字或 ≤ 32 字符）；
     * 对 DTO/Map/MultipartFile 等复杂对象不序列化，直接返回 null，避免 VARCHAR 溢出。
     */
    private String extractTargetId(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) return null;
        // 第一优先：Long / Integer 参数
        for (Object arg : args) {
            if (arg instanceof Long || arg instanceof Integer) {
                return arg.toString();
            }
        }
        // 第二优先：短字符串（路径变量通常是数字 ID，≤ 32 字符）
        for (Object arg : args) {
            if (arg instanceof String s && s.length() <= 32) {
                return s;
            }
        }
        return null;
    }
}
