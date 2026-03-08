package com.hospital.xray.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnnotationTableInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS image_annotation (
                        annotation_id  BIGINT       PRIMARY KEY,
                        image_id       BIGINT       NOT NULL,
                        report_id      BIGINT,
                        source         VARCHAR(10)  NOT NULL DEFAULT 'DOCTOR',
                        anno_type      VARCHAR(20)  NOT NULL DEFAULT 'RECTANGLE',
                        label          VARCHAR(100),
                        remark         TEXT,
                        x              DECIMAL(7,4) NOT NULL,
                        y              DECIMAL(7,4) NOT NULL,
                        width          DECIMAL(7,4) NOT NULL,
                        height         DECIMAL(7,4) NOT NULL,
                        color          VARCHAR(20),
                        confidence     DECIMAL(5,3),
                        created_by     BIGINT,
                        created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_annotation_image ON image_annotation(image_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_annotation_report ON image_annotation(report_id)");
            log.info("[AnnotationTableInitializer] image_annotation 表已就绪");
        } catch (Exception e) {
            log.error("[AnnotationTableInitializer] image_annotation 表初始化失败: {}", e.getMessage(), e);
        }
    }
}
