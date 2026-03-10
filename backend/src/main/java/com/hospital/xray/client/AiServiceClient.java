package com.hospital.xray.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.xray.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServiceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.qwen.api-key}")
    private String qwenApiKey;

    @Value("${ai.qwen.base-url}")
    private String qwenBaseUrl;

    @Value("${ai.qwen.model:qwen-vl-plus}")
    private String qwenModel;

    @Value("${ai.qwen.timeout:120000}")
    private int qwenTimeout;

    @Value("${ai.deepseek.api-key}")
    private String deepseekApiKey;

    @Value("${ai.deepseek.base-url}")
    private String deepseekBaseUrl;

    @Value("${ai.deepseek.timeout:60000}")
    private int deepseekTimeout;

    @Value("${minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    @Value("${minio.bucket-name:cxr-images}")
    private String minioBucket;

    /**
     * 调用 Qwen-VL-Plus 生成胸部X光报告
     * @param imagePath MinIO 中的文件路径（或完整 URL）
     * @param similarCases 相似病例列表（RAG 上下文）
     */
    public Map<String, Object> generateReport(String imagePath, List<Map<String, Object>> similarCases) {
        String imageUrl = buildImageUrl(imagePath);

        StringBuilder sb = new StringBuilder();
        sb.append("You are a senior thoracic radiologist. Analyze this chest X-ray and generate a clinically usable, standardized radiology report.\n");
        sb.append("Do not produce vague text. The report must be specific, structured, cautious, and suitable for clinical review.\n");
        sb.append("In findings, prioritize these structures when visible: lung fields, lung markings, hilar regions, trachea, mediastinum, cardiac silhouette, aortic arch, diaphragms, costophrenic angles, pleura, and visible thoracic bony structures.\n");
        sb.append("If the study is essentially normal, still describe key normal structures instead of writing only a generic normal statement.\n");
        sb.append("If an abnormality is present, describe side, location, extent, density or lucency change, morphology, and associated findings.\n");
        sb.append("If cardiac size can be reasonably estimated, you may mention an approximate cardiothoracic ratio; if not reliable, do not fabricate a number.\n");
        sb.append("Do not invent lesions that are not supported by the image. Use cautious wording when certainty is limited.\n");
        sb.append("The findings should be one coherent paragraph in Chinese medical style. The impression should be 1-3 concise Chinese clinical conclusions.\n");

        if (similarCases != null && !similarCases.isEmpty()) {
            sb.append("\nReference reports from similar cases are provided below for style and structure only. Do not copy them. The current image always takes priority.\n");
            for (int i = 0; i < similarCases.size(); i++) {
                Map<String, Object> c = similarCases.get(i);
                sb.append("\nReference case ").append(i + 1).append(":\n");
                if (c.get("findings") != null) sb.append("Findings: ").append(c.get("findings")).append("\n");
                if (c.get("impression") != null) sb.append("Impression: ").append(c.get("impression")).append("\n");
            }
        }

        sb.append("\n\nOutput rules:\n1. Output JSON only. No markdown, no commentary, no extra text.\n2. findings must be a standardized Chinese chest X-ray findings paragraph with sufficient anatomic detail.\n3. impression must be a standardized Chinese chest X-ray impression, concise and clinically usable.\n4. Avoid overly generic findings such as only saying 'cardiopulmonary silhouette is unremarkable' without elaboration.\n5. When normal, explicitly mention as many key normal structures as reasonably visible.\n6. When abnormal, describe the abnormality in a clinically meaningful way.\n\nJSON format:\n{\n  \"findings\": \"Standardized detailed Chinese findings\",\n  \"impression\": \"Standardized concise Chinese impression\",\n  \"confidence\": 0.85\n}\n");

        return callQwenVL(sb.toString(), imageUrl);
    }

    /**
     * 调用 DeepSeek 模拟 CheXbert 评测（14类病理标签提取 + 评分）
     */
    public Map<String, Object> evaluateWithChexbert(String reportText) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("你是医学NLP专家，请从以下胸部X光报告中提取 CheXpert 14 类标签，并估算评测指标。\n\n");
        promptBuilder.append("报告文本：\n");
        promptBuilder.append(reportText).append("\n\n");
        promptBuilder.append("CheXpert 14类标签（若报告明确提及则列入 predicted_labels）：\n");
        promptBuilder.append("Atelectasis, Cardiomegaly, Consolidation, Edema, Enlarged Cardiomediastinum,\n");
        promptBuilder.append("Fracture, Lung Lesion, Lung Opacity, No Finding, Pleural Effusion,\n");
        promptBuilder.append("Pleural Other, Pneumonia, Pneumothorax, Support Devices\n\n");
        promptBuilder.append("请严格按以下 JSON 格式输出，不要包含任何其他文字：\n");
        promptBuilder.append("{\n");
        promptBuilder.append("  \"predicted_labels\": [\"标签1\", \"标签2\"],\n");
        promptBuilder.append("  \"label_probabilities\": {\n");
        promptBuilder.append("    \"Atelectasis\": 0.0, \"Cardiomegaly\": 0.0, \"Consolidation\": 0.0,\n");
        promptBuilder.append("    \"Edema\": 0.0, \"Enlarged Cardiomediastinum\": 0.0, \"Fracture\": 0.0,\n");
        promptBuilder.append("    \"Lung Lesion\": 0.0, \"Lung Opacity\": 0.0, \"No Finding\": 0.0,\n");
        promptBuilder.append("    \"Pleural Effusion\": 0.0, \"Pleural Other\": 0.0, \"Pneumonia\": 0.0,\n");
        promptBuilder.append("    \"Pneumothorax\": 0.0, \"Support Devices\": 0.0\n");
        promptBuilder.append("  },\n");
        promptBuilder.append("  \"f1_score\": 0.0,\n");
        promptBuilder.append("  \"precision\": 0.0,\n");
        promptBuilder.append("  \"recall\": 0.0,\n");
        promptBuilder.append("  \"bleu4\": 0.0,\n");
        promptBuilder.append("  \"rouge_l\": 0.0,\n");
        promptBuilder.append("  \"missing_labels\": [],\n");
        promptBuilder.append("  \"extra_labels\": []\n");
        promptBuilder.append("}\n");
        String prompt = promptBuilder.toString();

        return callDeepSeek(prompt);
    }

    /**
     * 调用 DeepSeek 对医生草稿进行 AI 润色
     */
    public Map<String, Object> polishReport(String findings, String impression) {
        StringBuilder polishBuilder = new StringBuilder();
        polishBuilder.append("你是资深放射科主任医师，请对以下胸部X光报告草稿进行专业润色，使其更加规范、完整、准确。\n\n");
        polishBuilder.append("当前草稿：\n");
        polishBuilder.append("影像所见：").append(findings != null ? findings : "").append("\n");
        polishBuilder.append("影像印象：").append(impression != null ? impression : "").append("\n\n");
        polishBuilder.append("润色要求：\n");
        polishBuilder.append("1. 使用标准化的放射学术语（ACR标准）\n");
        polishBuilder.append("2. 按照\"肺野→纵隔→心影→膈肌→骨骼→软组织\"的顺序组织描述\n");
        polishBuilder.append("3. 量化描述（如心胸比、病灶大小范围）\n");
        polishBuilder.append("4. 印象部分简明扼要，列出主要诊断及鉴别\n");
        polishBuilder.append("5. 如果发现草稿中的明显错误或遗漏，在 suggestions 中指出\n\n");
        polishBuilder.append("请严格按以下 JSON 格式输出：\n");
        polishBuilder.append("{\n");
        polishBuilder.append("  \"polished_findings\": \"润色后的影像所见\",\n");
        polishBuilder.append("  \"polished_impression\": \"润色后的影像印象\",\n");
        polishBuilder.append("  \"changes_summary\": \"修改要点（2-3句话总结做了哪些改进）\",\n");
        polishBuilder.append("  \"suggestions\": [\"建议1\", \"建议2\"]\n");
        polishBuilder.append("}\n");
        String prompt = polishBuilder.toString();
        return callDeepSeek(prompt);
    }

    /**
     * 调用 DeepSeek 对已签发低质量报告生成审核建议
     */
    public Map<String, Object> getReviewAdvice(String findings, String impression,
                                               String qualityGrade, Double f1Score,
                                               List<String> missingLabels, List<String> extraLabels) {
        StringBuilder reviewBuilder = new StringBuilder();
        reviewBuilder.append("你是一位资深放射科主任医师兼质控专家，请对以下已签发的胸部X光报告进行审核，给出具体可操作的修改建议。\n\n");
        reviewBuilder.append("当前报告：\n");
        reviewBuilder.append("影像所见：").append(findings != null ? findings : "").append("\n");
        reviewBuilder.append("影像印象：").append(impression != null ? impression : "").append("\n\n");
        reviewBuilder.append("AI质量评测结果：\n");
        reviewBuilder.append("- 综合质量等级：").append(qualityGrade != null ? qualityGrade : "N/A").append("\n");
        reviewBuilder.append("- F1分数：").append(f1Score != null ? String.format("%.1f%%", f1Score * 100) : "0.0%").append("\n");
        reviewBuilder.append("- AI漏诊风险标签：").append(missingLabels != null && !missingLabels.isEmpty() ? String.join(", ", missingLabels) : "无").append("\n");
        reviewBuilder.append("- AI过度诊断标签：").append(extraLabels != null && !extraLabels.isEmpty() ? String.join(", ", extraLabels) : "无").append("\n\n");
        reviewBuilder.append("请给出针对性的审核建议，帮助医生修改报告，严格按以下 JSON 格式输出：\n");
        reviewBuilder.append("{\n");
        reviewBuilder.append("  \"overall_assessment\": \"总体评价（1-2句话）\",\n");
        reviewBuilder.append("  \"key_issues\": [\"主要问题1\", \"主要问题2\"],\n");
        reviewBuilder.append("  \"check_points\": [\"建议重点核查异常区域\", \"建议补充关键影像描述\"],\n");
        reviewBuilder.append("  \"suggested_findings\": \"建议修改后的影像所见参考文本\",\n");
        reviewBuilder.append("  \"suggested_impression\": \"建议修改后的影像印象参考文本\",\n");
        reviewBuilder.append("  \"priority\": \"high/medium/low\"\n");
        reviewBuilder.append("}\n");
        String prompt = reviewBuilder.toString();
        return callDeepSeek(prompt);
    }

    /**
     * 调用 DeepSeek 进行术语规范化分析
     */
    public Map<String, Object> analyzeTerms(String reportText) {
        StringBuilder termBuilder = new StringBuilder();
        termBuilder.append("你是医学术语标准化专家，请分析以下胸部X光报告，找出不规范、口语化或过时的术语，并提供标准化建议。\n\n");
        termBuilder.append("报告文本：\n");
        termBuilder.append(reportText != null ? reportText : "").append("\n\n");
        termBuilder.append("请严格按以下 JSON 格式输出，不要包含任何其他文字：\n");
        termBuilder.append("{\n");
        termBuilder.append("  \"corrections\": [\n");
        termBuilder.append("    {\n");
        termBuilder.append("      \"original_term\": \"原始术语\",\n");
        termBuilder.append("      \"suggested_term\": \"标准术语\",\n");
        termBuilder.append("      \"context\": \"该术语在报告中的完整句子\"\n");
        termBuilder.append("    }\n");
        termBuilder.append("  ]\n");
        termBuilder.append("}\n");
        termBuilder.append("若无需修正则返回 {\"corrections\": []}\n");
        String prompt = termBuilder.toString();

        return callDeepSeek(prompt);
    }

    private String buildImageUrl(String imagePath) {
        if (imagePath == null || imagePath.startsWith("http") || imagePath.startsWith("data:")) return imagePath;
        return minioEndpoint + "/" + minioBucket + "/" + imagePath;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callQwenVL(String prompt, String imageUrl) {
        Map<String, Object> textContent = Map.of("type", "text", "text", prompt);
        List<Map<String, Object>> contentList;
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Map<String, Object> imageContent = Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", imageUrl)
            );
            contentList = List.of(imageContent, textContent);
        } else {
            contentList = List.of(textContent);
        }
        Map<String, Object> message = Map.of(
                "role", "user",
                "content", contentList
        );
        Map<String, Object> body = Map.of(
                "model", qwenModel,
                "messages", List.of(message)
        );

        String content = callOpenAICompatible(qwenBaseUrl, qwenApiKey, body, "Qwen-VL");
        return parseJson(content, Map.of(
                "findings", "AI服务暂时不可用，请手动填写",
                "impression", "",
                "confidence", 0.0
        ));
    }

    private void checkDeepSeekConfigured() {
        if (!org.springframework.util.StringUtils.hasText(deepseekApiKey)
                || deepseekApiKey.startsWith("YOUR_")
                || deepseekApiKey.equals("sk-placeholder")) {
            throw new BusinessException(503, "DeepSeek API密钥未配置，请在环境变量 DEEPSEEK_API_KEY 中设置真实密钥");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callDeepSeek(String prompt) {
        checkDeepSeekConfigured();
        Map<String, Object> message = Map.<String, Object>of("role", "user", "content", prompt);
        Map<String, Object> body = new HashMap<>();
        body.put("model", "deepseek-chat");
        body.put("messages", List.of(message));
        body.put("response_format", Map.of("type", "json_object"));

        String content = callOpenAICompatible(deepseekBaseUrl, deepseekApiKey, body, "DeepSeek");
        return parseJson(content, new HashMap<>());
    }

    @SuppressWarnings("unchecked")
    private String callOpenAICompatible(String baseUrl, String apiKey, Map<String, Object> body, String provider) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            log.info("{} API 调用开始，预计耗时较长，请耐心等待...", provider);
            long startTime = System.currentTimeMillis();
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/chat/completions", entity, Map.class);
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("{} API 调用完成，耗时: {}ms", provider, elapsed);
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new BusinessException(500, provider + " API 返回异常: " + response.getStatusCode());
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new BusinessException(500, provider + " API 返回空结果");
            }
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            return (String) msg.get("content");
        } catch (ResourceAccessException e) {
            log.error("{} API 调用失败 [{}]: {}", provider, baseUrl, e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                throw new BusinessException(504, provider + " 服务响应超时，请稍后重试或检查网络连接");
            }
            throw new BusinessException(503, provider + " 服务暂时不可用，请稍后重试");
        } catch (Exception e) {
            log.error("{} API 调用异常", provider, e);
            throw new BusinessException(500, provider + " 调用失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String content, Map<String, Object> fallback) {
        if (content == null || content.isBlank()) return fallback;
        try {
            String cleaned = content.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
            }
            return objectMapper.readValue(cleaned, Map.class);
        } catch (Exception e) {
            log.warn("解析 AI 响应 JSON 失败，原文: {}", content);
            return fallback;
        }
    }
}
