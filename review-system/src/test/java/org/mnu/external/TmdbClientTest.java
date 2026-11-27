package org.mnu.external;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.mnu.domain.InputError;
import static org.junit.Assert.*;

public class TmdbClientTest {
    private HttpServer server;
    private TmdbClient client;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> body = new AtomicReference<>("{}");
    private final AtomicReference<URI> requested = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @Before public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/3", exchange -> {
            requested.set(exchange.getRequestURI());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] data = body.get().getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(status.get(), data.length);
                exchange.getResponseBody().write(data);
            } finally { exchange.close(); }
        });
        server.start();
        client = new TmdbClient("http://127.0.0.1:" + server.getAddress().getPort() + "/3", "test-token");
    }

    @After public void stop() { server.stop(0); }

    @Test public void movieSearchKeepsProviderOrderAndEscapesQuery() {
        body.set("{\"total_pages\":8,\"results\":[{\"id\":23,\"title\":\"같은 제목\",\"release_date\":\"2024-05-01\",\"poster_path\":\"/cover.jpg\"},"
                + "{\"id\":24,\"original_title\":\"원제\",\"poster_path\":\"https://example.invalid\"},{\"id\":25,\"adult\":true,\"title\":\"제외\"}]}");
        TmdbClient.SearchPage result = client.search("봄 & 여름", "MOVIE", 2);
        assertEquals(8, result.getPages());
        assertEquals(2, result.getWorks().size());
        assertEquals(23L, result.getWorks().get(0).get("externalId"));
        assertEquals(2024, result.getWorks().get(0).get("releaseYear"));
        assertEquals("/cover.jpg", result.getWorks().get(0).get("posterPath"));
        assertEquals("원제", result.getWorks().get(1).get("title"));
        assertNull(result.getWorks().get(1).get("releaseYear"));
        assertNull(result.getWorks().get(1).get("posterPath"));
        assertEquals("/3/search/movie", requested.get().getPath());
        assertTrue(requested.get().getRawQuery().contains("include_adult=false&page=2"));
        assertTrue(requested.get().getRawQuery().contains("%26"));
        assertFalse(requested.get().toString().contains("test-token"));
        assertEquals("Bearer test-token", authorization.get());
    }

    @Test public void detailsSeparateMovieAndTelevisionWithSameIdAndMapGenres() {
        body.set("{\"id\":12,\"title\":\"영화명\",\"name\":\"드라마명\",\"release_date\":\"2022-01-01\",\"first_air_date\":\"2023-02-02\","
                + "\"genres\":[{\"id\":18},{\"id\":18},{\"id\":99}],\"overview\":\"소개\"}");
        Map<String,Object> movie = client.getDetails(12, "MOVIE");
        assertEquals("/3/movie/12", requested.get().getPath());
        assertEquals("영화명", movie.get("title"));
        assertEquals(2022, movie.get("releaseYear"));
        assertEquals(new LinkedHashSet<>(Arrays.asList(18L,99L)), movie.get("externalGenreIds"));
        Map<String,Object> tv = client.getDetails(12, "TV");
        assertEquals("/3/tv/12", requested.get().getPath());
        assertEquals("드라마명", tv.get("title"));
        assertEquals(2023, tv.get("releaseYear"));
        assertEquals("INVALID_RESPONSE", failure(() -> client.getDetails(13, "TV")));
    }

    @Test public void noTokenInvalidTypeAndInvalidPageMakeNoRequest() {
        assertEquals("NOT_CONFIGURED", failure(() -> new TmdbClient("").search("영화", "MOVIE", 1)));
        assertThrows(InputError.class, () -> client.search("영화", "BOOK", 1));
        assertThrows(InputError.class, () -> client.search("영화", "MOVIE", 501));
        assertThrows(InputError.class, () -> client.getDetails(0, "MOVIE"));
        assertNull(requested.get());
    }

    @Test public void distinguishesEmptyMalformedMissingAuthenticationAndProviderFailures() {
        body.set("{\"results\":[]}");
        assertTrue(client.search("없음","TV",1).getWorks().isEmpty());
        body.set("null");
        assertEquals("INVALID_RESPONSE", failure(() -> client.search("영화","MOVIE",1)));
        body.set("{");
        assertEquals("INVALID_RESPONSE", failure(() -> client.search("영화","MOVIE",1)));
        int[] codes = {401,403,404,429,500,503,302};
        String[] reasons = {"AUTH_ERROR","AUTH_ERROR","NOT_FOUND","RATE_LIMITED","SERVER_ERROR","SERVER_ERROR","INVALID_RESPONSE"};
        for (int i = 0; i < codes.length; i++) {
            status.set(codes[i]);
            assertEquals(reasons[i], failure(() -> client.getDetails(4,"MOVIE")));
        }
    }

    @Test public void boundsResponseSizeAndReportsSlowProvider() {
        char[] oversized = new char[1024 * 1024 + 1];
        Arrays.fill(oversized, ' ');
        body.set(new String(oversized));
        assertEquals("INVALID_RESPONSE", failure(() -> client.search("영화","MOVIE",1)));
        server.createContext("/3/movie/10", exchange -> {
            try { Thread.sleep(3500); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        assertEquals("TIMEOUT", failure(() -> client.getDetails(10, "MOVIE")));
    }

    private String failure(Runnable action) {
        return assertThrows(TmdbClient.Failure.class, action::run).getKind();
    }
}
