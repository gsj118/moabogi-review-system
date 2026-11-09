package org.mnu.mapper;
import java.util.*;
import org.apache.ibatis.annotations.Param;
import org.mnu.domain.QueryFilter;
public interface WorkMapper {
    List<Map<String,Object>> list(@Param("q") QueryFilter query);
    long count(@Param("q") QueryFilter query);
    Map<String,Object> get(long id);
    List<Map<String,Object>> genres();
    List<Long> genreIds(long id);
    List<Map<String,Object>> duplicates(@Param("title") String title, @Param("mediaType") String type);
    int insert(Map<String,Object> work);
    int update(Map<String,Object> work);
    int delete(long id);
    int clearGenres(long id);
    int addGenre(@Param("workId") long workId,@Param("genreId") long genreId);
    int createGenre(String name);
    int renameGenre(@Param("id") long id,@Param("name") String name);
    int deleteGenre(long id);
}
