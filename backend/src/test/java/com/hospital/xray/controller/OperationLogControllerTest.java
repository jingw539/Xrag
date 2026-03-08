package com.hospital.xray.controller;
import com.hospital.xray.entity.SysOperationLog;
import com.hospital.xray.mapper.SysOperationLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 操作日志控制器集成测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.hospital.xray.config.SecurityConfig.class)
@Transactional
class OperationLogControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private SysOperationLogMapper operationLogMapper;
    
    @BeforeEach
    void setUp() {
        // 准备测试数据
        createTestLog("CASE_VIEW", "123456", 1001L);
        createTestLog("CASE_CREATE", "123457", 1002L);
        createTestLog("CASE_UPDATE", "123458", 1001L);
    }
    
    @Test
    @WithMockUser(authorities = "ADMIN")
    void testListLogs_WithNoFilters() throws Exception {
        mockMvc.perform(get("/api/logs")
                .param("page", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(3)))
            .andExpect(jsonPath("$.data.list").isArray())
            .andExpect(jsonPath("$.data.list", hasSize(greaterThanOrEqualTo(3))));
    }
    
    @Test
    @WithMockUser(authorities = "ADMIN")
    void testListLogs_WithUserIdFilter() throws Exception {
        mockMvc.perform(get("/api/logs")
                .param("userId", "1001")
                .param("page", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.list").isArray())
            .andExpect(jsonPath("$.data.list[*].userId", everyItem(is(1001))));
    }
    
    @Test
    @WithMockUser(authorities = "ADMIN")
    void testListLogs_WithOperationTypeFilter() throws Exception {
        mockMvc.perform(get("/api/logs")
                .param("operationType", "CASE_CREATE")
                .param("page", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.list").isArray())
            .andExpect(jsonPath("$.data.list[*].operationType", everyItem(is("CASE_CREATE"))));
    }
    
    @Test
    @WithMockUser(authorities = "QC")
    void testListLogs_WithQCRole() throws Exception {
        // 质控人员也应该能访问日志
        mockMvc.perform(get("/api/logs")
                .param("page", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }
    
    @Test
    @WithMockUser(authorities = "ADMIN")
    void testListLogs_WithAdminRole_Success() throws Exception {
        // 管理员应该能访问日志
        mockMvc.perform(get("/api/logs")
                .param("page", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }
    
    @Test
    void testListLogs_Unauthorized() throws Exception {
        // 未登录用户不应该能访问日志
        mockMvc.perform(get("/api/logs")
                .param("page", "1")
                .param("pageSize", "20"))
            .andExpect(status().isUnauthorized());
    }
    
    @Test
    @WithMockUser(authorities = "ADMIN")
    void testListLogs_Pagination() throws Exception {
        // 创建更多测试数据
        for (int i = 0; i < 15; i++) {
            createTestLog("CASE_VIEW", "12345" + i, 1001L);
        }
        
        // 测试第1页
        mockMvc.perform(get("/api/logs")
                .param("page", "1")
                .param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.list", hasSize(10)));
        
        // 测试第2页
        mockMvc.perform(get("/api/logs")
                .param("page", "2")
                .param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.list", hasSize(greaterThanOrEqualTo(8))));
    }
    
    /**
     * 创建测试日志
     */
    private SysOperationLog createTestLog(String operationType, String targetId, Long userId) {
        SysOperationLog log = new SysOperationLog();
        log.setUserId(userId);
        log.setOperationType(operationType);
        log.setTargetId(targetId);
        log.setDetail("测试操作: " + operationType);
        log.setClientIp("192.168.1.100");
        log.setApiPath("/api/test");
        log.setElapsedMs(100);
        log.setCreatedAt(LocalDateTime.now());
        
        operationLogMapper.insert(log);
        return log;
    }
}
