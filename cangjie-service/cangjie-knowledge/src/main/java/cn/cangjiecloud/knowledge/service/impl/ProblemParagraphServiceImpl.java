package cn.cangjiecloud.knowledge.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.knowledge.entity.ProblemParagraphEntity;
import cn.cangjiecloud.knowledge.mapper.ProblemParagraphMapper;
import cn.cangjiecloud.knowledge.service.IProblemParagraphService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ProblemParagraphServiceImpl
        extends ServiceImpl<ProblemParagraphMapper, ProblemParagraphEntity>
        implements IProblemParagraphService {
}
