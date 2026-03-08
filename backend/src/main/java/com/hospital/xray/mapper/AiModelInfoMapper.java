package com.hospital.xray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.xray.entity.AiModelInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiModelInfoMapper extends BaseMapper<AiModelInfo> {

    @Select("SELECT * FROM ai_model_info WHERE model_type = #{type} AND is_active = 1 ORDER BY created_at DESC LIMIT 1")
    AiModelInfo selectActiveByType(@Param("type") String type);
}
