package com.hospital.xray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.xray.entity.EvaluationMetric;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface EvaluationMetricMapper extends BaseMapper<EvaluationMetric> {

    @Select({
            "<script>",
            "SELECT scope, tag_name, metric_name, metric_value, support",
            "FROM evaluation_metric",
            "WHERE run_id = #{runId}",
            "<if test="scope != null and scope != ''">",
            "  AND scope = #{scope}",
            "</if>",
            "ORDER BY scope, tag_name, metric_name",
            "</script>"
    })
    List<Map<String, Object>> selectMetricsByRun(@Param("runId") Long runId, @Param("scope") String scope);

    @Select({
            "<script>",
            "SELECT r.model_name AS modelName, r.dataset_name AS datasetName,",
            "       m.metric_name AS metricName,",
            "       COALESCE(m.tag_name, 'ALL') AS tagName,",
            "       m.metric_value AS metricValue,",
            "       m.support AS support",
            "FROM evaluation_metric m",
            "JOIN evaluation_run r ON m.run_id = r.run_id",
            "WHERE m.metric_name = #{metricName}",
            "<if test="tagName != null and tagName != ''">",
            "  AND m.tag_name = #{tagName}",
            "</if>",
            "<if test="datasetName != null and datasetName != ''">",
            "  AND r.dataset_name = #{datasetName}",
            "</if>",
            "ORDER BY r.model_name",
            "</script>"
    })
    List<Map<String, Object>> selectModelCompare(@Param("datasetName") String datasetName,
                                                 @Param("metricName") String metricName,
                                                 @Param("tagName") String tagName);
}
