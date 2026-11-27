package org.mnu.external;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import com.sun.net.httpserver.HttpServer;
import org.junit.*;
import org.mnu.config.RootConfig;
import org.mnu.domain.InputError;
import org.mnu.mapper.WorkMapper;
import org.mnu.service.TmdbImportService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import static org.junit.Assert.*;

public class TmdbImportIT {
    private static AnnotationConfigWebApplicationContext root;
    private static JdbcTemplate db;
    private static WorkMapper mapper;
    private static PlatformTransactionManager manager;
    private final long externalId = ThreadLocalRandom.current().nextLong(1000000000L, 2000000000L);
    private final String title = "TMDB 합성 시험 " + externalId;
    private long user, genre;
    private boolean createdDefault;
    private HttpServer server;
    private TmdbClient client;
    private TmdbImportService imports;
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeClass public static void database() {
        Assume.assumeTrue("전용 MySQL 시험 설정", System.getProperty("review.config","").contains("test.properties"));
        root = new AnnotationConfigWebApplicationContext();
        root.setServletContext(new MockServletContext());
        root.register(RootConfig.class);
        root.refresh();
        db = new JdbcTemplate(root.getBean(DataSource.class));
        assertEquals("review_system_test", db.queryForObject("SELECT DATABASE()", String.class));
        mapper = root.getBean(WorkMapper.class);
        manager = root.getBean(PlatformTransactionManager.class);
    }

    @AfterClass public static void closeContext() { if (root != null) root.close(); }

    @Before public void prepare() throws Exception {
        String username = "tmdb_fixture_" + externalId;
        db.update("INSERT INTO app_user(username,password_hash,name,email) VALUES(?, ?, ?, ?)",
                username, "not-a-login", "시험 계정", username + "@example.invalid");
        user = db.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
        db.update("INSERT INTO genre(name) VALUES(?)", title);
        genre = db.queryForObject("SELECT id FROM genre WHERE name=?", Long.class, title);
        createdDefault = db.update("INSERT IGNORE INTO genre(name) VALUES('미분류')") > 0;
        db.update("INSERT INTO external_genre_map(source,media_type,external_genre_id,genre_id) VALUES('TMDB','MOVIE',?,?)", externalId, genre);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/3/", e -> {
            requests.incrementAndGet();
            String data = "{\"id\":" + externalId + ", \"title\":\"" + title + "\", \"name\":\"" + title + "\","
                    + "\"genres\":[{\"id\":" + externalId + "},{\"id\":" + (externalId+1) + "}],"
                    + "\"release_date\":\"2022-01-01\",\"first_air_date\":\"2023-02-01\",\"overview\":\"짧은 소개\",\"poster_path\":\"/poster.jpg\"}";
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            try {
                e.sendResponseHeaders(200,bytes.length);
                e.getResponseBody().write(bytes);
            } finally { e.close(); }
        });
        server.start();
        client = new TmdbClient("http://127.0.0.1:" + server.getAddress().getPort() + "/3", "test-token");
        imports = new TmdbImportService(client, mapper, manager);
    }

    @After public void cleanup() {
        if (server != null) server.stop(0);
        if (user != 0) {
            db.update("DELETE FROM work WHERE created_by=?", user);
            db.update("DELETE FROM app_user WHERE id=?", user);
        }
        if (genre != 0) db.update("DELETE FROM genre WHERE id=?", genre);
        if (createdDefault) db.update("DELETE FROM genre WHERE name='미분류' AND NOT EXISTS (SELECT 1 FROM work_genre WHERE genre_id=genre.id)");
    }

    @Test public void repeatedConcurrentAndCrossMediaSelectionsKeepIdentity() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Long> run = () -> { start.await(); return imports.importWork(user, "MOVIE", externalId, false); };
        long id;
        try {
            Future<Long> first = pool.submit(run), second = pool.submit(run);
            start.countDown();
            id = first.get(15, TimeUnit.SECONDS);
            assertEquals(id, (long) second.get(15, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        int existingRequests = requests.get();
        assertEquals(id, imports.importWork(user, "MOVIE", externalId, false));
        assertEquals(existingRequests, requests.get());
        assertNotEquals(id, imports.importWork(user, "TV", externalId, false));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM work WHERE source='TMDB' AND external_id=?", Integer.class, externalId).intValue());
        List<Long> mapped = mapper.genreIds(id);
        assertEquals(2, mapped.size()); assertTrue(mapped.contains(genre)); assertTrue(mapped.contains(mapper.unclassifiedGenre()));
        Map<String,Object> movie = mapper.get(id);
        assertEquals("/poster.jpg", movie.get("posterPath")); assertEquals("TMDB", movie.get("source"));
        assertEquals(externalId, ((Number) movie.get("externalId")).longValue());
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM viewing_record WHERE work_id=?", Integer.class, id).intValue());
    }

    @Test public void manualCandidateRequiresExplicitSeparateRegistrationAndStaleEntryRefreshes() {
        db.update("INSERT INTO work(title,media_type,source,created_by) VALUES(?,'MOVIE','MANUAL',?)", title, user);
        long manual = db.queryForObject("SELECT id FROM work WHERE title=? AND created_by=?", Long.class, title,user);
        TmdbImportService.ConfirmationRequired conflict = assertThrows(TmdbImportService.ConfirmationRequired.class,
                () -> imports.importWork(user,"MOVIE",externalId,false));
        assertEquals(manual, ((Number) conflict.getMatches().get(0).get("id")).longValue());
        long external = imports.importWork(user,"MOVIE",externalId,true);
        assertNotEquals(manual, external);
        assertEquals("MANUAL", mapper.get(manual).get("source"));
        db.update("UPDATE work SET external_fetched_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 181 DAY) WHERE id=?", external);
        assertEquals("외부 작품 정보 갱신 필요", mapper.get(external).get("title"));
        assertNull(mapper.get(external).get("posterPath"));
        int before = requests.get();
        assertEquals(external, imports.importWork(user,"MOVIE",externalId,false));
        assertEquals(before+1,requests.get());
        assertEquals(title, mapper.get(external).get("title"));
    }

    @Test public void genreFailureRollsBackWorkInsertion() {
        WorkMapper rejecting = (WorkMapper) Proxy.newProxyInstance(WorkMapper.class.getClassLoader(),
                new Class<?>[]{WorkMapper.class}, (object, method, args) -> {
                    if ("addGenre".equals(method.getName())) throw new InputError("합성 장르 저장 실패", 409);
                    try { return method.invoke(mapper,args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                });
        TmdbImportService isolated = new TmdbImportService(client,rejecting,manager);
        assertThrows(InputError.class, () -> isolated.importWork(user,"MOVIE",externalId,false));
        assertNull(mapper.externalWork("MOVIE",externalId));
    }
}
