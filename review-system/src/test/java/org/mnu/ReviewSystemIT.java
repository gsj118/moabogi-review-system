package org.mnu;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import javax.servlet.Filter;
import javax.sql.DataSource;
import org.junit.*;
import org.mnu.config.RootConfig;
import org.mnu.domain.*;
import org.mnu.service.*;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.*;

public class ReviewSystemIT {
    @Configuration public static class FixedClock {
        @Bean @Primary public Clock testClock(){return Clock.fixed(Instant.parse("2026-10-13T03:00:00Z"),ZoneId.of("Asia/Seoul"));}
    }
    private static AnnotationConfigWebApplicationContext root;
    private static XmlWebApplicationContext web;
    private static JdbcTemplate db;
    private static AccountService accounts;
    private static WorkService works;
    private static RecordService records;
    private static StatsService stats;
    private static MockMvc mvc;
    private long user,other,admin,drama,sf;
    @BeforeClass public static void initialize() {
        Assume.assumeTrue("Set -Dreview.config to a dedicated MySQL test configuration",System.getProperty("review.config","").contains("test.properties"));
        MockServletContext servlet=new MockServletContext();
        root=new AnnotationConfigWebApplicationContext();root.setServletContext(servlet);root.register(RootConfig.class,FixedClock.class);root.refresh();
        db=new JdbcTemplate(root.getBean(DataSource.class));
        assertEquals("review_system_test",db.queryForObject("SELECT DATABASE()",String.class));
        accounts=root.getBean(AccountService.class);works=root.getBean(WorkService.class);records=root.getBean(RecordService.class);stats=root.getBean(StatsService.class);
        web=new XmlWebApplicationContext();web.setParent(root);web.setServletContext(servlet);web.setConfigLocation("file:src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml");web.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(web).apply(springSecurity((Filter)root.getBean("springSecurityFilterChain"))).build();
    }
    @AfterClass public static void close(){if(web!=null)web.close();if(root!=null)root.close();}
    @Before public void clean(){
        db.execute("DROP TRIGGER IF EXISTS integration_fail_state");
        for(String table:Arrays.asList("record_submission","user_work_state","viewing_record","work_genre","work","app_user","genre"))db.update("DELETE FROM "+table);
        user=register("viewer_a");other=register("viewer_b");admin=register("admin_user");db.update("UPDATE app_user SET role='ADMIN' WHERE id=?",admin);
        db.update("INSERT INTO genre(name) VALUES('드라마'),('SF'),('코미디')");drama=db.queryForObject("SELECT id FROM genre WHERE name='드라마'",Long.class);sf=db.queryForObject("SELECT id FROM genre WHERE name='SF'",Long.class);
    }
    private long register(String name){SignupForm f=new SignupForm();f.setUsername(name);f.setPassword(UUID.randomUUID().toString()+"!9");f.setName("시험 회원");f.setEmail(name+"@example.invalid");return accounts.register(f);}
    private long work(String title,String type,Long... genres){WorkForm f=new WorkForm();f.setTitle(title);f.setMediaType(type);f.setGenreIds(Arrays.asList(genres));return works.create(user,f);}
    private RecordForm form(long work,String date,String rating,String action){RecordForm f=new RecordForm();f.setWorkId(work);f.setViewedOn(date);f.setRating(rating);f.setAction(action);f.setToken(UUID.randomUUID().toString());return f;}
    private long save(long work,String date,String rating){return records.submit(user,form(work,date,rating,"PAST")).getId();}
    private QueryFilter october(){QueryFilter q=new QueryFilter();q.setFrom("2026-10-01");q.setTo("2026-10-31");return q;}
    @SuppressWarnings("unchecked") private Map<String,Object> summary(Map<String,Object> r){return (Map<String,Object>)r.get("summary");}
    private long number(Map<String,Object> map,String name){return ((Number)map.get(name)).longValue();}
    @SuppressWarnings("unchecked") private Map<String,Object> genre(Map<String,Object> report,String name){for(Map<String,Object> g:(List<Map<String,Object>>)report.get("genres"))if(name.equals(g.get("name")))return g;throw new AssertionError("Genre missing");}
    private void invalid(Runnable action,int status){try{action.run();fail("Expected validation/access error");}catch(InputError e){assertEquals(status,e.getStatus());}}

