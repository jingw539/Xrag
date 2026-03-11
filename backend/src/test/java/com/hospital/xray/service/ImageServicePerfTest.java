package com.hospital.xray.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hospital.xray.entity.ImageInfo;
import com.hospital.xray.entity.RetrievalLog;
import com.hospital.xray.mapper.ImageInfoMapper;
import com.hospital.xray.mapper.RetrievalLogMapper;
import com.hospital.xray.service.impl.ImageServiceImpl;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ImageServicePerfTest {

    private static final Logger log = LoggerFactory.getLogger(ImageServicePerfTest.class);

    @Mock
    private MinioClient minioClient;

    @Mock
    private ImageInfoMapper imageInfoMapper;

    @Mock
    private RetrievalLogMapper retrievalLogMapper;

    @InjectMocks
    private ImageServiceImpl imageService;

    @Test
    public void benchmarkDeleteImagesByCaseId() throws Exception {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(imageService, "bucketName", "test-bucket");

        // Mock 100 images
        List<ImageInfo> images = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ImageInfo img = new ImageInfo();
            img.setImageId((long) i);
            img.setCaseId(1L);
            img.setFilePath("cases/1/2023/10/10/uuid" + i + ".jpg");
            images.add(img);
        }

        when(imageInfoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(images);

        AtomicInteger minioCalls = new AtomicInteger(0);
        // Simulate network delay for minioClient
        doAnswer(invocation -> {
            minioCalls.incrementAndGet();
            Thread.sleep(10); // 10ms per delete operation (which now deletes up to 1000 items at once)
            List<io.minio.Result<io.minio.messages.DeleteError>> results = new ArrayList<>();
            return results;
        }).when(minioClient).removeObjects(any(io.minio.RemoveObjectsArgs.class));

        AtomicInteger dbCalls = new AtomicInteger(0);
        // Simulate DB delay for batch clear
        when(retrievalLogMapper.update(any(), any(LambdaUpdateWrapper.class))).thenAnswer(invocation -> {
            dbCalls.incrementAndGet();
            Thread.sleep(2); // 2ms per batch update operation
            return 1;
        });

        when(imageInfoMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

        long start = System.currentTimeMillis();
        imageService.deleteImagesByCaseId(1L);
        long end = System.currentTimeMillis();

        long timeTaken = end - start;
        log.info("Time taken to delete 100 images: {}ms", timeTaken);
        log.info("Minio calls: {}", minioCalls.get());
        log.info("DB update calls: {}", dbCalls.get());

        // Check if DB batch is used
    }
}
