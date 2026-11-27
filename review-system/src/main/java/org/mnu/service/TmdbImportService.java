package org.mnu.service;

import java.util.*;
import org.mnu.domain.InputError;
import org.mnu.external.TmdbClient;
import org.mnu.mapper.WorkMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 선택한 TMDB 작품을 외부 식별자로 재사용하고 메타데이터·장르를 함께 저장합니다. */
@Service
public class TmdbImportService {
    private final TmdbClient client;
    private final WorkMapper mapper;
    private final TransactionTemplate transaction;

    public TmdbImportService(TmdbClient client, WorkMapper mapper, PlatformTransactionManager manager) {
        this.client = client;
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public static class ConfirmationRequired extends InputError {
        private static final long serialVersionUID = 1L;
        private final List<Map<String,Object>> matches;
        private final String title;
        ConfirmationRequired(String title, List<Map<String,Object>> matches) {
            super("같은 제목·유형의 작품이 있습니다. 기존 작품을 확인하세요.", 409);
            this.title = title;
            this.matches = matches;
        }
        public String getTitle() { return title; }
        public List<Map<String,Object>> getMatches() { return matches; }
    }

    private static class ConcurrentInsert extends RuntimeException {
        private static final long serialVersionUID = 1L;
        ConcurrentInsert(DuplicateKeyException cause) { super(cause); }
    }

    public long importWork(long userId, String mediaType, long externalId, boolean confirmDuplicate) {
        TmdbClient.apiType(mediaType);
        if (externalId <= 0) throw new InputError("외부 작품 번호를 확인하세요.");
        Map<String,Object> existing = mapper.externalWork(mediaType, externalId);
        if (existing != null && ((Number) existing.get("fresh")).intValue() != 0) return id(existing);

        // Network I/O finishes before any transaction or database lock is taken.
        Map<String,Object> row = client.getDetails(externalId, mediaType);
        row.put("createdBy", userId);
        try {
            return transaction.execute(status -> {
                Map<String,Object> latest = mapper.externalWork(mediaType, externalId);
                if (latest != null && ((Number) latest.get("fresh")).intValue() != 0) return id(latest);
                if (latest == null) {
                    if (!confirmDuplicate) {
                        List<Map<String,Object>> matches = mapper.duplicates((String) row.get("title"), mediaType);
                        if (!matches.isEmpty()) throw new ConfirmationRequired((String) row.get("title"), matches);
                    }
                    try { mapper.insertExternal(row); }
                    catch (DuplicateKeyException collision) { throw new ConcurrentInsert(collision); }
                } else {
                    row.put("id", id(latest));
                    if (mapper.refreshExternal(row) != 1) throw new InputError("작품이 변경되었습니다. 다시 선택하세요.", 409);
                    mapper.clearGenres(id(row));
                }
                for (long genre : localGenres(mediaType, row)) mapper.addGenre(id(row), genre);
                return id(row);
            });
        } catch (ConcurrentInsert collision) {
            // The failed transaction has rolled back. Read the committed winner using a new transaction.
            Long winner = transaction.execute(status -> {
                Map<String,Object> saved = mapper.externalWork(mediaType, externalId);
                return saved == null ? null : id(saved);
            });
            if (winner != null) return winner;
            throw collision;
        }
    }

    private long id(Map<String,Object> row) { return ((Number) row.get("id")).longValue(); }

    private Set<Long> localGenres(String mediaType, Map<String,Object> row) {
        @SuppressWarnings("unchecked")
        Set<Long> external = (Set<Long>) row.get("externalGenreIds");
        Set<Long> local = new LinkedHashSet<>();
        boolean unknown = external.isEmpty();
        for (long id : external) {
            Long mapped = mapper.mappedGenre(mediaType, id);
            if (mapped == null) unknown = true;
            else local.add(mapped);
        }
        if (unknown) {
            Long unclassified = mapper.unclassifiedGenre();
            if (unclassified == null) throw new InputError("미분류 장르 설정이 필요합니다. 작품을 저장하지 못했습니다.", 503);
            local.add(unclassified);
        }
        return local;
    }
}
