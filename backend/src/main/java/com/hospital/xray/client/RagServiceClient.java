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
            List<Map<String, Object>> results = extractResultList(response.getBody());
            if (results.isEmpty()) return List.of();
            List<SimilarCaseVO> out = new ArrayList<>();
            for (Map<String, Object> row : results) {
                SimilarCaseVO vo = new SimilarCaseVO();
                Object caseId = firstNonNull(row.get("case_id"), row.get("caseId"), row.get("id"));
                if (caseId != null) vo.setCaseId(Long.parseLong(caseId.toString()));
                Object score = firstNonNull(row.get("score"), row.get("similarity_score"), row.get("similarity"));
                if (score != null) {
                    try {
                        vo.setSimilarityScore(new BigDecimal(score.toString()));
                    } catch (NumberFormatException ignored) {}
                }
                Object sourceId = firstNonNull(row.get("source_id"), row.get("sourceId"));
                if (sourceId != null) vo.setSourceId(sourceId.toString());
                Object imagePath = firstNonNull(row.get("image_path"), row.get("imagePath"), row.get("image"));
                if (imagePath != null) vo.setImagePath(imagePath.toString());
                Object findings = firstNonNull(row.get("findings"), row.get("finding"), row.get("report"));
                if (findings != null) vo.setFindings(findings.toString());
                Object impression = firstNonNull(row.get("impression"), row.get("summary"));
                if (impression != null) vo.setImpression(impression.toString());
                normalizeChineseReport(vo);
                out.add(vo);
            }
            return out;
        } catch (Exception e) {
            log.warn("RAG search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private void normalizeChineseReport(SimilarCaseVO vo) {
        if (vo == null) return;
        String text = vo.getImpression();
        if (text == null || text.isBlank()) return;
        int idxFind = text.indexOf("所见");
        int idxImp = text.indexOf("印象");
        if (idxImp >= 0) {
            String findings = "";
            String impression = "";
            if (idxFind >= 0 && idxFind < idxImp) {
                findings = text.substring(idxFind, idxImp);
            }
            impression = text.substring(idxImp);
            findings = stripLabel(findings, "所见");
            impression = stripLabel(impression, "印象");
            if (StringUtils.hasText(findings)) {
                vo.setFindings(findings.trim());
            }
            if (StringUtils.hasText(impression)) {
                vo.setImpression(impression.trim());
            }
        }
    }

    private String stripLabel(String text, String label) {
        if (text == null) return "";
        String t = text.trim();
        if (t.startsWith(label)) {
            t = t.substring(label.length()).trim();
        }
        if (t.startsWith(":") || t.startsWith("：")) {
            t = t.substring(1).trim();
        }
        return t;
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractResultList(Map<String, Object> body) {
        if (body == null) return List.of();
        Object node = firstNonNull(body.get("results"), body.get("data"), body.get("similar_cases"));
        if (node instanceof Map<?, ?> m) {
            Map<String, Object> map = (Map<String, Object>) m;
            node = firstNonNull(map.get("results"), map.get("data"), map.get("similar_cases"), map.get("items"));
        }
        if (!(node instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> row) {
                out.add((Map<String, Object>) row);
            }
        }
        return out;
    }

    private Object firstNonNull(Object... values) {
        if (values == null) return null;
        for (Object v : values) {
            if (v != null) return v;
        }
        return null;
    }
}
