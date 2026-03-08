package com.hospital.xray.service;

import com.hospital.xray.dto.LogQueryDTO;
import com.hospital.xray.dto.OperationLogVO;
import com.hospital.xray.dto.PageResult;
import com.hospital.xray.entity.SysOperationLog;

/**
 * 操作日志服务接口
 * 负责记录和查询系统操作日志
 */
public interface OperationLogService {
    
    /**
     * 记录操作日志
     * 
     * @param operationType 操作类型
     * @param targetId 操作目标ID
     * @param detail 操作详情
     */
    void log(String operationType, String targetId, String detail);
    
    /**
     * 保存操作日志实体
     * 
     * @param log 操作日志实体
     */
    void saveLog(SysOperationLog log);
    
    /**
     * 记录错误日志
     * 
     * @param operationType 操作类型
     * @param errorMessage 错误信息
     */
    void logError(String operationType, String errorMessage);
    
    /**
     * 分页查询操作日志
     * 
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<OperationLogVO> listLogs(LogQueryDTO query);
}
