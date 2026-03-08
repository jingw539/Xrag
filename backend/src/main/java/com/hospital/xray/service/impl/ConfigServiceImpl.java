package com.hospital.xray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.xray.dto.ConfigUpdateDTO;
import com.hospital.xray.dto.ConfigVO;
import com.hospital.xray.entity.SysConfig;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.mapper.SysConfigMapper;
import com.hospital.xray.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    private final SysConfigMapper sysConfigMapper;

    @Override
    public List<ConfigVO> listAll() {
        return sysConfigMapper.selectList(null)
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public ConfigVO getByKey(String key) {
        SysConfig config = sysConfigMapper.selectByKey(key);
        if (config == null) {
            throw new BusinessException(404, "配置项不存在: " + key);
        }
        return toVO(config);
    }

    @Override
    @Transactional
    public void update(String key, ConfigUpdateDTO dto) {
        SysConfig config = sysConfigMapper.selectByKey(key);
        if (config == null) {
            throw new BusinessException(404, "配置项不存在: " + key);
        }
        config.setConfigValue(dto.getConfigValue());
        config.setUpdatedAt(LocalDateTime.now());
        sysConfigMapper.updateById(config);
    }

    private ConfigVO toVO(SysConfig c) {
        ConfigVO vo = new ConfigVO();
        vo.setConfigId(c.getConfigId());
        vo.setConfigKey(c.getConfigKey());
        vo.setConfigValue(c.getConfigValue());
        vo.setDescription(c.getDescription());
        vo.setUpdatedAt(c.getUpdatedAt());
        return vo;
    }
}
