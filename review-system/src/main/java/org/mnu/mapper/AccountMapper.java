package org.mnu.mapper;
import java.util.Map;
/**
 * @brief 회원 계정 조회와 기본 USER 계정 저장을 매핑하는 MyBatis 인터페이스입니다.
 * @details 실제 SQL은 AccountMapper.xml에 정의되며 구현 객체는 MyBatis가 제공합니다.
 */
public interface AccountMapper {
    /**
     * @brief 아이디와 정확히 일치하는 회원 계정을 조회합니다.
     * @param username 로그인 아이디
     * @return 회원 정보와 비밀번호 해시, 권한; 계정이 없으면 null
     */
    Map<String,Object> byUsername(String username);
    /**
     * @brief 새 회원을 USER 권한으로 저장하고 생성 ID를 입력 Map에 채웁니다.
     * @param account username, passwordHash, name, email을 가진 값; 성공 후 id가 추가됩니다.
     * @return 삽입된 행 수
     */
    int insert(Map<String,Object> account);
}
