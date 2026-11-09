package org.mnu.mapper;
import java.util.*;
import org.apache.ibatis.annotations.Param;
import org.mnu.domain.QueryFilter;
public interface StatsMapper {
    Map<String,Object> summary(@Param("userId") long userId,@Param("q") QueryFilter query);
    List<Map<String,Object>> months(@Param("userId") long userId,@Param("q") QueryFilter query);
    List<Map<String,Object>> genres(@Param("userId") long userId,@Param("q") QueryFilter query);
    List<Map<String,Object>> representatives(@Param("userId") long userId,@Param("q") QueryFilter query);
    List<Map<String,Object>> distribution(@Param("userId") long userId,@Param("q") QueryFilter query);
    List<Map<String,Object>> media(@Param("userId") long userId,@Param("q") QueryFilter query);
    List<Map<String,Object>> states(long userId);
}
