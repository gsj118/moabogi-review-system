package org.mnu.mapper;
import java.util.*;
import org.apache.ibatis.annotations.Param;
import org.mnu.domain.QueryFilter;
public interface RecordMapper {
    List<Map<String,Object>> list(@Param("userId") long userId,@Param("q") QueryFilter query);
    long count(@Param("userId") long userId,@Param("q") QueryFilter query);
    Map<String,Object> get(@Param("userId") long userId,@Param("id") long id);
    List<Map<String,Object>> workSummaries(@Param("userId") long userId,@Param("workIds") List<Long> workIds);
    int insert(Map<String,Object> record);
    int update(Map<String,Object> record);
    int delete(@Param("userId") long userId,@Param("id") long id);
    int reserve(@Param("userId") long userId,@Param("token") String token,@Param("hash") String hash);
    Map<String,Object> submission(@Param("userId") long userId,@Param("token") String token);
    int finish(@Param("userId") long userId,@Param("token") String token,@Param("recordId") long recordId);
    int setState(@Param("userId") long userId,@Param("workId") long workId,@Param("status") String status);
    int clearState(@Param("userId") long userId,@Param("workId") long workId);
    String state(@Param("userId") long userId,@Param("workId") long workId);
    List<Map<String,Object>> states(@Param("userId") long userId,@Param("status") String status);
}
