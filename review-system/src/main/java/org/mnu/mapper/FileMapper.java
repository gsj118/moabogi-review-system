package org.mnu.mapper;

import java.util.List;
import java.util.Map;
import org.mnu.domain.FileAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FileMapper {
    void insert(FileAttachment fa);
    int delete(@Param("id") long id, @Param("userId") long userId);
    FileAttachment selectById(long id);
    int countByPost(long postId);
    int countByPostAndUser(@Param("postId") long postId, @Param("userId") long userId);
    List<Map<String,Object>> selectByPost(long postId);
}
