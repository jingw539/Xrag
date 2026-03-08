package com.hospital.xray.controller;

import com.hospital.xray.service.CaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 病例控制器单元测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaseControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private CaseService caseService;
    
    @Test
    @WithMockUser(authorities = "ADMIN")
    void testImportCases_Success() throws Exception {
        // 准备 CSV 文件内容
        String csvContent = "检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室\n" +
                "EX20240401001,P100001,M,45,2024-04-01 10:30:00,胸部,放射科\n" +
                "EX20240401002,P100002,F,38,2024-04-01 11:00:00,胸部,急诊科\n" +
                "EX20240401003,P100003,M,52,2024-04-01 11:30:00,胸部,放射科";
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test_cases.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );
        
        // 执行请求
        mockMvc.perform(multipart("/api/cases/import")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.totalRows").value(3))
                .andExpect(jsonPath("$.data.successCount").value(3))
                .andExpect(jsonPath("$.data.failedCount").value(0));
    }
    
    @Test
    @WithMockUser(authorities = "ADMIN")
    void testImportCases_EmptyFile() throws Exception {
        // 准备空文件
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.csv",
                "text/csv",
                new byte[0]
        );
        
        // 执行请求
        mockMvc.perform(multipart("/api/cases/import")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("文件不能为空"));
    }
    
    @Test
    @WithMockUser(authorities = "ADMIN")
    void testImportCases_InvalidFileFormat() throws Exception {
        // 准备非 CSV 文件
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "This is not a CSV file".getBytes(StandardCharsets.UTF_8)
        );
        
        // 执行请求
        mockMvc.perform(multipart("/api/cases/import")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("仅支持 CSV 格式文件"));
    }
    
    @Test
    @WithMockUser(authorities = "ADMIN")
    void testImportCases_InvalidHeader() throws Exception {
        // 准备错误表头的 CSV 文件
        String csvContent = "错误表头1,错误表头2,错误表头3\n" +
                "EX20240401001,P100001,M";
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invalid_header.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );
        
        // 执行请求
        mockMvc.perform(multipart("/api/cases/import")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("CSV 文件格式错误，期望表头：检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室"));
    }
    
    @Test
    @WithMockUser(authorities = "ADMIN")
    void testImportCases_MissingRequiredFields() throws Exception {
        // 准备缺少必填字段的 CSV 文件
        String csvContent = "检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室\n" +
                ",P100001,M,45,2024-04-01 10:30:00,胸部,放射科\n" +
                "EX20240401002,,F,38,2024-04-01 11:00:00,胸部,急诊科";
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "missing_fields.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );
        
        // 执行请求
        mockMvc.perform(multipart("/api/cases/import")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(2))
                .andExpect(jsonPath("$.data.errors[0].row").value(2))
                .andExpect(jsonPath("$.data.errors[0].reason").value("必填字段缺失：检查号"))
                .andExpect(jsonPath("$.data.errors[1].row").value(3))
                .andExpect(jsonPath("$.data.errors[1].reason").value("必填字段缺失：患者匿名ID"));
    }
    
    @Test
    @WithMockUser(authorities = "ADMIN")
    void testImportCases_DuplicateExamNo() throws Exception {
        // 准备包含重复检查号的 CSV 文件
        String csvContent = "检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室\n" +
                "EX20240401010,P100010,M,45,2024-04-01 10:30:00,胸部,放射科\n" +
                "EX20240401010,P100011,F,38,2024-04-01 11:00:00,胸部,急诊科";
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "duplicate.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );
        
        // 执行请求
        mockMvc.perform(multipart("/api/cases/import")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].row").value(3))
                .andExpect(jsonPath("$.data.errors[0].reason").value("检查号已存在"));
    }
    
    @Test
    @WithMockUser(authorities = "ADMIN")
    void testImportCases_InvalidDateFormat() throws Exception {
        // 准备包含错误日期格式的 CSV 文件
        String csvContent = "检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室\n" +
                "EX20240401020,P100020,M,45,2024/04/01 10:30:00,胸部,放射科";
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invalid_date.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );
        
        // 执行请求
        mockMvc.perform(multipart("/api/cases/import")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalRows").value(1))
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].row").value(2))
                .andExpect(jsonPath("$.data.errors[0].reason").value("检查时间格式错误，期望格式：yyyy-MM-dd HH:mm:ss"));
    }
    
    @Test
    @WithMockUser(authorities = "ADMIN")
    void testImportCases_InvalidAge() throws Exception {
        // 准备包含错误年龄格式的 CSV 文件
        String csvContent = "检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室\n" +
                "EX20240401030,P100030,M,abc,2024-04-01 10:30:00,胸部,放射科";
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invalid_age.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );
        
        // 执行请求
        mockMvc.perform(multipart("/api/cases/import")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalRows").value(1))
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].row").value(2))
                .andExpect(jsonPath("$.data.errors[0].reason").value("年龄格式错误"));
    }
    
    @Test
    @WithMockUser(authorities = "ADMIN")
    void testImportCases_PartialSuccess() throws Exception {
        // 准备部分成功的 CSV 文件
        String csvContent = "检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室\n" +
                "EX20240401040,P100040,M,45,2024-04-01 10:30:00,胸部,放射科\n" +
                ",P100041,F,38,2024-04-01 11:00:00,胸部,急诊科\n" +
                "EX20240401042,P100042,M,52,2024-04-01 11:30:00,胸部,放射科";
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "partial_success.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );
        
        // 执行请求
        mockMvc.perform(multipart("/api/cases/import")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalRows").value(3))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].row").value(3))
                .andExpect(jsonPath("$.data.errors[0].reason").value("必填字段缺失：检查号"));
    }
    
    @Test
    @WithMockUser(authorities = "ADMIN")
    void testImportCases_OptionalFieldsEmpty() throws Exception {
        // 准备可选字段为空的 CSV 文件
        String csvContent = "检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室\n" +
                "EX20240401050,P100050,,,2024-04-01 10:30:00,胸部,";
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "optional_empty.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );
        
        // 执行请求
        mockMvc.perform(multipart("/api/cases/import")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalRows").value(1))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(0));
    }
}
