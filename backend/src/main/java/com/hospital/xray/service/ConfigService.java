package com.hospital.xray.service;

import com.hospital.xray.dto.ConfigUpdateDTO;
import com.hospital.xray.dto.ConfigVO;

import java.util.List;

public interface ConfigService {

    List<ConfigVO> listAll();

    ConfigVO getByKey(String key);

    void update(String key, ConfigUpdateDTO dto);
}
