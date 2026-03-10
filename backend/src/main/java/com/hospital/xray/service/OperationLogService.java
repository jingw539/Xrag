package com.hospital.xray.service;

import com.hospital.xray.dto.LogQueryDTO;
import com.hospital.xray.dto.OperationLogVO;
import com.hospital.xray.dto.PageResult;
import com.hospital.xray.entity.SysOperationLog;

/**
 * 操作日志服务
 */
public interface OperationLogService {

    void log(String operationType, String targetId, String detail);

    void saveLog(SysOperationLog log);

    void logError(String operationType, String errorMessage);

    PageResult<OperationLogVO> listLogs(LogQueryDTO query);

    byte[] exportLogs(LogQueryDTO query);
}
