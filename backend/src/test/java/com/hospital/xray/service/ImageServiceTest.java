package com.hospital.xray.service;

import com.hospital.xray.dto.ImageUploadResult;
import com.hospital.xray.dto.ImageVO;
import com.hospital.xray.entity.CaseInfo;
import com.hospital.xray.entity.ImageInfo;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.mapper.CaseInfoMapper;
import com.hospital.xray.mapper.ImageInfoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 影像服务单元测试
 */
@SpringBootTest
@Transactional
class ImageServiceTest {
    
    @Autowired
    private ImageService imageService;
    
    @Autowired
    private ImageInfoMapper imageInfoMapper;
    
    @Autowired
    private CaseInfoMapper caseInfoMapper;
    
    @Test
    void testUploadImage_Success() {
        // 准备测试数据 - 创建病例
        CaseInfo caseInfo = createTestCase("EX20240301001", "P123456");
        Long caseId = caseInfo.getCaseId();
        
        // 创建模拟图像文件（1x1 PNG）
        byte[] imageContent = createTestPngImage();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test-image.png",
            "image/png",
            imageContent
        );
        
        // 执行上传（第4参数：上传用户ID，测试传 null 由异步逻辑容忍）
        ImageUploadResult result = imageService.uploadImage(file, caseId, "正位", null);
        
        // 验证结果
        assertNotNull(result);
        assertNotNull(result.getImageId());
        assertNotNull(result.getFilePath());
        assertNotNull(result.getFullUrl());
        
        // 验证数据库记录
        ImageInfo saved = imageInfoMapper.selectById(result.getImageId());
        assertNotNull(saved);
        assertEquals(caseId, saved.getCaseId());
        assertEquals("test-image.png", saved.getFileName());
        assertEquals("PNG", saved.getFileType());
        assertEquals("正位", saved.getViewPosition());
    }
    
    @Test
    void testUploadImage_FileTooLarge() {
        // 准备测试数据
        CaseInfo caseInfo = createTestCase("EX20240301001", "P123456");
        Long caseId = caseInfo.getCaseId();
        
        // 创建超大文件（模拟 51MB）
        byte[] largeContent = new byte[51 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "large-image.jpg",
            "image/jpeg",
            largeContent
        );
        
        // 验证抛出异常
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            imageService.uploadImage(file, caseId, null, null);
        });
        
        assertTrue(exception.getMessage().contains("文件过大"));
    }
    
    @Test
    void testUploadImage_InvalidFileType() {
        // 准备测试数据
        CaseInfo caseInfo = createTestCase("EX20240301001", "P123456");
        Long caseId = caseInfo.getCaseId();
        
        // 创建不支持的文件类型
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "document.pdf",
            "application/pdf",
            "test content".getBytes()
        );
        
        // 验证抛出异常
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            imageService.uploadImage(file, caseId, null, null);
        });
        
        assertTrue(exception.getMessage().contains("不支持的文件格式"));
    }
    
    @Test
    void testListImagesByCaseId_Success() {
        // 准备测试数据 - 创建病例和影像
        CaseInfo caseInfo = createTestCase("EX20240301001", "P123456");
        Long caseId = caseInfo.getCaseId();
        
        createTestImage(caseId, "image1.jpg", "正位");
        createTestImage(caseId, "image2.jpg", "侧位");
        
        // 执行查询
        List<ImageVO> images = imageService.listImagesByCaseId(caseId);
        
        // 验证结果
        assertNotNull(images);
        assertEquals(2, images.size());
        assertTrue(images.stream().anyMatch(img -> "image1.jpg".equals(img.getFileName())));
        assertTrue(images.stream().anyMatch(img -> "image2.jpg".equals(img.getFileName())));
    }
    
    @Test
    void testListImagesByCaseId_EmptyList() {
        // 准备测试数据 - 创建病例但不添加影像
        CaseInfo caseInfo = createTestCase("EX20240301001", "P123456");
        Long caseId = caseInfo.getCaseId();
        
        // 执行查询
        List<ImageVO> images = imageService.listImagesByCaseId(caseId);
        
        // 验证结果
        assertNotNull(images);
        assertTrue(images.isEmpty());
    }
    
    @Test
    void testDeleteImage_Success() {
        // 准备测试数据
        CaseInfo caseInfo = createTestCase("EX20240301001", "P123456");
        Long caseId = caseInfo.getCaseId();
        ImageInfo imageInfo = createTestImage(caseId, "test.jpg", "正位");
        Long imageId = imageInfo.getImageId();
        
        // 执行删除
        imageService.deleteImage(imageId);
        
        // 验证影像已被删除
        ImageInfo deleted = imageInfoMapper.selectById(imageId);
        assertNull(deleted);
    }
    
    @Test
    void testDeleteImage_NotFound() {
        // 验证抛出异常
        assertThrows(BusinessException.class, () -> {
            imageService.deleteImage(999999L);
        });
    }
    
    @Test
    void testDeleteImagesByCaseId_Success() {
        // 准备测试数据
        CaseInfo caseInfo = createTestCase("EX20240301001", "P123456");
        Long caseId = caseInfo.getCaseId();
        
        createTestImage(caseId, "image1.jpg", "正位");
        createTestImage(caseId, "image2.jpg", "侧位");
        createTestImage(caseId, "image3.jpg", "斜位");
        
        // 执行批量删除
        imageService.deleteImagesByCaseId(caseId);
        
        // 验证所有影像已被删除
        List<ImageVO> images = imageService.listImagesByCaseId(caseId);
        assertTrue(images.isEmpty());
    }
    
    /**
     * 创建测试病例
     */
    private CaseInfo createTestCase(String examNo, String patientAnonId) {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setExamNo(examNo);
        caseInfo.setPatientAnonId(patientAnonId);
        caseInfo.setGender("M");
        caseInfo.setAge(45);
        caseInfo.setExamTime(LocalDateTime.now());
        caseInfo.setBodyPart("胸部");
        caseInfo.setDepartment("放射科");
        caseInfo.setReportStatus("NONE");
        caseInfo.setIsTypical(0);
        caseInfo.setCreatedAt(LocalDateTime.now());
        caseInfo.setUpdatedAt(LocalDateTime.now());
        
        caseInfoMapper.insert(caseInfo);
        return caseInfo;
    }
    
    /**
     * 创建测试影像记录
     */
    private ImageInfo createTestImage(Long caseId, String fileName, String viewPosition) {
        ImageInfo imageInfo = new ImageInfo();
        imageInfo.setCaseId(caseId);
        imageInfo.setFilePath("test/path/" + fileName);
        imageInfo.setFileName(fileName);
        imageInfo.setFileType("JPG");
        imageInfo.setFileSize(1024L);
        imageInfo.setViewPosition(viewPosition);
        imageInfo.setImgWidth(512);
        imageInfo.setImgHeight(512);
        imageInfo.setCreatedAt(LocalDateTime.now());
        
        imageInfoMapper.insert(imageInfo);
        return imageInfo;
    }
    
    /**
     * 创建测试用的 PNG 图像字节数组（1x1 像素）
     */
    private byte[] createTestPngImage() {
        // 最小的有效 PNG 文件（1x1 像素，白色）
        return new byte[] {
            (byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte)0xC4,
            (byte)0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54,
            0x78, (byte)0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05,
            0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte)0xB4, 0x00, 0x00,
            0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte)0xAE, 0x42,
            0x60, (byte)0x82
        };
    }
}
