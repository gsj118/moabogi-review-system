package org.mnu.service;

import java.time.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.mnu.domain.*;
import org.mnu.mapper.RecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @brief 회원 소유의 감상 기록과 현재 시청 상태를 관리하는 서비스입니다.
 * @details 감상마다 새 기록을 저장하고 현재 상태는 회원과 작품별 별도 값으로 유지합니다.
 * 저장 요청 UUID와 내용 해시로 재전송을 구분하며 version으로 오래된 수정의 덮어쓰기를 막습니다.
 */
@Service
public class RecordService {
    /**
     * @brief 소유권이 제한된 기록, 상태 및 요청 원장 SQL을 실행합니다.
     */
    private final RecordMapper mapper;
    /**
     * @brief 감상 대상 공용 작품의 존재 여부를 확인하는 서비스입니다.
     */
    private final WorkService works;
    /**
     * @brief 감상일의 미래 날짜 검증과 오늘 날짜를 결정하는 기준 시계입니다.
     */
    private final Clock clock;
    /**
     * @brief 기록 저장소, 작품 서비스와 날짜 기준 시계를 주입받습니다.
     * @param mapper 회원별 기록과 상태 Mapper
     * @param works 공용 작품 조회 서비스
     * @param clock 오늘 날짜를 결정하는 시계
     */
    public RecordService(RecordMapper mapper, WorkService works, Clock clock) {this.mapper=mapper;this.works=works;this.clock=clock;}
    /**
     * @brief 주입된 시계 기준의 오늘 날짜를 반환합니다.
     * @return 감상일 입력과 미래 날짜 검증에 사용할 날짜
     */
    public LocalDate today() {return LocalDate.now(clock);}
    /**
     * @brief 회원 소유의 감상 기록을 검증된 조건과 보정된 페이지로 조회합니다.
     * @details 전체 이력에서 정한 순번과 이전 유효 평점을 유지하고 현재 평점과의 차이를 보충합니다.
     * @param userId 인증된 회원 식별자
     * @param q 조회 조건과 페이지; 유효한 페이지 값으로 보정됩니다.
     * @return 현재 페이지의 본인 감상 기록 목록
     * @throws InputError 조회 조건이 잘못된 경우
     */
    public List<Map<String,Object>> list(long userId,QueryFilter q) {q.validate();q.clampPage(mapper.count(userId,q));List<Map<String,Object>> rows=mapper.list(userId,q);for(Map<String,Object> row:rows)ratingChange(row);return rows;}
    /**
     * @brief 같은 회원과 조회 조건의 전체 감상 기록 수를 반환합니다.
     * @param userId 인증된 회원 식별자
     * @param q 기간, 작품, 장르 등의 조회 조건
     * @return 중복 장르로 늘어나지 않는 감상 기록 건수
     * @throws InputError 조회 조건이 잘못된 경우
     */
    public long count(long userId,QueryFilter q) {q.validate();return mapper.count(userId,q);}
    /**
     * @brief 본인 소유의 기록 한 건을 조회하고 평점 변화 값을 보충합니다.
     * @param userId 인증된 회원 식별자
     * @param id 감상 기록 식별자
     * @return 감상 기록과 전체 이력 기준의 순번, 이전 유효 평점 및 평점 차이
     * @throws InputError 해당 회원의 기록이 없으면 404 입력 오류
     */
    public Map<String,Object> get(long userId,long id) {Map<String,Object> row=mapper.get(userId,id);if(row==null)throw new InputError("감상 기록을 찾을 수 없습니다.",404);ratingChange(row);return row;}
    // Compare against the preceding rated event in the member's full chronology, never against NULL/zero.
    /**
     * @brief 현재 평점과 전체 이력의 앞선 최신 유효 평점 사이 차이를 계산합니다.
     * @details 두 값이 모두 있을 때만 차이와 증감 부호를 추가합니다.
     * 미평점은 0점으로 취급하지 않으며 바로 앞 감상 원문을 선택하는 기능과는 구분합니다.
     * @param row rating과 previousRating을 가진 조회 결과; 변화 값이 이 Map에 추가됩니다.
     */
    private void ratingChange(Map<String,Object> row) {
        if(row.get("rating")!=null && row.get("previousRating")!=null) {
            BigDecimal change=((BigDecimal)row.get("rating")).subtract((BigDecimal)row.get("previousRating"));
            row.put("ratingChange",change);
            row.put("ratingChangeDirection",change.signum());
        }
    }
    /**
     * @brief 화면에 표시할 작품들의 본인 전체 이력 요약을 한 번에 조회합니다.
     * @details 기간 필터 없이 기록 수, 최근 감상일, 최근 유효 평점과 그 날짜, 현재 상태를 조회합니다.
     * 빈 작품 목록에는 SQL을 실행하지 않습니다.
     * @param userId 인증된 회원 식별자
     * @param workIds 화면에 표시된 공용 작품 식별자 목록
     * @return 작품 ID를 키로 갖는 전체 이력 요약 Map
     */
    public Map<Long,Map<String,Object>> workSummaries(long userId,List<Long> workIds) {
        Map<Long,Map<String,Object>> result=new HashMap<Long,Map<String,Object>>();
        if(!workIds.isEmpty())for(Map<String,Object> row:mapper.workSummaries(userId,workIds))result.put(((Number)row.get("workId")).longValue(),row);
        return result;
    }
    /**
     * @brief 작품의 존재를 확인하고 본인의 해당 작품 전체 이력 요약을 반환합니다.
     * @param userId 인증된 회원 식별자
     * @param workId 공용 작품 식별자
     * @return 감상 기록 수, 최근 감상과 유효 평점, 현재 상태를 가진 요약
     * @throws InputError 작품이 없는 경우
     */
    public Map<String,Object> workSummary(long userId,long workId) {works.get(workId);return workSummaries(userId,Collections.singletonList(workId)).get(workId);}
    /**
     * @brief 감상일, 선택 평점과 감상평을 검증해 저장용 값을 만듭니다.
     * @details 감상일은 연도 1000 이상인 오늘 또는 과거 날짜입니다.
     * 평점은 NULL 또는 0.5~5.0의 0.5 간격이며 감상평은 최대 4000자입니다.
     * @param form 검증할 감상 입력
     * @return viewedOn, rating, review를 갖는 저장용 Map; 미평점 rating은 null
     * @throws InputError 날짜, 평점 또는 감상평이 허용 조건을 벗어나는 경우
     */
    private Map<String,Object> validate(RecordForm form) {
        LocalDate date;
        try {date=LocalDate.parse(form.getViewedOn());}catch(RuntimeException e){throw new InputError("감상일을 YYYY-MM-DD 형식으로 입력하세요.");}
        if(date.isAfter(today()) || date.getYear()<1000)throw new InputError("감상일은 오늘 또는 과거 날짜로 입력하세요.");
        BigDecimal rating=null;
        if(!form.getRating().trim().isEmpty()) {
            try {rating=new BigDecimal(form.getRating().trim());}catch(NumberFormatException e){throw new InputError("평점은 0.5~5.0의 0.5 간격으로 입력하세요.");}
            if(rating.compareTo(new BigDecimal("0.5"))<0 || rating.compareTo(new BigDecimal("5.0"))>0 || rating.remainder(new BigDecimal("0.5")).compareTo(BigDecimal.ZERO)!=0)throw new InputError("평점은 0.5~5.0의 0.5 간격으로 입력하세요.");
            rating=rating.setScale(1);
        }
        if(form.getReview().length()>4000)throw new InputError("감상평은 4,000자 이내로 입력하세요.");
        Map<String,Object> row=new HashMap<String,Object>();row.put("viewedOn",date);row.put("rating",rating);row.put("review",form.getReview());return row;
    }
    /**
     * @brief 동일 UUID로 전송된 요청 내용의 일치 여부를 비교할 SHA-256 해시를 만듭니다.
     * @param row 정규화한 감상일과 평점 등의 저장 값
     * @param f 작품 ID, 저장 동작과 감상평을 가진 입력
     * @return 요청 내용을 UTF-8로 해시한 64자리 16진수 문자열
     * @throws IllegalStateException 해시 계산을 수행할 수 없는 경우
     */
    private String hash(Map<String,Object> row,RecordForm f) {
        String canonical=f.getWorkId()+"\n"+row.get("viewedOn")+"\n"+row.get("rating")+"\n"+f.getAction()+"\n"+f.getReview();
        try {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b&255));return out.toString();}catch(Exception e){throw new IllegalStateException("요청 검증을 수행할 수 없습니다.");}
    }
    /**
     * @brief 요청 UUID를 예약하고 감상 저장과 선택적인 현재 상태 변경을 원자적으로 수행합니다.
     * @details PAST는 기록만 추가하고 COMPLETE는 기록과 현재 상태 COMPLETED를 함께 저장합니다.
     * 동일 UUID와 동일 내용은 이전 결과를 반환하며, 내용이 다르거나 기존 결과가 삭제되었으면 거절합니다.
     * UUID 예약, 기록 INSERT, 상태 변경과 요청 결과 저장은 하나의 트랜잭션입니다.
     * @param userId 인증된 회원 식별자
     * @param form 작품, 날짜, 선택 평점, 감상평, 동작과 UUID를 가진 저장 요청
     * @return 새 기록 또는 같은 요청의 기존 기록 식별자와 중복 여부
     * @throws InputError 입력 오류, 작품 없음, UUID 오류 또는 요청 재사용 충돌인 경우
     */
    @Transactional public SubmitResult submit(long userId,RecordForm form) {
        Map<String,Object> row=validate(form);works.get(form.getWorkId());
        if(!"PAST".equals(form.getAction()) && !"COMPLETE".equals(form.getAction()))throw new InputError("기록 저장 동작을 선택하세요.");
        try {if(!UUID.fromString(form.getToken()).toString().equals(form.getToken()))throw new IllegalArgumentException();}catch(RuntimeException e){throw new InputError("작성 화면을 다시 열어 저장하세요.");}
        String fingerprint=hash(row,form);
        if(mapper.reserve(userId,form.getToken(),fingerprint)==0) {
            Map<String,Object> previous=mapper.submission(userId,form.getToken());
            if(!fingerprint.equals(previous.get("requestHash")))throw new InputError("이미 사용한 저장 요청입니다. 새 작성 화면에서 저장하세요.",409);
            if(previous.get("recordId")==null)throw new InputError("이 요청으로 만든 기록은 이미 삭제되었습니다. 새 작성 화면을 이용하세요.",409);
            return new SubmitResult(((Number)previous.get("recordId")).longValue(),true);
        }
        int sameDayExisting = mapper.sameDayCount(userId, form.getWorkId(), (LocalDate) row.get("viewedOn"));
        row.put("userId",userId);row.put("workId",form.getWorkId());mapper.insert(row);long id=((Number)row.get("id")).longValue();
        if("COMPLETE".equals(form.getAction()))mapper.setState(userId,form.getWorkId(),"COMPLETED");
        mapper.finish(userId,form.getToken(),id);return new SubmitResult(id, false, sameDayExisting);
    }
    /**
     * @brief 소유권과 version을 검사해 감상 내용을 수정합니다.
     * @details 감상일, 평점과 감상평만 갱신하며 작품 소유자와 현재 시청 상태는 바꾸지 않습니다.
     * @param userId 인증된 회원 식별자
     * @param id 수정할 감상 기록 식별자
     * @param form 새 감상 내용과 편집 시점의 version
     * @throws InputError 본인 기록이 없거나 입력 오류 또는 version 충돌인 경우
     */
    @Transactional public void update(long userId,long id,RecordForm form) {
        get(userId,id);Map<String,Object> row=validate(form);row.put("id",id);row.put("userId",userId);row.put("version",form.getVersion());
        if(mapper.update(row)!=1)throw new InputError("다른 창에서 기록이 변경되었습니다. 최신 기록을 확인하세요.",409);
    }
    /**
     * @brief 본인 감상 기록만 삭제하고 공용 작품과 현재 상태는 유지합니다.
     * @param userId 인증된 회원 식별자
     * @param id 삭제할 감상 기록 식별자
     * @throws InputError 본인 기록이 없거나 확인 이후 기록이 변경된 경우
     */
    @Transactional public void delete(long userId,long id) {get(userId,id);if(mapper.delete(userId,id)!=1)throw new InputError("기록이 이미 변경되었습니다.",409);}
    /**
     * @brief 허용된 현재 시청 상태와 선택적인 빈 값을 검증합니다.
     * @param status WANT, WATCHING, COMPLETED, DROPPED 또는 허용된 빈 문자열
     * @param empty 빈 문자열을 허용할지 여부
     * @throws InputError 허용되지 않은 상태인 경우
     */
    private void status(String status,boolean empty) {if(empty && "".equals(status))return;if(!Arrays.asList("WANT","WATCHING","COMPLETED","DROPPED").contains(status))throw new InputError("현재 상태를 선택하세요.");}
    /**
     * @brief 감상 기록을 만들지 않고 작품의 본인 현재 상태만 변경하거나 해제합니다.
     * @param userId 인증된 회원 식별자
     * @param workId 공용 작품 식별자
     * @param status 새 현재 상태; 빈 문자열이면 상태 행을 삭제합니다.
     * @throws InputError 작품이 없거나 허용되지 않은 상태인 경우
     */
    @Transactional public void state(long userId,long workId,String status) {works.get(workId);status(status,true);if("".equals(status))mapper.clearState(userId,workId);else mapper.setState(userId,workId,status);}
    /**
     * @brief 작품의 본인 현재 시청 상태를 조회합니다.
     * @param userId 인증된 회원 식별자
     * @param workId 공용 작품 식별자
     * @return 저장된 상태 코드; 상태가 없으면 null
     */
    public String state(long userId,long workId) {return mapper.state(userId,workId);}
    /**
     * @brief 현재 시청 상태 기준으로 본인의 작품 목록을 조회합니다.
     * @details 과거 감상일 기간 통계와 별개로 현재 저장된 상태를 조회합니다.
     * @param userId 인증된 회원 식별자
     * @param status 조회할 상태 코드; 빈 문자열이면 모든 현재 상태
     * @return 현재 상태 갱신 시각과 작품 ID 내림차순의 목록
     * @throws InputError 상태 필터가 유효하지 않은 경우
     */
    public List<Map<String,Object>> states(long userId,String status) {status(status,true);return mapper.states(userId,status);}
    /**
     * @brief 감상 저장 결과의 기록 식별자와 동일 요청 재처리 여부를 전달합니다.
     */
    public static class SubmitResult {
        /**
         * @brief 저장되었거나 이전 요청에서 생성된 감상 기록 식별자입니다.
         */
        private final long id;
        /**
         * @brief 동일 UUID와 내용의 기존 처리 결과를 재사용했으면 true입니다.
         */
        private final boolean duplicate;
        private final int sameDayExisting;
        /**
         * @brief 감상 저장 결과를 생성합니다.
         * @param id 결과 감상 기록 식별자
         * @param duplicate 동일 요청의 이전 결과 재사용 여부
         */
        public SubmitResult(long id, boolean duplicate) { this(id, duplicate, 0); }
        public SubmitResult(long id, boolean duplicate, int sameDayExisting) {
            this.id = id;
            this.duplicate = duplicate;
            this.sameDayExisting = sameDayExisting;
        }
        public int getSameDayExisting() { return sameDayExisting; }
        /**
         * @brief 결과 감상 기록 식별자를 반환합니다.
         * @return 새로 저장했거나 재사용한 기록의 ID
         */
        public long getId(){return id;}
        /**
         * @brief 같은 요청의 기존 결과를 재사용했는지 반환합니다.
         * @return 기존 결과를 재사용했으면 true, 새 기록을 저장했으면 false
         */
        public boolean isDuplicate(){return duplicate;}
    }
}
