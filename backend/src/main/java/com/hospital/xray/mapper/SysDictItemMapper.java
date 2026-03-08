package com.hospital.xray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.xray.entity.SysDictItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysDictItemMapper extends BaseMapper<SysDictItem> {

    @Select("SELECT i.* FROM sys_dict_item i " +
            "JOIN sys_dict d ON i.dict_id = d.dict_id " +
            "WHERE d.dict_code = #{dictCode} ORDER BY i.sort_order")
    List<SysDictItem> selectByDictCode(@Param("dictCode") String dictCode);
}
