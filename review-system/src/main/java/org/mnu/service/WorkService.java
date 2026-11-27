package org.mnu.service;

import java.util.*;
import org.mnu.domain.*;
import org.mnu.mapper.WorkMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @brief 공용 작품과 장르의 조회, 검증 및 관리 트랜잭션을 담당합니다.
 * @details 직접 등록과 TMDB 작품 가져오기를 구분합니다. 작품과 장르 연결을 함께 저장하며,
 * 공용 정보의 수정과 삭제는 전달받은 관리자 여부를 검사합니다.
 */
@Service
public class WorkService {
    /**
     * @brief 공용 작품과 장르 SQL을 실행하는 Mapper입니다.
     */
    private final WorkMapper mapper;
    /**
     * @brief 작품과 장르 저장소를 주입받습니다.
     * @param mapper 작품과 장르 MyBatis Mapper
     */
    public WorkService(WorkMapper mapper) { this.mapper = mapper; }
    /**
     * @brief 작품 조회 조건을 검증하고 유효한 페이지의 공용 작품 목록을 반환합니다.
     * @details Mapper의 제목, 유형, 장르 조건으로 조회하며 정렬은 작품 등록 시각과 ID의 내림차순입니다.
     * 페이지 값은 같은 조건의 전체 건수에 맞춰 보정합니다.
     * @param q 조회 조건과 페이지; 보정된 페이지가 이 객체에 반영됩니다.
     * @return 현재 페이지의 작품 정보 목록
     * @throws InputError 조회 조건이 허용 범위를 벗어나는 경우
     */
    public List<Map<String,Object>> list(long userId, QueryFilter q) {
        q.validate();
        q.clampPage(mapper.count(userId, q));
        return mapper.list(userId, q);
    }
    public List<Map<String,Object>> list(QueryFilter q) { return list(0, q); }
    /**
     * @brief 작품 조회 조건을 검증하고 같은 조건의 전체 작품 수를 반환합니다.
     * @param q 제목, 유형과 장르 등의 조회 조건
     * @return 조건에 일치하는 공용 작품 수
     * @throws InputError 조회 조건이 잘못된 경우
     */
    public long count(long userId, QueryFilter q) { q.validate(); return mapper.count(userId, q); }
    public long count(QueryFilter q) { return count(0, q); }
    /**
     * @brief 선택 가능한 공용 장르를 이름과 ID 순서로 조회합니다.
     * @return 장르 ID와 이름 목록
     */
    public List<Map<String,Object>> genres() { return mapper.genres(); }
    /**
     * @brief 작품에 연결된 장르 식별자를 조회합니다.
     * @param id 작품 식별자
     * @return 작품에 연결된 장르 ID 목록
     */
    public List<Long> genreIds(long id) { return mapper.genreIds(id); }
    /**
     * @brief 제목과 유형이 정확히 일치하는 기존 작품 후보를 조회합니다.
     * @param f 중복 확인에 사용할 작품 제목과 유형
     * @return ID 순서의 중복 후보; Mapper가 최대 20개를 반환합니다.
     */
    public List<Map<String,Object>> duplicates(WorkForm f) { return mapper.duplicates(f.getTitle(), f.getMediaType()); }
    /**
     * @brief 식별자로 공용 작품을 조회하고 존재 여부를 확인합니다.
     * @param id 작품 식별자
     * @return 작품 기본 정보와 연결 장르 이름
     * @throws InputError 작품이 없으면 404 입력 오류
     */
    public Map<String,Object> get(long id) { Map<String,Object> w = mapper.get(id); if (w == null) throw new InputError("작품을 찾을 수 없습니다.",404); return w; }
    /**
     * @brief 작품 입력의 제목, 유형, 소개, 연도와 등록된 장르를 검증합니다.
     * @details 제목은 1~200자, 소개는 최대 1000자, 연도는 비어 있거나 1888~2100이어야 합니다.
     * 장르는 등록된 ID만 최대 20개를 허용하며 빈 연도는 NULL로 저장합니다.
     * @param f 검증할 작품 입력
     * @return title, mediaType, releaseYear, synopsis를 가진 저장용 값
     * @throws InputError 입력 값이나 장르 선택이 유효하지 않은 경우
     */
    private Map<String,Object> validate(WorkForm f) {
        if (f.getTitle().isEmpty() || f.getTitle().length()>200) throw new InputError("제목은 1~200자로 입력하세요.");
        if (!"MOVIE".equals(f.getMediaType()) && !"TV".equals(f.getMediaType())) throw new InputError("영화 또는 드라마를 선택하세요.");
        if (f.getSynopsis().length()>1000) throw new InputError("소개는 1,000자 이내로 입력하세요.");
        Integer year=null;
        if (!f.getReleaseYear().isEmpty()) {
            try { year=Integer.valueOf(f.getReleaseYear()); } catch (NumberFormatException e) { throw new InputError("연도는 숫자로 입력하세요."); }
            if (year<1888 || year>2100) throw new InputError("연도는 1888~2100 사이로 입력하세요.");
        }
        Set<Long> known=new HashSet<Long>(); for(Map<String,Object> g:genres()) known.add(((Number)g.get("id")).longValue());
        if (f.getGenreIds().size()>20 || !known.containsAll(f.getGenreIds())) throw new InputError("등록된 장르를 선택하세요.");
        Map<String,Object> row=new HashMap<String,Object>(); row.put("title",f.getTitle());row.put("mediaType",f.getMediaType());row.put("releaseYear",year);row.put("synopsis",f.getSynopsis());return row;
    }
    /**
     * @brief 작품과 선택 장르를 하나의 트랜잭션으로 직접 등록합니다.
     * @details 같은 제목과 유형의 후보가 있으면 별개 작품 등록 확인이 필요합니다.
     * 중복 장르 ID는 한 번만 연결하며 이 작업은 개인 감상 기록을 만들지 않습니다.
     * @param userId 작품 등록자의 인증된 회원 식별자
     * @param f 작품 정보와 중복 등록 확인 여부
     * @return 생성된 공용 작품 식별자
     * @throws InputError 작품 입력이 잘못되었거나 중복 후보를 확인하지 않은 경우
     */
    @Transactional public long create(long userId,WorkForm f) {
        Map<String,Object> row=validate(f);
        if (!f.isConfirmDuplicate() && !duplicates(f).isEmpty()) throw new InputError("같은 제목·유형의 작품이 있습니다. 기존 작품을 확인하거나 별개 작품 등록을 확인하세요.");
        row.put("createdBy",userId);mapper.insert(row);long id=((Number)row.get("id")).longValue();
        for(long genre:new LinkedHashSet<Long>(f.getGenreIds())) mapper.addGenre(id,genre);return id;
    }
    /**
     * @brief 공용 정보 관리에 필요한 관리자 여부를 검사합니다.
     * @param allowed 인증 정보에서 판정한 관리자 여부
     * @throws InputError 관리자 권한이 없으면 403 입력 오류
     */
    private void admin(boolean allowed) { if(!allowed) throw new InputError("공용 정보 관리 권한이 필요합니다.",403); }
    /**
     * @brief 관리자 권한으로 작품 정보와 장르 연결을 함께 갱신합니다.
     * @details 기존 장르 연결을 지운 뒤 선택한 고유 장르를 다시 연결합니다.
     * @param id 수정할 작품 식별자
     * @param f 검증할 작품 정보와 장르 선택
     * @param administrator 인증 정보에서 판정한 관리자 여부
     * @throws InputError 권한이 없거나 작품이 없거나 입력이 잘못된 경우
     */
    @Transactional public void update(long id,WorkForm f,boolean administrator) { admin(administrator);get(id);Map<String,Object> row=validate(f);row.put("id",id);mapper.update(row);mapper.clearGenres(id);for(long genre:new LinkedHashSet<Long>(f.getGenreIds()))mapper.addGenre(id,genre); }
    /**
     * @brief 관리자 권한으로 참조되지 않는 공용 작품을 삭제합니다.
     * @param id 삭제할 작품 식별자
     * @param administrator 인증 정보에서 판정한 관리자 여부
     * @throws InputError 권한 없음, 작품 없음, 감상 기록이나 현재 상태가 참조 중인 경우
     */
    @Transactional public void delete(long id,boolean administrator) { admin(administrator);get(id);try {mapper.delete(id);}catch(DataIntegrityViolationException e){throw new InputError("감상 기록 또는 현재 상태에서 참조하는 작품은 삭제할 수 없습니다.",409);} }
    /**
     * @brief 장르명을 앞뒤 공백 제거 후 1~40자로 검증합니다.
     * @param name 입력 장르명
     * @return 공백을 제거한 장르명
     * @throws InputError 장르명이 없거나 길이 범위를 벗어나는 경우
     */
    private String genreName(String name) { if(name==null || name.trim().isEmpty() || name.trim().length()>40) throw new InputError("장르명은 1~40자로 입력하세요.");return name.trim(); }
    /**
     * @brief 관리자 권한으로 중복되지 않는 공용 장르를 생성합니다.
     * @param name 생성할 장르명
     * @param administrator 인증 정보에서 판정한 관리자 여부
     * @throws InputError 권한이 없거나 장르명이 잘못되었거나 중복되는 경우
     */
    @Transactional public void createGenre(String name,boolean administrator) {admin(administrator);try {mapper.createGenre(genreName(name));}catch(DataIntegrityViolationException e){throw new InputError("이미 등록된 장르입니다.");}}
    /**
     * @brief 관리자 권한으로 기존 장르의 이름을 변경합니다.
     * @param id 변경할 장르 식별자
     * @param name 변경할 장르명
     * @param administrator 인증 정보에서 판정한 관리자 여부
     * @throws InputError 권한, 장르명, 중복 오류 또는 대상 장르가 없는 경우
     */
    @Transactional public void renameGenre(long id,String name,boolean administrator) {admin(administrator);protectUnclassified(id);try {if(mapper.renameGenre(id,genreName(name))!=1)throw new InputError("장르를 찾을 수 없습니다.",404);}catch(DataIntegrityViolationException e){throw new InputError("이미 등록된 장르입니다.");}}
    /**
     * @brief 관리자 권한으로 작품에 연결되지 않은 장르를 삭제합니다.
     * @param id 삭제할 장르 식별자
     * @param administrator 인증 정보에서 판정한 관리자 여부
     * @throws InputError 권한이 없거나 작품이 참조하는 장르인 경우
     */
    @Transactional public void deleteGenre(long id,boolean administrator) {admin(administrator);try {mapper.deleteGenre(id);}catch(DataIntegrityViolationException e){throw new InputError("작품에 연결된 장르는 삭제할 수 없습니다.",409);}}

    private void protectUnclassified(long id) {
        if (Objects.equals(mapper.unclassifiedGenre(), id))
            throw new InputError("외부 작품의 기본 장르인 미분류는 이름을 변경하거나 삭제할 수 없습니다.", 409);
    }
}
