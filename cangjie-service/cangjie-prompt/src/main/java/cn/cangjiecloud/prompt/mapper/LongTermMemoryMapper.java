package cn.cangjiecloud.prompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface LongTermMemoryMapper extends BaseMapper<LongTermMemoryEntity> {

    @Select("SELECT * FROM long_term_memory WHERE user_id = #{userId} AND application_id = #{applicationId} "
            + "AND dimension = #{dimension} AND is_active = true AND deleted = 0")
    List<LongTermMemoryEntity> selectActiveMemories(@Param("userId") String userId,
                                                    @Param("applicationId") String applicationId,
                                                    @Param("dimension") String dimension);

    @Select("SELECT * FROM long_term_memory WHERE user_id = #{userId} AND application_id = #{applicationId} "
            + "AND is_active = true AND deleted = 0 ORDER BY dimension")
    List<LongTermMemoryEntity> selectAllActiveMemories(@Param("userId") String userId,
                                                       @Param("applicationId") String applicationId);

    @Update("UPDATE long_term_memory SET trigger_count = trigger_count + 1, "
            + "last_triggered_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int incrementTriggerCount(@Param("id") String id);

    @Update("UPDATE long_term_memory SET is_active = false, update_by = #{updateBy}, "
            + "update_time = CURRENT_TIMESTAMP WHERE id = #{id}")
    int deactivate(@Param("id") String id, @Param("updateBy") String updateBy);
}