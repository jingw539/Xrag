package com.hospital.xray.controller;

import com.hospital.xray.dto.AnnotationVO;
import com.hospital.xray.service.AnnotationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(com.hospital.xray.config.SecurityConfig.class)
class AnnotationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnnotationService annotationService;

    @Test
    @WithMockUser(authorities = "DOCTOR")
    void listByImage_WithDoctorRole_Success() throws Exception {
        AnnotationVO annotation = new AnnotationVO();
        annotation.setAnnotationId(1L);
        annotation.setImageId(1L);

        when(annotationService.listByImage(1L)).thenReturn(List.of(annotation));

        mockMvc.perform(get("/api/annotations/image/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].annotationId").value(1L))
                .andExpect(jsonPath("$.data[0].imageId").value(1L));
    }

    @Test
    @WithMockUser(authorities = "QC")
    void listByImage_WithQCRole_Success() throws Exception {
        when(annotationService.listByImage(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/annotations/image/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void listByImage_WithAdminRole_Success() throws Exception {
        when(annotationService.listByImage(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/annotations/image/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @WithMockUser(authorities = "USER")
    void listByImage_WithUserRole_Forbidden() throws Exception {
        mockMvc.perform(get("/api/annotations/image/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void listByImage_WithoutAuth_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/annotations/image/1"))
                .andExpect(status().isUnauthorized());
    }
}
