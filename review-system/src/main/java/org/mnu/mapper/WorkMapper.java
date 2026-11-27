package org.mnu.mapper;
import java.util.*;
import org.apache.ibatis.annotations.Param;
import org.mnu.domain.QueryFilter;
/**
 * @brief 공용 작품과 장르, 작품과 장르 연결 SQL을 매핑합니다.
 * @details 실제 SQL은 WorkMapper.xml에 정의됩니다. 관리자 권한과 입력 검증은 WorkService에서 수행합니다.
 */
public interface WorkMapper {
    /**
     * @brief 제목, 유형과 장르 조건에 맞는 작품 한 페이지를 조회합니다.
     * @param query 검증과 페이지 보정이 끝난 조회 조건
     * @return 등록 시각과 ID 내림차순의 공용 작품 목록
     */
    List<Map<String,Object>> list(@Param("userId") long userId, @Param("q") QueryFilter query);
    /**
     * @brief 목록과 같은 작품 조건의 전체 건수를 조회합니다.
     * @param query 제목, 유형과 장르 조회 조건
     * @return 조건에 맞는 작품 수
     */
    long count(@Param("userId") long userId, @Param("q") QueryFilter query);
    /**
     * @brief 작품 식별자로 기본 정보와 장르 이름을 조회합니다.
     * @param id 공용 작품 식별자
     * @return 작품 정보; 작품이 없으면 null
     */
    Map<String,Object> get(long id);
    /**
     * @brief 공용 장르를 이름과 ID 순서로 조회합니다.
     * @return 장르 ID와 이름 목록
     */
    List<Map<String,Object>> genres();
    /**
     * @brief 작품에 연결된 장르 ID를 오름차순으로 조회합니다.
     * @param id 공용 작품 식별자
     * @return 연결된 장르 ID 목록
     */
    List<Long> genreIds(long id);
    /**
     * @brief 제목과 유형이 같은 작품을 ID 순서로 최대 20개 조회합니다.
     * @param title 정확히 일치할 작품 제목
     * @param type MOVIE 또는 TV 작품 유형
     * @return 중복 등록 확인에 사용할 기존 작품 후보
     */
    List<Map<String,Object>> duplicates(@Param("title") String title, @Param("mediaType") String type);
    /**
     * @brief 직접 등록 작품을 MANUAL 공급원과 NULL 외부 ID로 저장합니다.
     * @param work 작품 기본 정보와 createdBy; 성공 후 생성된 id가 채워집니다.
     * @return 삽입된 행 수
     */
    int insert(Map<String,Object> work);
    /**
     * @brief 기존 작품의 제목, 유형, 연도와 소개를 갱신합니다.
     * @param work 대상 id와 갱신할 기본 정보
     * @return 갱신된 행 수
     */
    int update(Map<String,Object> work);
    /**
     * @brief 공용 작품 한 건을 삭제합니다.
     * @param id 삭제할 작품 식별자
     * @return 삭제된 행 수; 참조 제약 위반은 데이터 접근 예외로 전달됩니다.
     */
    int delete(long id);
    /**
     * @brief 작품의 모든 장르 연결 행을 삭제합니다.
     * @param id 공용 작품 식별자
     * @return 삭제된 연결 행 수
     */
    int clearGenres(long id);
    /**
     * @brief 작품과 등록된 장르 사이 연결을 저장합니다.
     * @param workId 공용 작품 식별자
     * @param genreId 공용 장르 식별자
     * @return 삽입된 연결 행 수
     */
    int addGenre(@Param("workId") long workId,@Param("genreId") long genreId);
    /**
     * @brief 공용 장르명을 저장합니다.
     * @param name 검증된 장르명
     * @return 삽입된 행 수
     */
    int createGenre(String name);
    /**
     * @brief 지정한 공용 장르의 이름을 갱신합니다.
     * @param id 장르 식별자
     * @param name 검증된 새 장르명
     * @return 갱신된 행 수
     */
    int renameGenre(@Param("id") long id,@Param("name") String name);
    /**
     * @brief 공용 장르를 삭제합니다.
     * @param id 장르 식별자
     * @return 삭제된 행 수; 연결된 작품이 있으면 참조 제약 예외가 전달됩니다.
     */
    int deleteGenre(long id);
    Map<String,Object> externalWork(@Param("mediaType") String type, @Param("externalId") long externalId);
    int insertExternal(Map<String,Object> work);
    int refreshExternal(Map<String,Object> work);
    Long mappedGenre(@Param("mediaType") String type, @Param("externalId") long id);
    Long unclassifiedGenre();
}
