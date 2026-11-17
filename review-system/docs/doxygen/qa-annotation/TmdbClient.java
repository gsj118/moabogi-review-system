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

/**
 * @brief QA_JO 브랜치에서 TMDB 검색과 작품 상세 조회를 담당하는 외부 연동 컴포넌트입니다.
 * @details 원본 커밋은 dfc9a33138f1099bc234cb1841fd122c5a50fffc입니다.
 * 영화·TV 검색 결과와 상세 JSON을 Map으로 변환해 WorkService에 전달합니다.
 * 이 문서는 해당 커밋의 현재 구현을 설명하며 실제 API 호출 성공을 보증하지 않습니다.
 * 현재 구현의 제약은 다음과 같습니다.
 * @li getDetails()는 영화 유형의 JSON 필드 선택에 대소문자 비교 오류가 있습니다.
 * @li 반환한 외부 ID·공급원은 후속 WorkService의 저장 Map에 전달되지 않습니다.
 * @li 외부 장르 ID를 로컬 장르 ID로 변환하는 매핑 단계가 없습니다.
 * 또한 TIMEOUT_MS 상수는 현재 RestTemplate의 연결·읽기 제한에 적용되지 않습니다.
 * @see org.mnu.service.WorkService
 * @see org.mnu.controller.WorkController
 */
@Component
public class TmdbClient {
    /** @brief 서버 외부 설정 tmdb.api.key에서 주입한 TMDB API 키입니다. */
    private final String apiKey;
    /** @brief TMDB HTTPS GET 요청을 수행하는 클라이언트입니다. 현재 명시적 제한 시간 설정은 없습니다. */
    private final RestTemplate rest = new RestTemplate();
    /** @brief 응답 문자열을 Jackson JSON 트리로 읽는 변환기입니다. */
    private final ObjectMapper mapper = new ObjectMapper();
    /** @brief 요청을 보낼 고정 TMDB API 주소입니다. */
    private static final String BASE_URL = "https://api.themoviedb.org/3";
    /** @brief 검색·상세 요청에 사용하는 한국어 언어 코드입니다. */
    private static final String LANGUAGE = "ko-KR";
    /** @brief 제한 시간으로 선언된 밀리초 값입니다. 현재 HTTP 클라이언트에는 적용되지 않습니다. */
    private static final int TIMEOUT_MS = 5000;

