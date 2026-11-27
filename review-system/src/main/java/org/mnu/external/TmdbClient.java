package org.mnu.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.mnu.domain.InputError;

/** TMDB 영화·드라마를 검색하고 선택된 작품의 상세 메타데이터를 읽습니다. */
public class TmdbClient {
    private static final String API = "https://api.themoviedb.org/3";
    private static final int MAX_BYTES = 1024 * 1024;
    private final ObjectMapper json = new ObjectMapper();
    private final String api;
    private final String token;

    public TmdbClient(String token) { this(API, token); }
    // Package-local so tests can exercise real HTTP without changing the production API host.
    TmdbClient(String api, String token) { this.api = api; this.token = token == null ? "" : token.trim(); }
    public boolean isEnabled() { return !token.isEmpty(); }

    public static class Failure extends InputError {
        private static final long serialVersionUID = 1L;
        private final String kind;
        Failure(String kind, String message, int status) { super(message, status); this.kind = kind; }
        public String getKind() { return kind; }
    }

    public static class SearchPage {
        private final List<Map<String,Object>> works;
        private final int pages;
        SearchPage(List<Map<String,Object>> works, int pages) { this.works = works; this.pages = pages; }
        public List<Map<String,Object>> getWorks() { return works; }
        public int getPages() { return pages; }
    }

    public SearchPage search(String query, String mediaType, int page) {
        String type = apiType(mediaType);
        if (query == null || query.trim().isEmpty() || query.trim().length() > 200)
            throw new InputError("작품 제목을 1~200자로 입력하세요.");
        if (page < 1 || page > 500) throw new InputError("검색 페이지를 확인하세요.");
        String term;
        try { term = URLEncoder.encode(query.trim(), "UTF-8"); }
        catch (UnsupportedEncodingException e) { throw new AssertionError(e); }
        JsonNode result = request("/search/" + type + "?language=ko-KR&include_adult=false&page=" + page + "&query=" + term);
        if (!result.path("results").isArray()) throw invalidResponse();
        List<Map<String,Object>> works = new ArrayList<>();
        for (JsonNode item : result.get("results")) {
            if (works.size() == 20) break;
            Map<String,Object> row = metadata(item, mediaType);
            if (row != null) works.add(row);
        }
        return new SearchPage(works, Math.min(500, Math.max(page, result.path("total_pages").asInt(page))));
    }

    public Map<String,Object> getDetails(long externalId, String mediaType) {
        if (externalId <= 0) throw new InputError("외부 작품 번호를 확인하세요.");
        JsonNode item = request("/" + apiType(mediaType) + "/" + externalId + "?language=ko-KR");
        Map<String,Object> row = metadata(item, mediaType);
        if (row == null || ((Long) row.get("externalId")) != externalId) throw invalidResponse();
        Set<Long> genres = new LinkedHashSet<>();
        JsonNode genreList = item.path("genres");
        if (!genreList.isMissingNode() && !genreList.isArray()) throw invalidResponse();
        for (JsonNode genre : genreList) {
            JsonNode id = genre.path("id");
            if (id.isIntegralNumber() && id.canConvertToLong() && id.longValue() > 0) genres.add(id.longValue());
        }
        row.put("externalGenreIds", genres);
        return row;
    }

    public static String apiType(String mediaType) {
        if ("MOVIE".equals(mediaType)) return "movie";
        if ("TV".equals(mediaType)) return "tv";
        throw new InputError("영화 또는 드라마를 선택하세요.");
    }

    private Map<String,Object> metadata(JsonNode item, String mediaType) {
        JsonNode id = item.path("id");
        if (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0 || item.path("adult").asBoolean()) return null;
        boolean movie = "MOVIE".equals(mediaType);
        String title = item.path(movie ? "title" : "name").asText("").trim();
        if (title.isEmpty()) title = item.path(movie ? "original_title" : "original_name").asText("").trim();
        if (title.isEmpty() || title.codePointCount(0, title.length()) > 200) return null;
        String overview = item.path("overview").asText("").trim();
        int length = overview.codePointCount(0, overview.length());
        if (length > 1000) overview = overview.substring(0, overview.offsetByCodePoints(0, 1000));
        String poster = item.path("poster_path").asText("");
        Map<String,Object> row = new LinkedHashMap<>();
        row.put("externalId", id.longValue());
        row.put("mediaType", mediaType);
        row.put("title", title);
        row.put("releaseYear", year(item.path(movie ? "release_date" : "first_air_date").asText("")));
        row.put("overview", overview);
        row.put("posterPath", poster.length() <= 255 && poster.matches("/[A-Za-z0-9_-]+\\.(?:jpg|jpeg|png|webp)") ? poster : null);
        return row;
    }

    private Integer year(String date) {
        try {
            int year = LocalDate.parse(date).getYear();
            return year >= 1888 && year <= 2100 ? year : null;
        } catch (DateTimeParseException e) { return null; }
    }

    private Failure invalidResponse() { return new Failure("INVALID_RESPONSE", "TMDB 작품 정보를 읽을 수 없습니다.", 502); }

    private JsonNode request(String path) {
        if (!isEnabled()) throw new Failure("NOT_CONFIGURED", "외부 검색이 연결되지 않았습니다.", 503);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(api + path).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(3000);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            if (status == 401 || status == 403) throw new Failure("AUTH_ERROR", "TMDB 서버 인증 설정을 확인하세요.", 503);
            if (status == 404) throw new Failure("NOT_FOUND", "TMDB에서 작품을 찾을 수 없습니다.", 404);
            if (status == 429) throw new Failure("RATE_LIMITED", "TMDB 요청이 많습니다. 잠시 후 다시 시도하세요.", 429);
            if (status >= 500) throw new Failure("SERVER_ERROR", "TMDB 서버에서 응답하지 않습니다.", 502);
            if (status != 200 || connection.getContentLengthLong() > MAX_BYTES) throw invalidResponse();
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[8192];
                for (;;) {
                    long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                    if (remaining <= 0) throw new SocketTimeoutException();
                    connection.setReadTimeout((int)Math.min(3000, remaining));
                    int count = input.read(chunk);
                    if (count < 0) break;
                    if (bytes.size() + count > MAX_BYTES) throw invalidResponse();
                    bytes.write(chunk, 0, count);
                }
                JsonNode root = json.readTree(bytes.toByteArray());
                if (root == null || !root.isObject()) throw invalidResponse();
                return root;
            }
        } catch (SocketTimeoutException e) {
            throw new Failure("TIMEOUT", "TMDB 응답 시간이 초과됐습니다.", 504);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw invalidResponse();
        } catch (IOException e) {
            throw new Failure("NETWORK_ERROR", "TMDB에 연결할 수 없습니다.", 502);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
