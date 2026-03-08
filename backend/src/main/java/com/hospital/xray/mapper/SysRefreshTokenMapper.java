package com.hospital.xray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.xray.entity.SysRefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysRefreshTokenMapper extends BaseMapper<SysRefreshToken> {

    @Select("SELECT * FROM sys_refresh_token WHERE token_hash = #{tokenHash} AND revoked = 0")
    SysRefreshToken selectActiveByHash(@Param("tokenHash") String tokenHash);

    @Update("UPDATE sys_refresh_token SET revoked = 1 WHERE user_id = #{userId} AND revoked = 0")
    int revokeAllByUserId(@Param("userId") Long userId);
}
