package org.mnu.mapper;
import java.util.*;
import org.apache.ibatis.annotations.Param;
import org.mnu.domain.QueryFilter;
/**
 * @brief 회원별 감상 활동과 장르별 평가 집계를 매핑합니다.
 * @details StatsMapper.xml은 회원의 전체 이력 순번을 먼저 정한 뒤 기간과 작품 조건을 적용합니다.
 * 장르 대표 평점은 선택 범위 안의 작품별 최신 유효 평점이며, NULL은 미평점입니다.
 */
public interface StatsMapper {
    /**
     * @brief 조회 범위의 감상 건수, 고유 작품 수, 재감상과 미평점, 평균을 집계합니다.
     * @details 재감상 여부는 전체 이력의 순번으로 판단하고 평균은 범위 내 유효 평점 기록의 평균입니다.
     * @param userId 인증된 회원 식별자
     * @param query 검증된 통계 조회 조건
     * @return 활동 요약; 유효 평점이 없으면 averageRating은 null
     */
    Map<String,Object> summary(@Param("userId") long userId,@Param("q") QueryFilter query);
    /**
     * @brief 조회 범위에서 감상이 발생한 월별 기록 수와 고유 작품 수를 집계합니다.
     * @param userId 인증된 회원 식별자
     * @param query 검증된 통계 조회 조건
     * @return 월 오름차순의 집계; 기록이 없는 월을 별도로 생성하지 않습니다.
     */
    List<Map<String,Object>> months(@Param("userId") long userId,@Param("q") QueryFilter query);
    /**
     * @brief 장르별 감상 건수와 작품별 대표 평점의 평균 및 표본 수를 조회합니다.
     * @details 복수 장르 작품은 각 장르에 반영됩니다. 표본은 선택 범위의 작품별 최신 유효 평점 수이며,
     * 표본이 없는 장르는 0건과 NULL 평균으로 반환합니다.
     * @param userId 인증된 회원 식별자
     * @param query 검증된 통계 조회 조건
     * @return 모든 등록 장르의 감상 건수, sampleSize와 averageRating
     */
    List<Map<String,Object>> genres(@Param("userId") long userId,@Param("q") QueryFilter query);
    /**
     * @brief 선택 범위 안에서 작품별 최신 유효 평점 기록을 한 건씩 조회합니다.
     * @param userId 인증된 회원 식별자
     * @param query 검증된 통계 조회 조건
     * @return 대표 기록 ID, 작품 ID와 제목, 감상일 및 평점; 범위 밖 평점은 포함하지 않습니다.
     */
    List<Map<String,Object>> representatives(@Param("userId") long userId,@Param("q") QueryFilter query);
    /**
     * @brief 조회 범위의 NULL이 아닌 평점별 감상 기록 수를 집계합니다.
     * @param userId 인증된 회원 식별자
     * @param query 검증된 통계 조회 조건
     * @return 평점 오름차순의 기록 수; 미평점은 분포에서 제외됩니다.
     */
    List<Map<String,Object>> distribution(@Param("userId") long userId,@Param("q") QueryFilter query);
    /**
     * @brief 조회 범위의 영화와 TV별 감상 기록 수 및 고유 작품 수를 집계합니다.
     * @param userId 인증된 회원 식별자
     * @param query 검증된 통계 조회 조건
     * @return 작품 유형별 recordCount와 workCount
     */
    List<Map<String,Object>> media(@Param("userId") long userId,@Param("q") QueryFilter query);
    /**
     * @brief 기간과 검색 조건 없이 회원의 전체 현재 상태별 작품 수를 집계합니다.
     * @param userId 인증된 회원 식별자
     * @return 현재 저장된 상태 코드와 workCount; 상태 행이 없는 작품은 포함하지 않습니다.
     */
    List<Map<String,Object>> states(long userId);
}
