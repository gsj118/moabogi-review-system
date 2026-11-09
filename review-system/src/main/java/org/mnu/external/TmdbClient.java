package org.mnu.external;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@Component
public class TmdbClient {
    private final String apiKey;
    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String BASE_URL = "https://api.themoviedb.org/3";
    private static final String LANGUAGE = "ko-KR";
    private static final int TIMEOUT_MS = 5000;

    public TmdbClient(@Value("${tmdb.api.key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    public List<Map<String, Object>> search(String query, String mediaType) throws TmdbException {
        if (!isEnabled()) throw new TmdbException("TMDB API 키가 설정되지 않았습니다.", TmdbException.ErrorType.NOT_CONFIGURED);
        if (query == null || query.trim().isEmpty()) throw new TmdbException("검색어를 입력하세요.");
        if (!mediaType.equals("movie") && !mediaType.equals("tv")) throw new TmdbException("영화 또는 드라마를 선택하세요.");

        try {
            String url = String.format("%s/search/%s?api_key=%s&query=%s&language=%s&page=1",
                    BASE_URL, mediaType, apiKey, encode(query.trim()), LANGUAGE);
            String response = rest.getForObject(url, String.class);
            JsonNode root = mapper.readTree(response);

            List<Map<String, Object>> results = new ArrayList<>();
            JsonNode results_array = root.get("results");
            if (results_array != null && results_array.isArray()) {
                for (int i = 0; i < Math.min(results_array.size(), 20); i++) {
                    JsonNode item = results_array.get(i);
                    if (item.get("id") == null) continue;

                    Map<String, Object> work = new HashMap<>();
                    work.put("externalId", item.get("id").asLong());
                    work.put("mediaType", "movie".equals(mediaType) ? "MOVIE" : "TV");
                    work.put("title", item.get("movie".equals(mediaType) ? "title" : "name").asText(""));
                    work.put("releaseYear", extractYear(item.get("movie".equals(mediaType) ? "release_date" : "first_air_date").asText("")));
                    work.put("posterPath", item.get("poster_path").asText(""));
                    work.put("overview", item.get("overview").asText(""));
                    work.put("source", "TMDB");
                    results.add(work);
                }
            }
            return results;
        } catch (HttpClientErrorException.NotFound e) {
            throw new TmdbException("검색 결과가 없습니다.", TmdbException.ErrorType.NOT_FOUND);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new TmdbException("API 요청이 많습니다. 잠시 후 다시 시도해주세요.", TmdbException.ErrorType.RATE_LIMITED);
        } catch (HttpServerErrorException e) {
            throw new TmdbException("TMDB 서버 오류입니다. 잠시 후 다시 시도해주세요.", TmdbException.ErrorType.SERVER_ERROR);
        } catch (ResourceAccessException e) {
            throw new TmdbException("TMDB 연결 시간 초과입니다. 로컬 작품을 등록하거나 다시 시도해주세요.", TmdbException.ErrorType.TIMEOUT);
        } catch (Exception e) {
            throw new TmdbException("외부 작품 검색 중 오류가 발생했습니다. " + e.getMessage(), TmdbException.ErrorType.UNKNOWN);
        }
    }

    public Map<String, Object> getDetails(long externalId, String mediaType) throws TmdbException {
        if (!isEnabled()) throw new TmdbException("TMDB API 키가 설정되지 않았습니다.", TmdbException.ErrorType.NOT_CONFIGURED);

        try {
            String url = String.format("%s/%s/%d?api_key=%s&language=%s",
                    BASE_URL, mediaType.equals("MOVIE") ? "movie" : "tv", externalId, apiKey, LANGUAGE);
            String response = rest.getForObject(url, String.class);
            JsonNode node = mapper.readTree(response);

            Map<String, Object> details = new HashMap<>();
            details.put("externalId", node.get("id").asLong());
            details.put("mediaType", mediaType);
            details.put("title", node.get("movie".equals(mediaType.toUpperCase()) ? "title" : "name").asText(""));
            details.put("releaseYear", extractYear(node.get("movie".equals(mediaType.toUpperCase()) ? "release_date" : "first_air_date").asText("")));
            details.put("overview", node.get("overview").asText(""));
            details.put("posterPath", node.get("poster_path").asText(""));

            List<Long> genreIds = new ArrayList<>();
            JsonNode genres_array = node.get("genres");
            if (genres_array != null && genres_array.isArray()) {
                for (JsonNode genre : genres_array) {
                    genreIds.add(genre.get("id").asLong());
                }
            }
            details.put("genreIds", genreIds);
            details.put("source", "TMDB");

            return details;
        } catch (HttpClientErrorException.NotFound e) {
            throw new TmdbException("작품을 찾을 수 없습니다.", TmdbException.ErrorType.NOT_FOUND);
        } catch (Exception e) {
            throw new TmdbException("작품 상세 조회 중 오류가 발생했습니다.", TmdbException.ErrorType.UNKNOWN);
        }
    }

    private String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private Integer extractYear(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            return Integer.parseInt(dateStr.substring(0, 4));
        } catch (Exception e) {
            return null;
        }
    }

    public static class TmdbException extends Exception {
        public enum ErrorType {
            NOT_CONFIGURED("API 키 미설정"),
            NOT_FOUND("검색 결과 없음"),
            RATE_LIMITED("요청 한계 (429)"),
            SERVER_ERROR("서버 오류"),
            TIMEOUT("시간 초과"),
            UNKNOWN("알 수 없는 오류");

            private final String description;
            ErrorType(String description) { this.description = description; }
            public String getDescription() { return description; }
        }

        private final ErrorType type;
        public TmdbException(String message, ErrorType type) {
            super(message);
            this.type = type;
        }
        public TmdbException(String message) {
            this(message, ErrorType.UNKNOWN);
        }
        public ErrorType getType() { return type; }
    }
}
