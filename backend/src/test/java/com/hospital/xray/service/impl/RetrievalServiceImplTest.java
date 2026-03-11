package com.hospital.xray.service.impl;

import com.hospital.xray.entity.ImageInfo;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.mapper.CaseInfoMapper;
import com.hospital.xray.mapper.ImageInfoMapper;
import com.hospital.xray.mapper.ReportInfoMapper;
import com.hospital.xray.mapper.RetrievalLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class RetrievalServiceImplTest {

    @Mock
    private RetrievalLogMapper retrievalLogMapper;
    @Mock
    private ImageInfoMapper imageInfoMapper;
    @Mock
    private CaseInfoMapper caseInfoMapper;
    @Mock
    private ReportInfoMapper reportInfoMapper;

    @InjectMocks
    private RetrievalServiceImpl retrievalService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void search_ImageNotFound_ThrowsException() {
        // Arrange
        Long caseId = 1L;
        Long imageId = 999L;
        Integer topK = 5;

        when(imageInfoMapper.selectById(imageId)).thenReturn(null);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            retrievalService.search(caseId, imageId, topK);
        });

        assertEquals(404, exception.getCode());
        assertEquals("影像不存在", exception.getMessage());
    }
}
