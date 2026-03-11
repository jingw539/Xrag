package com.hospital.xray.client;

import com.hospital.xray.dto.SimilarCaseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagServiceClient {

    private final RestTemplate restTemplate;

    @Value("${rag.base-url:}")
    private String ragBaseUrl;

    public boolean isEnabled() {
        return StringUtils.hasText(ragBaseUrl);
    }

    public List<SimilarCaseVO> searchSimilarCases(String query, int topK) {
        if (!isEnabled() || !StringUtils.hasText(query)) {
            return List.of();
        }
        String base = normalizeBaseUrl(ragBaseUrl);
        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("top_k", topK);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(base + "/search", entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return List.of();
            }
            Object resultsObj = response.getBody().get("results");
            if (!(resultsObj instanceof List<?> results)) {
                return List.of();
            }
            List<SimilarCaseVO> out = new ArrayList<>();
            for (Object r : results) {
                if (!(r instanceof Map<?, ?> row)) continue;
                SimilarCaseVO vo = new SimilarCaseVO();
                Object caseId = row.get("case_id");
                if (caseId != null) vo.setCaseId(Long.parseLong(caseId.toString()));
                Object score = row.get("score");
                if (score != null) {
                    try {
                        vo.setSimilarityScore(new BigDecimal(score.toString()));
                    } catch (NumberFormatException ignored) {}
                }
                Object findings = row.get("findings");
                if (findings != null) vo.setFindings(findings.toString());
                Object impression = row.get("impression");
                if (impression != null) vo.setImpression(impression.toString());
                out.add(vo);
            }
            return out;
        } catch (Exception e) {
            log.warn("RAG search failed: {}", e.getMessage());
            return List.of();
        }
    }

    public void upsertReport(Long reportId, Long caseId, String findings, String impression) {
        if (!isEnabled() || caseId == null) {
            return;
        }
        String base = normalizeBaseUrl(ragBaseUrl);
        Map<String, Object> item = new HashMap<>();
        if (reportId != null) item.put("report_id", reportId);
        item.put("case_id", caseId);
        item.put("findings", findings != null ? findings : "");
        item.put("impression", impression != null ? impression : "");

        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(item));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForEntity(base + "/index", entity, Map.class);
        } catch (Exception e) {
            log.warn("RAG index update failed: {}", e.getMessage());
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String base = baseUrl.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }
}