    /**
     * @brief 외부 설정의 API 키를 보관합니다.
     * @param apiKey tmdb.api.key 설정값이며 누락된 경우 빈 문자열입니다.
     */
    public TmdbClient(@Value("${tmdb.api.key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * @brief API 키 문자열이 설정되어 있는지 확인합니다.
     * @details 인증 유효성이나 실제 외부 연결 성공 여부를 검사하지 않습니다.
     * @return 키가 null이 아니고 공백을 제거한 값이 비어 있지 않으면 true입니다.
     */
    public boolean isEnabled() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    /**
     * @brief 제목으로 TMDB 영화 또는 TV 작품의 첫 검색 페이지를 조회합니다.
     * @details 최대 20개를 외부 ID, 앱의 매체 유형, 제목, 연도, 포스터 경로,
     * 소개, 공급원이 담긴 Map으로 반환합니다. 현재 다음 페이지 조회는 없습니다.
     * JSON의 일부 필드 누락은 일반 오류로 변환될 수 있습니다.
     * @param query 공백을 제외하고 한 글자 이상인 제목 검색어입니다.
     * @param mediaType API용 소문자 유형 movie 또는 tv입니다. null은 허용하지 않습니다.
     * @return 검색 결과 Map 목록이며 정상 응답에 결과가 없으면 빈 목록입니다.
     * @throws TmdbException 키 미설정, 잘못된 검색 조건, HTTP 오류 또는 응답 변환 실패 시 발생합니다.
     */
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

    /**
     * @brief TMDB 외부 ID로 작품 상세를 조회해 가져오기용 Map을 구성합니다.
     * @details 현재 구현은 유형을 대문자로 바꾼 뒤 소문자 movie와 비교하므로
     * MOVIE 입력에도 TV 필드 name·first_air_date를 선택합니다. 해당 필드가 없는
     * 영화 응답은 일반 오류로 처리됩니다. 이 결함은 문서화 기준본에 그대로 남아 있습니다.
     * genreIds에는 TMDB 장르 ID를 그대로 넣으며 로컬 장르로 변환하지 않습니다.
     * 외부 ID·공급원을 후속 저장 Map에 전달하지 않는 WorkService의 제약도 별도로 있습니다.
     * @param externalId TMDB 작품 식별자입니다. 현재 이 메소드에는 양수 검증이 없습니다.
     * @param mediaType 앱의 매체 유형 MOVIE 또는 TV입니다. 현재 MOVIE 이외의 값은 TV 경로를 사용합니다.
     * @return 외부 ID·유형·제목·연도·소개·포스터 경로·외부 장르 ID·공급원을 담은 Map입니다.
     * @throws TmdbException 키 미설정, 작품 없음, HTTP 요청 또는 JSON 변환 실패 시 발생합니다.
     */
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

    /**
     * @brief 검색어를 UTF-8 URL 매개변수 형식으로 인코딩합니다.
     * @param value 인코딩할 문자열입니다.
     * @return 인코딩한 값이며 변환 예외가 발생하면 입력값을 그대로 반환합니다.
     */
    private String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * @brief 날짜 문자열의 앞 네 글자를 정수 연도로 변환합니다.
     * @param dateStr 연도가 앞에 있는 날짜 문자열입니다. null·빈 문자열도 받을 수 있습니다.
     * @return 읽을 수 있는 정수 연도이며 누락·길이 부족·숫자 변환 실패 시 null입니다.
     */
    private Integer extractYear(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            return Integer.parseInt(dateStr.substring(0, 4));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @brief 외부 연동 실패 메시지와 분류를 호출 계층에 전달하는 검사 예외입니다.
     * @details 현재 원인 예외를 생성자의 cause로 연결하지 않습니다.
     */
    public static class TmdbException extends Exception {
        /** @brief 현재 클라이언트가 구분하는 외부 연동 실패 유형입니다. */
        public enum ErrorType {
            /** @brief API 키가 비어 있어 요청을 수행하지 않은 상태입니다. */
            NOT_CONFIGURED("API 키 미설정"),
            /** @brief 원격 서버가 HTTP 404로 응답한 상태입니다. 정상 빈 검색 목록과는 별개입니다. */
            NOT_FOUND("검색 결과 없음"),
            /** @brief 검색 요청에서 HTTP 429를 받은 상태입니다. */
            RATE_LIMITED("요청 한계 (429)"),
            /** @brief 검색 요청에서 원격 서버의 HTTP 5xx를 받은 상태입니다. */
            SERVER_ERROR("서버 오류"),
            /** @brief 검색 요청에서 ResourceAccessException이 발생한 상태입니다. 연결 오류도 이 유형에 포함됩니다. */
            TIMEOUT("시간 초과"),
            /** @brief 별도 유형으로 분류하지 않은 입력·요청·응답 변환 오류입니다. */
            UNKNOWN("알 수 없는 오류");

            /** @brief 실패 유형의 한국어 설명입니다. */
            private final String description;
            /**
             * @brief 실패 유형의 설명을 보관합니다.
             * @param description 한국어 유형 설명입니다.
             */
            ErrorType(String description) { this.description = description; }
            /**
             * @brief 실패 유형 설명을 반환합니다.
             * @return 한국어 유형 설명입니다.
             */
            public String getDescription() { return description; }
        }

        /** @brief 예외를 처리할 때 사용할 실패 분류입니다. */
        private final ErrorType type;
        /**
         * @brief 메시지와 지정한 실패 유형을 가진 예외를 만듭니다.
         * @param message 호출 계층에 전달할 실패 설명입니다.
         * @param type 호출 계층이 확인할 오류 유형입니다.
         */
        public TmdbException(String message, ErrorType type) {
            super(message);
            this.type = type;
        }
        /**
         * @brief 실패 유형이 UNKNOWN인 예외를 만듭니다.
         * @param message 호출 계층에 전달할 실패 설명입니다.
         */
        public TmdbException(String message) {
            this(message, ErrorType.UNKNOWN);
        }
        /**
         * @brief 예외의 실패 분류를 반환합니다.
         * @return 생성 시 지정한 ErrorType입니다.
         */
        public ErrorType getType() { return type; }
    }
}
