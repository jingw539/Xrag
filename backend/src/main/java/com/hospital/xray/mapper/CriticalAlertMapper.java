package com.hospital.xray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.xray.entity.CriticalAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CriticalAlertMapper extends BaseMapper<CriticalAlert> {

    @Select("SELECT label_type, COUNT(*) as cnt FROM critical_alert GROUP BY label_type ORDER BY cnt DESC")
    List<Map<String, Object>> selectAlertTypeStats();

    @Select("SELECT COUNT(*) FROM critical_alert WHERE alert_status = 'PENDING'")
    Long countPending();

    @Select("SELECT AVG(EXTRACT(EPOCH FROM (response_time - alert_time))) FROM critical_alert " +
            "WHERE response_time IS NOT NULL AND response_action = #{action}")
    Double avgResponseSeconds(@Param("action") String action);
}
