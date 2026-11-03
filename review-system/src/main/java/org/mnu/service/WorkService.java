package org.mnu.service;

import java.util.*;
import org.mnu.domain.*;
import org.mnu.mapper.WorkMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkService {
    private final WorkMapper mapper;
    public WorkService(WorkMapper mapper) { this.mapper = mapper; }
    public List<Map<String,Object>> list(QueryFilter q) { q.validate();q.clampPage(mapper.count(q));return mapper.list(q); }
    public long count(QueryFilter q) { q.validate(); return mapper.count(q); }
    public List<Map<String,Object>> genres() { return mapper.genres(); }
    public List<Long> genreIds(long id) { return mapper.genreIds(id); }
    public List<Map<String,Object>> duplicates(WorkForm f) { return mapper.duplicates(f.getTitle(), f.getMediaType()); }
    public Map<String,Object> get(long id) { Map<String,Object> w = mapper.get(id); if (w == null) throw new InputError("작품을 찾을 수 없습니다.",404); return w; }
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
    @Transactional public long create(long userId,WorkForm f) {
        Map<String,Object> row=validate(f);
        if (!f.isConfirmDuplicate() && !duplicates(f).isEmpty()) throw new InputError("같은 제목·유형의 작품이 있습니다. 기존 작품을 확인하거나 별개 작품 등록을 확인하세요.");
        row.put("createdBy",userId);mapper.insert(row);long id=((Number)row.get("id")).longValue();
        for(long genre:new LinkedHashSet<Long>(f.getGenreIds())) mapper.addGenre(id,genre);return id;
    }
    private void admin(boolean allowed) { if(!allowed) throw new InputError("공용 정보 관리 권한이 필요합니다.",403); }
    @Transactional public void update(long id,WorkForm f,boolean administrator) { admin(administrator);get(id);Map<String,Object> row=validate(f);row.put("id",id);mapper.update(row);mapper.clearGenres(id);for(long genre:new LinkedHashSet<Long>(f.getGenreIds()))mapper.addGenre(id,genre); }
    @Transactional public void delete(long id,boolean administrator) { admin(administrator);get(id);try {mapper.delete(id);}catch(DataIntegrityViolationException e){throw new InputError("감상 기록 또는 현재 상태에서 참조하는 작품은 삭제할 수 없습니다.",409);} }
    private String genreName(String name) { if(name==null || name.trim().isEmpty() || name.trim().length()>40) throw new InputError("장르명은 1~40자로 입력하세요.");return name.trim(); }
    @Transactional public void createGenre(String name,boolean administrator) {admin(administrator);try {mapper.createGenre(genreName(name));}catch(DataIntegrityViolationException e){throw new InputError("이미 등록된 장르입니다.");}}
    @Transactional public void renameGenre(long id,String name,boolean administrator) {admin(administrator);try {if(mapper.renameGenre(id,genreName(name))!=1)throw new InputError("장르를 찾을 수 없습니다.",404);}catch(DataIntegrityViolationException e){throw new InputError("이미 등록된 장르입니다.");}}
    @Transactional public void deleteGenre(long id,boolean administrator) {admin(administrator);try {mapper.deleteGenre(id);}catch(DataIntegrityViolationException e){throw new InputError("작품에 연결된 장르는 삭제할 수 없습니다.",409);}}
}
