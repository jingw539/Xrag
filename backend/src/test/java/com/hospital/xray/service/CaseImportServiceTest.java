package com.hospital.xray.service;

import com.hospital.xray.dto.ImportResult;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.mapper.CaseInfoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 病例批量导入功能测试
 */
@SpringBootTest
@Transactional
class CaseImportServiceTest {
    
    @Autowired
    private CaseService caseService;
    
    @Autowired
    private CaseInfoMapper caseInfoMapper;
    
    @Test
    void testImportCases_Success() {
        // 准备测试数据
        String csvContent = "检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室\n" +
                "EX20240301001,P123456,M,45,2024-03-01 10:30:00,胸部,放射科\n" +
                "EX20240301002,P123457,F,38,2024-03-01 11:00:00,胸部,放射科";
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );
        
        // 执行导入
        ImportResult result = caseService.importCases(file);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(2, result.getTotalRows());
        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getFailedCount());
        assertTrue(result.getErrors().isEmpty());
    }
    
    @Test
    void testImportCases_WithErrors() {
        // 准备测试数据（包含错误）
        String csvContent = "检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室\n" +
                "EX20240301003,P123458,M,45,2024-03-01 10:30:00,胸部,放射科\n" +
                ",P123459,F,38,2024-03-01 11:00:00,胸部,放射科\n" +  // 缺少检查号
                "EX20240301005,P123460,M,abc,2024-03-01 12:00:00,胸部,放射科";  // 年龄格式错误
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );
        
        // 执行导入
        ImportResult result = caseService.importCases(file);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(3, result.getTotalRows());
        assertEquals(1, result.getSuccessCount());
        assertEquals(2, result.getFailedCount());
        assertEquals(2, result.getErrors().size());
    }
    
    @Test
    void testImportCases_DuplicateExamNo() {
        // 先导入一条数据
        String csvContent1 = "检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室\n" +
                "EX20240301006,P123461,M,45,2024-03-01 10:30:00,胸部,放射科";
        
        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "test1.csv",
                "text/csv",
                csvContent1.getBytes(StandardCharsets.UTF_8)
        );
        
        ImportResult result1 = caseService.importCases(file1);
        assertEquals(1, result1.getSuccessCount());
        
        // 再次导入相同检查号的数据
        String csvContent2 = "检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室\n" +
                "EX20240301006,P123462,F,38,2024-03-01 11:00:00,胸部,放射科";
        
        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "test2.csv",
                "text/csv",
                csvContent2.getBytes(StandardCharsets.UTF_8)
        );
        
        ImportResult result2 = caseService.importCases(file2);
        
        // 验证结果
        assertEquals(1, result2.getTotalRows());
        assertEquals(0, result2.getSuccessCount());
        assertEquals(1, result2.getFailedCount());
        assertEquals("检查号已存在", result2.getErrors().get(0).getReason());
    }
    
    @Test
    void testImportCases_EmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                new byte[0]
        );
        
        // 验证抛出异常
        assertThrows(BusinessException.class, () -> {
            caseService.importCases(file);
        });
    }
    
    @Test
    void testImportCases_InvalidFileType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "test content".getBytes(StandardCharsets.UTF_8)
        );
        
        // 验证抛出异常
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            caseService.importCases(file);
        });
        
        assertTrue(exception.getMessage().contains("仅支持 CSV 格式文件"));
    }
    
    @Test
    void testImportCases_InvalidHeader() {
        String csvContent = "错误的表头\n" +
                "EX20240301007,P123463,M,45,2024-03-01 10:30:00,胸部,放射科";
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );
        
        // 验证抛出异常
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            caseService.importCases(file);
        });
        
        assertTrue(exception.getMessage().contains("CSV 文件格式错误"));
    }
    
    @Test
    void testImportCases_MissingRequiredFields() {
        String csvContent = "检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室\n" +
                "EX20240301008,,M,45,2024-03-01 10:30:00,胸部,放射科";  // 缺少患者匿名ID
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );
        
        ImportResult result = caseService.importCases(file);
        
        // 验证结果
        assertEquals(1, result.getTotalRows());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailedCount());
        assertTrue(result.getErrors().get(0).getReason().contains("必填字段缺失"));
    }
    
    @Test
    void testImportCases_InvalidDateFormat() {
        String csvContent = "检查号,患者匿名ID,性别,年龄,检查时间,检查部位,科室\n" +
                "EX20240301009,P123464,M,45,2024/03/01,胸部,放射科";  // 日期格式错误
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );
        
        ImportResult result = caseService.importCases(file);
        
        // 验证结果
        assertEquals(1, result.getTotalRows());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailedCount());
        assertTrue(result.getErrors().get(0).getReason().contains("检查时间格式错误"));
    }
}
