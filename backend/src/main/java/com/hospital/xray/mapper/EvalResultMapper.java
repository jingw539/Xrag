package com.hospital.xray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.xray.entity.EvalResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface EvalResultMapper extends BaseMapper<EvalResult> {

    @Select("SELECT AVG(f1_score) as avgF1, AVG(bleu4_score) as avgBleu4, " +
            "AVG(rouge_l_score) as avgRougeL, COUNT(*) as total " +
            "FROM eval_result WHERE eval_type = #{evalType}")
    Map<String, Object> selectAvgScoresByType(@Param("evalType") String evalType);

    @Select("SELECT quality_grade, COUNT(*) as cnt FROM eval_result " +
            "WHERE eval_type = #{evalType} GROUP BY quality_grade")
    List<Map<String, Object>> selectGradeDistribution(@Param("evalType") String evalType);

    @Select("SELECT CAST(eval_time AS DATE) as eval_date, " +
            "AVG(f1_score) as avg_f1, AVG(bleu4_score) as avg_bleu4 " +
            "FROM eval_result " +
            "WHERE eval_time >= #{start} AND eval_time <= #{end} " +
            "GROUP BY CAST(eval_time AS DATE) ORDER BY CAST(eval_time AS DATE) ASC")
    List<Map<String, Object>> selectEvalTrend(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    @Select("SELECT TO_CHAR(eval_time, 'YYYY-MM') as eval_month, " +
            "AVG(f1_score) as avg_f1, AVG(bleu4_score) as avg_bleu4 " +
            "FROM eval_result " +
            "WHERE eval_time >= #{start} AND eval_time <= #{end} " +
            "GROUP BY TO_CHAR(eval_time, 'YYYY-MM') ORDER BY eval_month ASC")
    List<Map<String, Object>> selectEvalTrendByMonth(@Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end);

    @Select("SELECT TO_CHAR(eval_time, 'IYYY-IW') as eval_week, " +
            "AVG(f1_score) as avg_f1, AVG(bleu4_score) as avg_bleu4 " +
            "FROM eval_result " +
            "WHERE eval_time >= #{start} AND eval_time <= #{end} " +
            "GROUP BY TO_CHAR(eval_time, 'IYYY-IW') ORDER BY eval_week ASC")
    List<Map<String, Object>> selectEvalTrendByWeek(@Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end);

    @Select("SELECT AVG(f1_score) as adoptionAvgF1, " +
            "COUNT(CASE WHEN quality_grade IN ('A','B') THEN 1 END) as adoptedCount, " +
            "COUNT(*) as totalEvals FROM eval_result WHERE eval_type = #{evalType}")
    Map<String, Object> selectAdoptionStats(@Param("evalType") String evalType);

    @Select("SELECT e.eval_type as model_version, " +
            "AVG(e.precision_score) as avg_precision, " +
            "AVG(e.recall_score) as avg_recall, " +
            "AVG(e.f1_score) as avg_f1, " +
            "AVG(e.bleu4_score) as avg_bleu4, " +
            "COUNT(*) as eval_count, " +
            "MAX(e.eval_time) as last_eval_time " +
            "FROM eval_result e " +
            "GROUP BY e.eval_type " +
            "ORDER BY MAX(e.eval_time) DESC")
    List<Map<String, Object>> selectModelVersionComparison();

    @Select("SELECT AVG(EXTRACT(EPOCH FROM (r.sign_time - r.ai_generate_time)) * 1000) as avgGenMs " +
            "FROM report_info r " +
            "WHERE r.report_status = 'SIGNED' " +
            "AND r.sign_time IS NOT NULL AND r.ai_generate_time IS NOT NULL")
    Map<String, Object> selectAvgGenTimeMs();
}
