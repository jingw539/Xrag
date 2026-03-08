package com.hospital.xray.service;

import com.hospital.xray.dto.DictItemVO;

import java.util.List;

public interface DictService {

    List<DictItemVO> listItemsByCode(String dictCode);
}
