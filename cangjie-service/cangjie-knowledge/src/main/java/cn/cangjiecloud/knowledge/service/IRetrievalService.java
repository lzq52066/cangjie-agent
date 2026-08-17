package cn.cangjiecloud.knowledge.service;

import cn.cangjiecloud.knowledge.api.dto.RetrievalResultDTO;

import java.util.List;

public interface IRetrievalService {

    /**
     * 混合检索
     */
    List<RetrievalResultDTO> retrieve(cn.cangjiecloud.knowledge.api.dto.RetrievalQueryDTO query);
}
