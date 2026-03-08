package com.hospital.xray.service;

import com.hospital.xray.dto.CaseCreateDTO;
import com.hospital.xray.dto.CaseDetailVO;
import com.hospital.xray.dto.CaseQueryDTO;
import com.hospital.xray.dto.CaseUpdateDTO;
import com.hospital.xray.dto.CaseVO;
import com.hospital.xray.dto.ImportResult;
import com.hospital.xray.dto.PageResult;
import com.hospital.xray.dto.TypicalMarkDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 病例服务接口
 */
public interface CaseService {
    
    /**
     * 分页查询病例列表
     * 
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<CaseVO> listCases(CaseQueryDTO query);
    
    /**
     * 根据ID查询病例详情
     * 
     * @param caseId 病例ID
     * @return 病例详情
     */
    CaseDetailVO getCaseById(Long caseId);

    /**
     * 当前医生接诊/绑定病例
     *
     * @param caseId 病例ID
     * @return 更新后的病例详情
     */
    CaseDetailVO claimCase(Long caseId);
    
    /**
     * 创建病例
     * 
     * @param dto 病例创建信息
     * @return 病例ID
     */
    Long createCase(CaseCreateDTO dto);
    
    /**
     * 更新病例
     * 
     * @param caseId 病例ID
     * @param dto 病例更新信息
     */
    void updateCase(Long caseId, CaseUpdateDTO dto);
    
    /**
     * 删除病例（级联删除影像）
     * 
     * @param caseId 病例ID
     */
    void deleteCase(Long caseId);
    
    /**
     * 标记/取消典型病例
     * 
     * @param caseId 病例ID
     * @param dto 典型病例标记信息
     */
    void markTypical(Long caseId, TypicalMarkDTO dto);
    
    /**
     * 批量导入病例
     * 
     * @param file CSV 文件
     * @return 导入结果
     */
    ImportResult importCases(MultipartFile file);
}
