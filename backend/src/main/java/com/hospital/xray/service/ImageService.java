package com.hospital.xray.service;

import com.hospital.xray.dto.ImageMetadataUpdateDTO;
import com.hospital.xray.dto.ImageUploadResult;
import com.hospital.xray.dto.ImageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ImageService {
    ImageUploadResult uploadImage(MultipartFile file, Long caseId, String viewPosition, Long uploadUserId,
                                  Double pixelSpacingXmm, Double pixelSpacingYmm);
    List<ImageVO> listImagesByCaseId(Long caseId);
    List<ImageVO> listPriorImages(Long caseId, Long currentImageId);
    ImageVO updateImageMetadata(Long imageId, ImageMetadataUpdateDTO dto);
    void deleteImage(Long imageId);
    void deleteImagesByCaseId(Long caseId);
    void generateThumbnail(String originalPath, String thumbnailPath);
    String getImageAsDataUrl(Long imageId);
    byte[] getImageContent(Long imageId, boolean thumbnail);
    String getImageContentType(Long imageId, boolean thumbnail);
}
