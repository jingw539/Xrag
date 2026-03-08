package com.hospital.xray.service.impl;

import com.hospital.xray.dto.DictItemVO;
import com.hospital.xray.entity.SysDictItem;
import com.hospital.xray.mapper.SysDictItemMapper;
import com.hospital.xray.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private final SysDictItemMapper sysDictItemMapper;

    @Override
    public List<DictItemVO> listItemsByCode(String dictCode) {
        return sysDictItemMapper.selectByDictCode(dictCode)
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    private DictItemVO toVO(SysDictItem item) {
        DictItemVO vo = new DictItemVO();
        vo.setItemId(item.getItemId());
        vo.setItemCode(item.getItemCode());
        vo.setItemName(item.getItemName());
        vo.setSortOrder(item.getSortOrder());
        return vo;
    }
}
