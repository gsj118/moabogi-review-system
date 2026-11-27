package org.mnu.mapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.annotations.Param;

/** 회원별 작품 목록 장르 설정을 저장하고 조회합니다. */
public interface HiddenGenreMapper {
    List<Map<String,Object>> choices(@Param("userId") long userId);
    Long lockMember(@Param("userId") long userId);
    int clear(@Param("userId") long userId);
    int insert(@Param("userId") long userId, @Param("genreIds") Set<Long> genreIds);
}
