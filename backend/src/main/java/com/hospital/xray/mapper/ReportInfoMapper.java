package com.hospital.xray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.xray.entity.ReportInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReportInfoMapper extends BaseMapper<ReportInfo> {

    @Select("SELECT * FROM report_info WHERE case_id = #{caseId} ORDER BY created_at DESC LIMIT 1")
    ReportInfo selectLatestByCaseId(@Param("caseId") Long caseId);

    @Select({
            "<script>",
            "SELECT r.* FROM report_info r",
            "JOIN (",
            "  SELECT case_id, MAX(created_at) AS max_created",
            "  FROM report_info",
            "  WHERE case_id IN",
            "  <foreach item='id' collection='caseIds' open='(' separator=',' close=')'>#{id}</foreach>",
            "  GROUP BY case_id",
            ") m ON r.case_id = m.case_id AND r.created_at = m.max_created",
            "</script>"
    })
    List<ReportInfo> selectLatestByCaseIds(@Param("caseIds") List<Long> caseIds);
}
