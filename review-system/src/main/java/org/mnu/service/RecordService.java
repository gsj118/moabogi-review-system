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

@Service
public class RecordService {
    private final RecordMapper mapper;
    private final WorkService works;
    private final Clock clock;
    public RecordService(RecordMapper mapper, WorkService works, Clock clock) {this.mapper=mapper;this.works=works;this.clock=clock;}
    public LocalDate today() {return LocalDate.now(clock);}
    public List<Map<String,Object>> list(long userId,QueryFilter q) {q.validate();q.clampPage(mapper.count(userId,q));List<Map<String,Object>> rows=mapper.list(userId,q);for(Map<String,Object> row:rows)ratingChange(row);return rows;}
    public long count(long userId,QueryFilter q) {q.validate();return mapper.count(userId,q);}
    public Map<String,Object> get(long userId,long id) {Map<String,Object> row=mapper.get(userId,id);if(row==null)throw new InputError("감상 기록을 찾을 수 없습니다.",404);ratingChange(row);return row;}
    // Compare against the preceding rated event in the member's full chronology, never against NULL/zero.
    private void ratingChange(Map<String,Object> row) {
        if(row.get("rating")!=null && row.get("previousRating")!=null) {
            BigDecimal change=((BigDecimal)row.get("rating")).subtract((BigDecimal)row.get("previousRating"));
            row.put("ratingChange",change);
            row.put("ratingChangeDirection",change.signum());
        }
    }
    public Map<Long,Map<String,Object>> workSummaries(long userId,List<Long> workIds) {
        Map<Long,Map<String,Object>> result=new HashMap<Long,Map<String,Object>>();
        if(!workIds.isEmpty())for(Map<String,Object> row:mapper.workSummaries(userId,workIds))result.put(((Number)row.get("workId")).longValue(),row);
        return result;
    }
    public Map<String,Object> workSummary(long userId,long workId) {works.get(workId);return workSummaries(userId,Collections.singletonList(workId)).get(workId);}
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
    private String hash(Map<String,Object> row,RecordForm f) {
        String canonical=f.getWorkId()+"\n"+row.get("viewedOn")+"\n"+row.get("rating")+"\n"+f.getAction()+"\n"+f.getReview();
        try {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b&255));return out.toString();}catch(Exception e){throw new IllegalStateException("요청 검증을 수행할 수 없습니다.");}
    }
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
        row.put("userId",userId);row.put("workId",form.getWorkId());mapper.insert(row);long id=((Number)row.get("id")).longValue();
        if("COMPLETE".equals(form.getAction()))mapper.setState(userId,form.getWorkId(),"COMPLETED");
        mapper.finish(userId,form.getToken(),id);return new SubmitResult(id,false);
    }
    @Transactional public void update(long userId,long id,RecordForm form) {
        get(userId,id);Map<String,Object> row=validate(form);row.put("id",id);row.put("userId",userId);row.put("version",form.getVersion());
        if(mapper.update(row)!=1)throw new InputError("다른 창에서 기록이 변경되었습니다. 최신 기록을 확인하세요.",409);
    }
    @Transactional public void delete(long userId,long id) {get(userId,id);if(mapper.delete(userId,id)!=1)throw new InputError("기록이 이미 변경되었습니다.",409);}
    private void status(String status,boolean empty) {if(empty && "".equals(status))return;if(!Arrays.asList("WANT","WATCHING","COMPLETED","DROPPED").contains(status))throw new InputError("현재 상태를 선택하세요.");}
    @Transactional public void state(long userId,long workId,String status) {works.get(workId);status(status,true);if("".equals(status))mapper.clearState(userId,workId);else mapper.setState(userId,workId,status);}
    public String state(long userId,long workId) {return mapper.state(userId,workId);}
    public List<Map<String,Object>> states(long userId,String status) {status(status,true);return mapper.states(userId,status);}
    public static class SubmitResult {
        private final long id;private final boolean duplicate;
        public SubmitResult(long id,boolean duplicate){this.id=id;this.duplicate=duplicate;}
        public long getId(){return id;}public boolean isDuplicate(){return duplicate;}
    }
}