    @Test public void requiredOctoberStatisticsAndLatestNullRating(){
        long a=work("작품 A","TV",drama,sf),b=work("작품 B","MOVIE",drama),c=work("작품 C","MOVIE",sf),d=work("상태만 있는 작품","TV",drama);
        save(a,"2026-09-20","4.0");save(a,"2026-10-10","4.5");save(a,"2026-10-12","");save(b,"2026-10-11","3.0");save(b,"2026-10-13","4.0");save(c,"2026-09-21","5.0");records.state(user,d,"COMPLETED");
        Map<String,Object> report=stats.report(user,october()),s=summary(report);assertEquals(4,number(s,"recordCount"));assertEquals(2,number(s,"workCount"));assertEquals(2,number(s,"rewatchedWorks"));assertEquals(3,number(s,"rewatches"));assertEquals(1,number(s,"unratedCount"));
        assertEquals(0,new BigDecimal("4.25").compareTo((BigDecimal)genre(report,"드라마").get("averageRating")));assertEquals(2,number(genre(report,"드라마"),"sampleSize"));assertEquals(0,new BigDecimal("4.50").compareTo((BigDecimal)genre(report,"SF").get("averageRating")));assertEquals(1,number(genre(report,"SF"),"sampleSize"));assertNull(genre(report,"코미디").get("averageRating"));
        QueryFilter q=october();q.setGenreId(drama);assertEquals(4,records.count(user,q));assertEquals(4,records.list(user,q).size());assertEquals("COMPLETED",records.state(user,d));
    }
    @Test public void representativeRecalculatesWithoutTakingRatingOutsidePeriod(){
        long a=work("대표 평점","MOVIE",drama);save(a,"2026-09-20","5.0");long id=save(a,"2026-10-10","4.5");save(a,"2026-10-12","");
        RecordForm f=form(a,"2026-10-10","3.0","PAST");records.update(user,id,f);assertEquals(0,new BigDecimal("3.0").compareTo((BigDecimal)genre(stats.report(user,october()),"드라마").get("averageRating")));
        records.delete(user,id);assertNull(genre(stats.report(user,october()),"드라마").get("averageRating"));assertEquals(1,number(summary(stats.report(user,october())),"rewatches"));
    }
    @Test public void sameDaySeparateEventsAndDurableIdempotency(){
        long a=work("동일 날짜","MOVIE");RecordForm f=form(a,"2026-10-10","4.0","PAST");long id=records.submit(user,f).getId();assertTrue(records.submit(user,f).isDuplicate());save(a,"2026-10-10","4.0");assertEquals(2,records.count(user,new QueryFilter()));
        f.setReview("다른 요청");invalid(()->records.submit(user,f),409);f.setReview("");records.delete(user,id);invalid(()->records.submit(user,f),409);assertEquals(1,records.count(user,new QueryFilter()));
    }
    @Test public void simultaneousReplayCreatesOnlyOneRecord() throws Exception {
        long a=work("동시 제출","MOVIE");RecordForm f=form(a,"2026-10-10","4.0","COMPLETE");ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch go=new CountDownLatch(1);
        try{Callable<RecordService.SubmitResult> task=()->{go.await();return records.submit(user,f);};Future<RecordService.SubmitResult> x=executor.submit(task),y=executor.submit(task);go.countDown();RecordService.SubmitResult rx=x.get(15,TimeUnit.SECONDS),ry=y.get(15,TimeUnit.SECONDS);assertEquals(rx.getId(),ry.getId());assertNotEquals(rx.isDuplicate(),ry.isDuplicate());assertEquals(1,records.count(user,new QueryFilter()));}finally{executor.shutdownNow();}
    }
    @Test public void compositeSaveActuallyRollsBackRecordStateAndToken(){
        long a=work("롤백 검증","MOVIE");db.execute("ALTER TABLE user_work_state ADD CONSTRAINT integration_fail_state CHECK (status != 'COMPLETED')");
        try{try{records.submit(user,form(a,"2026-10-10","4.0","COMPLETE"));fail("Expected injected database failure");}catch(DataAccessException expected){}assertEquals(0,records.count(user,new QueryFilter()));assertNull(records.state(user,a));assertEquals(Integer.valueOf(0),db.queryForObject("SELECT COUNT(*) FROM record_submission",Integer.class));}finally{db.execute("ALTER TABLE user_work_state DROP CHECK integration_fail_state");}
    }
    @Test public void threeActionsAndEditsKeepStateIndependent(){
        long a=work("세 동작","TV");records.state(user,a,"WATCHING");long past=save(a,"2026-09-20","");assertEquals("WATCHING",records.state(user,a));
        records.submit(user,form(a,"2026-10-10","4.0","COMPLETE"));assertEquals("COMPLETED",records.state(user,a));records.state(user,a,"DROPPED");RecordForm update=form(a,"2026-09-21","5.0","PAST");records.update(user,past,update);records.delete(user,past);assertEquals("DROPPED",records.state(user,a));long count=records.count(user,new QueryFilter());records.state(user,a,"COMPLETED");assertEquals(count,records.count(user,new QueryFilter()));records.state(user,a,"");assertNull(records.state(user,a));assertEquals(count,records.count(user,new QueryFilter()));
    }
    @Test public void ownershipAppliesToOtherUserAndAdministrator() throws Exception {
        long a=work("비공개","MOVIE");long id=save(a,"2026-10-10","4.0");for(long actor:new long[]{other,admin}){invalid(()->records.get(actor,id),404);invalid(()->records.update(actor,id,form(a,"2026-10-11","3.0","PAST")),404);invalid(()->records.delete(actor,id),404);}
        mvc.perform(get("/records/"+id).with(user(accounts.loadUserByUsername("admin_user")))).andExpect(status().isNotFound());mvc.perform(post("/records/"+id+"/delete").with(user(accounts.loadUserByUsername("viewer_b"))).with(csrf())).andExpect(status().isNotFound());assertEquals(1,records.count(user,new QueryFilter()));
    }
    @Test public void csrfLoginGateAndPublicAdminPermissions() throws Exception {
        long a=work("권한 검사","MOVIE");mvc.perform(get("/records")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/works/"+a+"/state").param("status","COMPLETED").with(user(accounts.loadUserByUsername("viewer_a")))).andExpect(status().isForbidden());
        mvc.perform(post("/works/"+a+"/state").param("status","COMPLETED").with(user(accounts.loadUserByUsername("viewer_a"))).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/works/"+a+"/edit").with(user(accounts.loadUserByUsername("viewer_a")))).andExpect(status().isForbidden());mvc.perform(get("/admin/genres").with(user(accounts.loadUserByUsername("admin_user")))).andExpect(status().isOk());
    }
    @Test public void ratingBoundariesDatesAndUnicode(){
        long a=work("한글 😀","MOVIE");save(a,"2026-10-13","");save(a,"2026-10-13","0.5");save(a,"2026-10-13","5.0");for(String value:new String[]{"0","0.7","5.5","0.55","NaN"})invalid(()->records.submit(user,form(a,"2026-10-13",value,"PAST")),400);
        invalid(()->records.submit(user,form(a,"2026-10-14","4.0","PAST")),400);invalid(()->records.submit(user,form(a,"2026-02-30","4.0","PAST")),400);
        RecordForm f=form(a,"2026-10-13","4.5","PAST");f.setReview("한글과 이모지 😀 <script>alert(1)</script>");long id=records.submit(user,f).getId();assertEquals(f.getReview(),records.get(user,id).get("review"));
        f.setReview(String.join("",Collections.nCopies(4001,"가")));invalid(()->records.submit(user,f),400);
    }
    @Test public void databaseConstraintsAndReferencedWorkDeletion(){
        long a=work("삭제 보호","MOVIE",drama);long id=save(a,"2026-10-13","4.0");
        try{db.update("INSERT INTO viewing_record(user_id,work_id,viewed_on,rating) VALUES(?,?,?,?)",user,a,"2026-10-13",new BigDecimal("0.7"));fail("Expected database rating check");}catch(DataAccessException expected){}
        invalid(()->works.delete(a,true),409);invalid(()->works.update(a,new WorkForm(),false),403);invalid(()->works.deleteGenre(drama,true),409);records.delete(user,id);assertNotNull(works.get(a));records.state(user,a,"WANT");invalid(()->works.delete(a,true),409);records.state(user,a,"");works.delete(a,true);invalid(()->works.get(a),404);
    }
    @Test public void paginationUsesStableTiesAndNoGenreDuplicates(){
        long a=work("페이지 작품","MOVIE",drama,sf);for(int i=0;i<31;i++)save(a,"2026-10-10","4.0");db.update("UPDATE viewing_record SET created_at='2026-10-10 10:00:00.000000'");QueryFilter q=october();q.setGenreId(drama);assertEquals(31,records.count(user,q));Set<Long> ids=new HashSet<Long>();long previous=Long.MAX_VALUE;
        for(int page=1;page<=3;page++){q.setPage(page);List<Map<String,Object>> rows=records.list(user,q);assertEquals(page<3?15:1,rows.size());for(Map<String,Object> row:rows){long id=number(row,"id");assertTrue(id<previous);previous=id;assertTrue(ids.add(id));}}assertEquals(31,ids.size());
    }
    @Test public void periodIncludesBothBoundaryDates(){long a=work("경계일","MOVIE");save(a,"2026-09-30","");save(a,"2026-10-01","");save(a,"2026-10-13","");QueryFilter q=new QueryFilter();q.setFrom("2026-10-01");q.setTo("2026-10-13");assertEquals(2,records.count(user,q));q.setFrom("2026-10-14");invalid(()->records.count(user,q),400);}
    @Test public void backfillDateEditAndDeletionRecalculateRewatch(){long a=work("이력 재계산","MOVIE");long first=save(a,"2026-10-10","4.5");save(a,"2026-10-12","");assertEquals(1,number(summary(stats.report(user,october())),"rewatches"));long past=save(a,"2026-09-20","5.0");assertEquals(2,number(summary(stats.report(user,october())),"rewatches"));records.update(user,first,form(a,"2026-09-01","4.5","PAST"));assertEquals(1,number(summary(stats.report(user,october())),"recordCount"));assertNull(genre(stats.report(user,october()),"드라마").get("averageRating"));records.delete(user,past);assertEquals(1,number(summary(stats.report(user,october())),"rewatches"));}
    @Test public void fixedOwnerWorkAndOptimisticEdit(){long a=work("고정 작품","MOVIE"),b=work("다른 작품","TV");long id=save(a,"2026-10-10","4.0");RecordForm f=form(b,"2026-10-11","3.0","PAST");records.update(user,id,f);assertEquals(a,number(records.get(user,id),"workId"));invalid(()->records.update(user,id,f),409);}
    @Test public void sqlInputsAreBoundAndSortIsAllowlisted(){long a=work("정상 제목","MOVIE");save(a,"2026-10-10","");QueryFilter q=new QueryFilter();q.setKeyword("' OR 1=1 --");assertEquals(0,records.count(user,q));q.setSort("id;DELETE FROM app_user");invalid(()->records.list(user,q),400);assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM app_user",Integer.class).intValue());}
    @Test public void signupValidationAndUserRole(){SignupForm f=new SignupForm();f.setUsername("new_user");f.setName("새 회원");f.setEmail("new@example.invalid");f.setPassword("abcdefgh");invalid(()->accounts.register(f),400);f.setPassword(UUID.randomUUID().toString()+"!9");long id=accounts.register(f);assertEquals("USER",db.queryForObject("SELECT role FROM app_user WHERE id=?",String.class,id));assertTrue(db.queryForObject("SELECT password_hash FROM app_user WHERE id=?",String.class,id).startsWith("$2a$"));invalid(()->accounts.register(f),400);f.setUsername("another_user");f.setEmail("bad-address");invalid(()->accounts.register(f),400);}
    @Test public void externalMovieAndTvIdsAreDistinctAndManualDuplicateIsConfirmed(){
        db.update("INSERT INTO work(title,media_type,source,external_id,created_by) VALUES('외부 영화','MOVIE','TMDB',42,?),('외부 TV','TV','TMDB',42,?)",user,user);
        try{db.update("INSERT INTO work(title,media_type,source,external_id,created_by) VALUES('중복','MOVIE','TMDB',42,?)",user);fail("Expected unique source/type/id");}catch(DataAccessException expected){}
        work("수동 작품","MOVIE");WorkForm f=new WorkForm();f.setTitle("수동 작품");f.setMediaType("MOVIE");invalid(()->works.create(user,f),400);assertEquals(1,works.duplicates(f).size());f.setConfirmDuplicate(true);works.create(user,f);assertEquals(2,works.duplicates(f).size());
    }
    @Test public void emptyStatisticsUseZeroAndNull(){Map<String,Object> r=stats.report(user,october());assertEquals(0,number(summary(r),"recordCount"));assertEquals(0,number(summary(r),"rewatches"));assertNull(summary(r).get("averageRating"));assertNull(genre(r,"드라마").get("averageRating"));assertEquals(0,number(genre(r,"드라마"),"sampleSize"));}

    @Test public void realMapperPercentageBoundaries() {
        long a=work("평점 비율 경계","MOVIE",drama,sf);
        int[][] cases={{0,0},{0,4},{4,4},{1,4},{2,4},{2,3},{3,4},{3,17}};
        String[] expected={null,"0.0","100.0","25.0","50.0","66.7","75.0","17.6"};
        for(int i=0;i<cases.length;i++) {
            db.update("DELETE FROM record_submission");db.update("DELETE FROM viewing_record");
            for(int n=0;n<cases[i][1];n++)save(a,"2026-10-10",n<cases[i][0]?"4.0":"");
            Map<String,Object> s=summary(stats.report(user,october()));
            assertEquals(cases[i][1],number(s,"recordCount"));assertEquals(cases[i][0],number(s,"ratedCount"));
            assertEquals(expected[i]==null?null:new BigDecimal(expected[i]),s.get("ratedPercent"));
        }
    }
    @Test public void invalidStatisticsKeepFormAndDoNotExposeAReport() throws Exception {
        for(String[] dates:new String[][]{{"2026-10-13","2026-10-01"},{"2026-02-30","2026-10-13"}})
            mvc.perform(get("/stats").param("from",dates[0]).param("to",dates[1]).with(user(accounts.loadUserByUsername("viewer_a"))))
                .andExpect(status().isBadRequest()).andExpect(view().name("stats")).andExpect(model().attributeExists("filter","genres","pageTitle","message")).andExpect(model().attributeDoesNotExist("report"));
        mvc.perform(get("/stats").param("from","2026-10-01").param("to","2026-10-13").with(user(accounts.loadUserByUsername("viewer_a")))).andExpect(status().isOk()).andExpect(model().attributeExists("report"));
    }
    @Test public void outOfRangePagesClampWithFiltersAndShrink() throws Exception {
        long a=work("페이지 경계","MOVIE",drama,sf);for(int n=0;n<16;n++)save(a,"2026-10-10","4.0");
        QueryFilter q=october();q.setKeyword("페이지");q.setMediaType("MOVIE");q.setGenreId(drama);q.setSort("oldest");q.setPage(999);
        assertEquals(1,records.list(user,q).size());assertEquals(2,q.getPage());
        long id=number(records.list(user,q).get(0),"id");records.delete(user,id);assertEquals(15,records.list(user,q).size());assertEquals(1,q.getPage());
        for(String page:new String[]{"0","-1","abc","999999999999999999999"})
            mvc.perform(get("/records").param("page",page).with(user(accounts.loadUserByUsername("viewer_a")))).andExpect(status().isOk()).andExpect(view().name("records"));
    }
    @Test public void conflictPreservesDraftAndProvidesOwnedLatestRecord() throws Exception {
        long a=work("衝突 확인","MOVIE"),id=save(a,"2026-10-10","4.0");
        RecordForm first=form(a,"2026-10-11","5.0","PAST");first.setReview("먼저 저장한 값");records.update(user,id,first);
        mvc.perform(post("/records/"+id).param("viewedOn","2026-10-10").param("rating","3.0").param("review","오래된 초안 <script>").param("version","0").with(user(accounts.loadUserByUsername("viewer_a"))).with(csrf()))
            .andExpect(status().isConflict()).andExpect(view().name("record-form")).andExpect(model().attribute("conflict",true)).andExpect(model().attributeExists("latestRecord","recordForm"));
        assertEquals("먼저 저장한 값",records.get(user,id).get("review"));assertEquals(new BigDecimal("5.0"),records.get(user,id).get("rating"));
    }
    @Test public void historyContextSkipsNullsAndSeparatesMemberWorkAndPeriod() throws Exception {
        long a=work("이력 비교", "TV",drama,sf),b=work("다른 작품", "MOVIE",sf);
        long unrated=save(a,"2026-09-19",""),first=save(a,"2026-09-20","2.5");
        save(a,"2026-10-01","");long second=save(a,"2026-10-10","4.0"),same=save(a,"2026-10-12","4.0"),half=save(a,"2026-10-12","0.5");
        save(a,"2026-10-13","");save(b,"2026-10-10","5.0");
        records.submit(other,form(a,"2026-10-11","5.0","PAST"));records.state(other,a,"COMPLETED");records.state(user,a,"WATCHING");
        db.update("UPDATE viewing_record SET created_at='2026-10-13 12:00:00' WHERE work_id=?",a);
        assertNull(records.get(user,unrated).get("ratingChange"));assertNull(records.get(user,first).get("previousRating"));
        assertEquals(new BigDecimal("1.5"),records.get(user,second).get("ratingChange"));
        assertEquals(new BigDecimal("0.0"),records.get(user,same).get("ratingChange"));
        assertEquals(Integer.valueOf(0),records.get(user,same).get("ratingChangeDirection"));
        assertEquals(new BigDecimal("4.0"),records.get(user,half).get("previousRating"));assertEquals(new BigDecimal("-3.5"),records.get(user,half).get("ratingChange"));
        QueryFilter q=october();q.setWorkId(a);q.setFrom("2026-10-12");q.setTo("2026-10-12");q.setSort("oldest");q.setGenreId(sf);
        List<Map<String,Object>> rows=records.list(user,q);assertEquals(2,rows.size());assertEquals(5,number(rows.get(0),"viewingOrder"));assertEquals(6,number(rows.get(1),"viewingOrder"));assertEquals(new BigDecimal("4.0"),rows.get(0).get("previousRating"));
        Map<String,Object> context=records.workSummary(user,a);assertEquals(7,number(context,"recordCount"));assertEquals("WATCHING",context.get("currentState"));assertEquals(new BigDecimal("0.5"),context.get("latestRating"));
        assertEquals("2026-10-13",context.get("lastViewedOn").toString());assertEquals("2026-10-12",context.get("latestRatingOn").toString());
        assertEquals(1,number(records.workSummary(other,a),"recordCount"));assertTrue(records.workSummaries(user,Collections.<Long>emptyList()).isEmpty());
        assertEquals(2,number(summary(stats.report(user,q)),"recordCount"));
        for(String path:new String[]{"/works/"+a,"/records/new?workId="+a,"/records/"+half,"/records/"+half+"/edit"})
            mvc.perform(get(path).with(user(accounts.loadUserByUsername("viewer_a")))).andExpect(status().isOk()).andExpect(model().attributeExists("historySummary"));
        invalid(()->records.submit(user,form(a,"2026-10-12","0","PAST")),400);
    }
    @Test public void historyComparisonRecalculatesAfterBackfillDateRatingAndDeletion() {
        long a=work("이력 재계산","MOVIE");long first=save(a,"2026-10-10","4.0"),last=save(a,"2026-10-12","5.0");
        long backfill=save(a,"2026-09-20","2.0");assertEquals(2,number(records.get(user,first),"viewingOrder"));assertEquals(new BigDecimal("2.0"),records.get(user,first).get("ratingChange"));
        records.update(user,last,form(a,"2026-09-01","5.0","PAST"));assertEquals(1,number(records.get(user,last),"viewingOrder"));assertNull(records.get(user,last).get("previousRating"));assertEquals(new BigDecimal("-3.0"),records.get(user,backfill).get("ratingChange"));
        records.update(user,backfill,form(a,"2026-09-20","","PAST"));assertEquals(new BigDecimal("5.0"),records.get(user,first).get("previousRating"));assertEquals(new BigDecimal("-1.0"),records.get(user,first).get("ratingChange"));
        records.delete(user,last);assertEquals(2,number(records.get(user,first),"viewingOrder"));assertNull(records.get(user,first).get("previousRating"));assertNull(records.get(user,first).get("ratingChange"));
        assertEquals(2,number(records.workSummary(user,a),"recordCount"));assertEquals(new BigDecimal("4.0"),records.workSummary(user,a).get("latestRating"));records.delete(user,first);
        assertNull(records.workSummary(user,a).get("latestRating"));assertEquals(1,number(records.workSummary(user,a),"recordCount"));
    }
    @Test public void historyComparisonUsesWholeChronologyAcrossPagesAndStableTies() {
        long a=work("이력 페이지","MOVIE",drama,sf);long last=0;
        for(int n=0;n<16;n++)last=save(a,"2026-10-10",n%2==0?"3.0":"4.0");
        db.update("UPDATE viewing_record SET created_at='2026-10-13 12:00:00' WHERE work_id=?",a);
        QueryFilter q=october();q.setWorkId(a);q.setSort("oldest");q.setPage(2);q.setGenreId(drama);
        Map<String,Object> row=records.list(user,q).get(0);assertEquals(last,number(row,"id"));assertEquals(16,number(row,"viewingOrder"));assertEquals(new BigDecimal("3.0"),row.get("previousRating"));assertEquals(new BigDecimal("1.0"),row.get("ratingChange"));assertEquals(16,number(records.workSummary(user,a),"recordCount"));
        q.setSort("rating");q.setPage(1);for(Map<String,Object> item:records.list(user,q))assertEquals(records.get(user,number(item,"id")).get("ratingChange"),item.get("ratingChange"));
    }
    @Test public void failedLoginStoresOnlyUsernameAndSignupRedirectHasOnlyIntentionalFlag() throws Exception {
        for(String username:new String[]{"viewer_a","missing_account"}) {
            org.springframework.test.web.servlet.MvcResult result=mvc.perform(post("/login").param("username",username).param("password","wrong-secret").with(csrf())).andExpect(status().is3xxRedirection()).andReturn();
            org.springframework.mock.web.MockHttpSession session=(org.springframework.mock.web.MockHttpSession)result.getRequest().getSession();
            assertEquals(username,session.getAttribute("loginUsername"));assertNull(session.getAttribute("SPRING_SECURITY_LAST_EXCEPTION"));
            mvc.perform(get("/login").session(session)).andExpect(model().attribute("loginUsername",username));assertNull(session.getAttribute("loginUsername"));
        }
        String location=mvc.perform(post("/signup").param("username","redirect_check").param("password",UUID.randomUUID().toString()+"!9").param("name","시험 회원").param("email","redirect@example.invalid").with(csrf())).andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertTrue(location.startsWith("/login?registered"));assertFalse(location.contains("today"));assertFalse(location.contains("ratingOptions"));assertFalse(location.contains("stateLabels"));
    }
}
