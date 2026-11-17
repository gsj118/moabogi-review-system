package org.mnu.mapper;
import java.util.*;
import org.apache.ibatis.annotations.Param;
import org.mnu.domain.QueryFilter;
/**
 * @brief 회원 소유의 감상 기록, 현재 상태와 저장 요청 원장 SQL을 매핑합니다.
 * @details 실제 SQL은 RecordMapper.xml에 있으며 회원 ID로 개인 자료를 제한합니다.
 * 조회 순번과 이전 유효 평점은 전체 이력 기준으로 계산하고, 저장 트랜잭션은 RecordService가 관리합니다.
 */
public interface RecordMapper {
    /**
     * @brief 본인 전체 이력 순번을 계산한 뒤 조건과 페이지를 적용해 기록을 조회합니다.
     * @param userId 인증된 회원 식별자
     * @param query 검증되고 페이지가 보정된 조회 조건
     * @return 선택 페이지의 기록과 전체 이력 기준 previousRating; 미평점은 null
     */
    List<Map<String,Object>> list(@Param("userId") long userId,@Param("q") QueryFilter query);
    /**
     * @brief 목록과 동일한 조건의 본인 감상 기록 건수를 조회합니다.
     * @param userId 인증된 회원 식별자
     * @param query 검증된 조회 조건
     * @return 장르 연결에 의해 중복되지 않는 기록 수
     */
    long count(@Param("userId") long userId,@Param("q") QueryFilter query);
    /**
     * @brief 본인 소유의 기록 한 건과 전체 이력 기준 이전 유효 평점을 조회합니다.
     * @param userId 인증된 회원 식별자
     * @param id 감상 기록 식별자
     * @return 본인 기록 정보; 없거나 다른 회원의 기록이면 null
     */
    Map<String,Object> get(@Param("userId") long userId,@Param("id") long id);
    /**
     * @brief 지정 작품들의 본인 전체 기록과 현재 상태를 일괄 조회합니다.
     * @details 최근 감상일과 최근 유효 평점의 날짜는 다를 수 있으며 기간 필터는 적용하지 않습니다.
     * @param userId 인증된 회원 식별자
     * @param workIds 비어 있지 않은 작품 ID 목록
     * @return 작품별 기록 수, 최근 감상일, 최근 유효 평점과 그 날짜, 현재 상태
     */
    List<Map<String,Object>> workSummaries(@Param("userId") long userId,@Param("workIds") List<Long> workIds);
    /**
     * @brief 감상 이벤트를 새 행으로 저장하고 생성된 기록 ID를 반환 Map에 채웁니다.
     * @param record userId, workId, viewedOn, rating, review를 가진 Map; 성공 후 id가 추가됩니다.
     * @return 삽입된 행 수
     */
    int insert(Map<String,Object> record);
    /**
     * @brief 기록 ID, 회원 ID와 version이 모두 일치할 때 내용을 수정합니다.
     * @details 작품과 소유자는 변경하지 않으며 성공 시 version을 1 증가시킵니다.
     * @param record id, userId, version 및 새 감상일, 평점, 감상평
     * @return 갱신된 행 수; 소유권 불일치나 version 충돌이면 0
     */
    int update(Map<String,Object> record);
    /**
     * @brief ID와 회원 ID가 일치하는 본인 기록만 삭제합니다.
     * @param userId 인증된 회원 식별자
     * @param id 삭제할 감상 기록 식별자
     * @return 삭제된 행 수
     */
    int delete(@Param("userId") long userId,@Param("id") long id);
    /**
     * @brief 회원과 요청 UUID의 조합으로 저장 요청을 INSERT IGNORE 예약합니다.
     * @param userId 인증된 회원 식별자
     * @param token 저장 요청 UUID
     * @param hash 정규화한 요청 내용의 SHA-256 해시
     * @return 예약 행이 추가되면 1, 같은 키가 이미 존재하면 0
     */
    int reserve(@Param("userId") long userId,@Param("token") String token,@Param("hash") String hash);
    /**
     * @brief 동일 저장 요청의 원장을 잠금 조회해 기존 내용과 결과를 확인합니다.
     * @param userId 인증된 회원 식별자
     * @param token 저장 요청 UUID
     * @return requestHash와 recordId; 삭제된 결과의 recordId는 null이며 원장이 없으면 null
     */
    Map<String,Object> submission(@Param("userId") long userId,@Param("token") String token);
    /**
     * @brief 예약된 요청 원장에 생성된 감상 기록 ID를 연결합니다.
     * @param userId 인증된 회원 식별자
     * @param token 예약한 저장 요청 UUID
     * @param recordId 같은 트랜잭션에서 생성한 감상 기록 식별자
     * @return 갱신된 원장 행 수
     */
    int finish(@Param("userId") long userId,@Param("token") String token,@Param("recordId") long recordId);
    /**
     * @brief 회원과 작품별 현재 상태를 새로 저장하거나 기존 상태와 갱신 시각을 바꿉니다.
     * @param userId 인증된 회원 식별자
     * @param workId 공용 작품 식별자
     * @param status 검증된 현재 상태 코드
     * @return MySQL INSERT 또는 UPDATE의 영향 행 수
     */
    int setState(@Param("userId") long userId,@Param("workId") long workId,@Param("status") String status);
    /**
     * @brief 회원과 작품의 현재 상태 행만 삭제하고 감상 기록은 유지합니다.
     * @param userId 인증된 회원 식별자
     * @param workId 공용 작품 식별자
     * @return 삭제된 상태 행 수
     */
    int clearState(@Param("userId") long userId,@Param("workId") long workId);
    /**
     * @brief 회원과 작품에 저장된 현재 상태 코드를 조회합니다.
     * @param userId 인증된 회원 식별자
     * @param workId 공용 작품 식별자
     * @return 상태 코드; 저장된 상태가 없으면 null
     */
    String state(@Param("userId") long userId,@Param("workId") long workId);
    /**
     * @brief 회원의 현재 상태 작품 목록을 조회하며 선택적으로 상태 코드로 제한합니다.
     * @param userId 인증된 회원 식별자
     * @param status 검증된 상태 코드; 빈 문자열이면 전체 현재 상태
     * @return 상태 갱신 시각과 작품 ID 내림차순의 작품 및 상태 목록
     */
    List<Map<String,Object>> states(@Param("userId") long userId,@Param("status") String status);
}
