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
                        annotation_id       BIGINT       PRIMARY KEY,
                        image_id            BIGINT       NOT NULL,
                        report_id           BIGINT,
                        source              VARCHAR(10)  NOT NULL DEFAULT 'DOCTOR',
                        anno_type           VARCHAR(20)  NOT NULL DEFAULT 'RECTANGLE',
                        label               VARCHAR(100),
                        remark              TEXT,
                        x                   DECIMAL(7,4) NOT NULL,
                        y                   DECIMAL(7,4) NOT NULL,
                        width               DECIMAL(7,4) NOT NULL,
                        height              DECIMAL(7,4) NOT NULL,
                        measured_width_mm   DECIMAL(10,3),
                        measured_height_mm  DECIMAL(10,3),
                        compare_status      VARCHAR(20),
                        compare_note        TEXT,
                        color               VARCHAR(20),
                        confidence          DECIMAL(5,3),
                        created_by          BIGINT,
                        created_at          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            ensureColumnExists("image_annotation", "measured_width_mm", "DECIMAL(10,3)");
            ensureColumnExists("image_annotation", "measured_height_mm", "DECIMAL(10,3)");
            ensureColumnExists("image_annotation", "compare_status", "VARCHAR(20)");
            ensureColumnExists("image_annotation", "compare_note", "TEXT");
            ensureColumnExists("image_info", "pixel_spacing_x_mm", "DECIMAL(10,5)");
            ensureColumnExists("image_info", "pixel_spacing_y_mm", "DECIMAL(10,5)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_annotation_image ON image_annotation(image_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_annotation_report ON image_annotation(report_id)");
            log.info("[AnnotationTableInitializer] annotation/image metadata columns ready");
        } catch (Exception e) {
            log.error("[AnnotationTableInitializer] init failed: {}", e.getMessage(), e);
        }
    }

    private void ensureColumnExists(String tableName, String columnName, String columnDefinition) {
        Integer exists = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1)
                FROM information_schema.columns
                WHERE table_name = ?
                  AND column_name = ?
                """,
                Integer.class,
                tableName,
                columnName
        );
        if (exists == null || exists == 0) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
            log.info("[AnnotationTableInitializer] added column {}.{}", tableName, columnName);
        }
    }
}
