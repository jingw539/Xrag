package com.hospital.xray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.xray.entity.ImageInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 影像信息 Mapper 接口
 * 继承 MyBatis-Plus 的 BaseMapper，提供基础 CRUD 操作
 */
@Mapper
public interface ImageInfoMapper extends BaseMapper<ImageInfo> {
    // BaseMapper 已提供以下方法：
    // - insert(T entity): 插入一条记录
    // - deleteById(Serializable id): 根据 ID 删除
    // - updateById(T entity): 根据 ID 更新
    // - selectById(Serializable id): 根据 ID 查询
    // - selectList(Wrapper<T> queryWrapper): 根据条件查询列表
    // - selectPage(Page<T> page, Wrapper<T> queryWrapper): 分页查询
    // 等等...
    
    // 如需自定义 SQL，可在此添加方法并在对应的 XML 文件中实现
}
