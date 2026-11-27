package org.mnu.service;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mnu.domain.InputError;
import org.mnu.mapper.HiddenGenreMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 장르 숨김은 로그인 회원의 로컬 작품 목록과 검색에만 적용합니다. */
@Service
public class HiddenGenreService {
    private final HiddenGenreMapper mapper;

    public HiddenGenreService(HiddenGenreMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String,Object>> choices(long userId) {
        return mapper.choices(userId);
    }

    @Transactional
    public void replace(long userId, List<Long> selected) {
        // 선택이 비어 있어도 같은 회원의 동시 저장은 한 번에 하나씩 적용합니다.
        if (mapper.lockMember(userId) == null) throw new InputError("회원을 찾을 수 없습니다.", 404);
        Set<Long> available = new HashSet<>();
        for (Map<String,Object> genre : choices(userId)) {
            available.add(((Number) genre.get("id")).longValue());
        }
        Set<Long> wanted = new LinkedHashSet<>(selected);
        if (!available.containsAll(wanted)) throw new InputError("등록된 장르를 선택하세요.");
        mapper.clear(userId);
        if (!wanted.isEmpty()) mapper.insert(userId, wanted);
    }
}
