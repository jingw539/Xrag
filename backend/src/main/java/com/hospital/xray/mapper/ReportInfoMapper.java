package com.hospital.xray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.xray.entity.ReportInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReportInfoMapper extends BaseMapper<ReportInfo> {

    @Select("SELECT * FROM report_info WHERE case_id = #{caseId} ORDER BY created_at DESC LIMIT 1")
    ReportInfo selectLatestByCaseId(@Param("caseId") Long caseId);
}
