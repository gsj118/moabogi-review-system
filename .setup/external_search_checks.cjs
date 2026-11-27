'use strict';
// Only the isolated Tomcat gets this transient model; exercise the deployed JSP and its CSP.
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict');
const {chromium} = require('playwright');
const json = name => JSON.parse(fs.readFileSync(name, 'utf8'));
const out = path.resolve(process.env.MOA_EVIDENCE_DIR);
const {tools} = json(path.join(__dirname, 'environment.json'));
const account = json(path.join(tools, 'config/repov-browser-account.json'));
const base = 'http://localhost:8082', title = '저녁 & <장면>';
const results = {source:'synthetic server models and intercepted poster responses; no TMDB API or import', checks:[]};
function check(name, pass) { assert.ok(pass, name); results.checks.push(name); }
async function run() {
  assert.equal(json(path.join(out, 'fixture.json')).database, 'review_system_test');
  assert.match(account.username, /^repov_[0-9a-f]+_ui$/);
  const root = path.join(tools, 'test-tomcat/webapps/ROOT');
  const view = 'WEB-INF/views/search-external.jsp';
  assert.deepEqual(fs.readFileSync(path.join(root,view)), fs.readFileSync(path.join(__dirname,'../review-system/src/main/webapp',view)));
  const browser = await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe'});
  const dir = fs.mkdtempSync(path.join(root, 'pm-tmdb-'));
  try {
    fs.writeFileSync(path.join(dir,'model.jsp'), `<%@ page pageEncoding="UTF-8" import="java.util.*" %><%
      if (!java.net.InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress() ||
          !"${account.username}".equals(request.getRemoteUser())) { response.sendError(404); return; }
      String scenario=request.getParameter("scenario");
      request.setAttribute("externalSearchEnabled",true);
      if ("results".equals(scenario)) {
        List<Map<String,Object>> rows=new ArrayList<>();
        String[] posters={"/ok.jpg",null,"/fail.jpg","https://untrusted.invalid/image.jpg"};
        String[] titles={"저녁의 <장면>","포스터가 없는 드라마","이미지가 만료된 영화","잘못된 이미지 주소"};
        for(int i=0;i<posters.length;i++) {
          Map<String,Object> row=new HashMap<>(); row.put("externalId",100+i); row.put("mediaType",i==1?"TV":"MOVIE");
          row.put("title",titles[i]); row.put("releaseYear",i==1?null:2026); row.put("posterPath",posters[i]);
          row.put("overview","각자의 하루가 끝난 뒤, 오래된 극장에서 다시 만난 두 사람의 이야기."); rows.add(row);
        }
        request.setAttribute("query",request.getParameter("query")); request.setAttribute("mediaType",request.getParameter("mediaType"));
        request.setAttribute("externalResults",rows);
      } else if (!"empty".equals(scenario)) {
        request.setAttribute("errorType",scenario); request.setAttribute("message","TEST_UPSTREAM_INTERNAL");
        response.setStatus("NOT_FOUND".equals(scenario)?404:500);
      }
    %><jsp:forward page="/WEB-INF/views/search-external.jsp"/>`,{flag:'wx'});
    const ctx=await browser.newContext({locale:'ko-KR',viewport:{width:1440,height:960}}), p=await ctx.newPage();
    results.browser=browser.version();
    const requests=[], errors=[], unexpected=[];
    await ctx.route('**/*', async route=>{
      const u=new URL(route.request().url());
      if(u.origin===base) return route.continue();
      if(u.origin==='https://image.tmdb.org') {
        requests.push(u.pathname);
        if(u.pathname==='/t/p/w185/ok.jpg') return route.fulfill({contentType:'image/svg+xml',body:
          '<svg xmlns="http://www.w3.org/2000/svg" width="185" height="278" viewBox="0 0 185 278"><rect width="185" height="278" fill="#1f344d"/><circle cx="129" cy="71" r="30" fill="#ffd799"/><path d="M0 165L61 104l76 70 48-43v147H0z" fill="#517686"/><path d="M0 211l100-72 85 56v83H0z" fill="#304d5b"/><text x="14" y="241" fill="white" font-family="sans-serif" font-size="20">저녁의 장면</text><text x="15" y="262" fill="white" font-family="sans-serif" font-size="12">시험용 포스터</text></svg>'});
        return route.fulfill({status:404,body:''});
      }
      unexpected.push(u.origin); return route.abort();
    });
    p.on('pageerror',err=>errors.push(err.message));
    await p.goto(base+'/login'); await p.locator('#username').fill(account.username); await p.locator('#password').fill(account.password);
    await Promise.all([p.waitForURL(base+'/'),p.getByRole('button',{name:'로그인',exact:true}).click()]);
    const params=new URLSearchParams({query:title,mediaType:'TV'}), local=base+'/works?'+new URLSearchParams({keyword:title,mediaType:'TV'});
    const go = scenario=>p.goto(base+'/'+path.basename(dir)+'/model.jsp?'+params+'&scenario='+scenario);
    const back=p.getByRole('link',{name:'등록된 작품 검색',exact:true});
    const draft=p.getByRole('link',{name:'작품 직접 등록',exact:true});
    await p.goto(local); await p.getByRole('link',{name:'TMDB 작품 검색',exact:true}).click();
    check('real disconnected page preserves title and type', new URL(p.url()).searchParams.get('query')===title && await p.getByText(/지금은 TMDB 검색/).isVisible() && !await p.locator('#external-query').count());
    await draft.click(); check('manual registration preserves escaped title',await p.locator('#title').inputValue()===title);
    const response=await go('results'); check('actual search JSP renders four supplied results',response.status()===200 && await p.locator('.tmdb-result').count()===4);
    const csp=response.headers()['content-security-policy'];
    check('CSP permits only approved external image origin and same-origin styles',/(?:^|;)\s*img-src 'self' data: https:\/\/image\.tmdb\.org\s*(?:;|$)/.test(csp) && /(?:^|;)\s*style-src 'self'\s*(?:;|$)/.test(csp));
    const cards=p.locator('.tmdb-result'), poster=i=>cards.nth(i).locator('.tmdb-poster');
    await poster(0).locator('img').waitFor({state:'visible'});
    check('loaded poster replaces placeholder; alt reflects escaped title', await poster(0).locator('img').getAttribute('alt')==='저녁의 <장면> 포스터' && !await poster(0).locator('span').isVisible());
    for(const [i,label] of [[1,'missing'],[2,'404'],[3,'untrusted URL']]) check(label+' uses visible placeholder',await poster(i).locator('span').isVisible() && !await poster(i).locator('img').isVisible());
    check('no unapproved poster requests',unexpected.length===0 && [...new Set(requests)].sort().join(',')==='/t/p/w185/fail.jpg,/t/p/w185/ok.jpg');
    check('movie and TV source URLs bind to supplied IDs',await cards.nth(0).getByRole('link').getAttribute('href')==='https://www.themoviedb.org/movie/100' && await cards.nth(1).getByRole('link').getAttribute('href')==='https://www.themoviedb.org/tv/101');
    check('unknown year and escaped result titles', (await cards.nth(1).innerText()).includes('연도 미상') && (await cards.nth(0).locator('h2').textContent())==='저녁의 <장면>');
    const form=cards.nth(1).locator('form');
    check('import form matches QA fields with CSRF (not submitted)',await form.getAttribute('action')==='/works/import-external' && await form.locator('[name="externalId"]').inputValue()==='101' && await form.locator('[name="mediaType"]').inputValue()==='TV' && await form.locator('[name="confirmDuplicate"]').inputValue()==='false' && !!await form.locator('[name="_csrf"]').inputValue());
    check('local logo loads and notice is readable',await p.locator('.tmdb-credit img').evaluate(e=>e.complete && e.naturalWidth>0 && new URL(e.src).origin===location.origin) && (await p.locator('.tmdb-credit').innerText()).includes('not endorsed, certified, or otherwise approved by TMDB'));
    for(const width of [1440,390]) {
      await p.setViewportSize({width,height:width===390?844:960});
      check('no horizontal overflow at '+width,await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
      await p.locator('main').screenshot({path:path.join(out,'tmdb-results-'+width+'.png')});
    }
    const conditions={empty:'검색 결과가 없습니다.',NOT_FOUND:'검색 결과가 없습니다.',RATE_LIMITED:'검색 요청이 많습니다.',TIMEOUT:'TMDB 응답 시간이 초과됐습니다.',SERVER_ERROR:'TMDB 서버가 응답하지 않습니다.',UNKNOWN:'TMDB 검색을 마치지 못했습니다.',NOT_CONFIGURED:'지금은 TMDB 검색을 이용할 수 없습니다.'};
    for(const [key,msg] of Object.entries(conditions)) {
      await go(key);
      const u=new URL(await back.getAttribute('href'),base),body=await p.locator('main').innerText();
      check(key+' explains outcome once and retains local search',body.includes(msg) && !body.includes('TEST_UPSTREAM_INTERNAL') && !await cards.count() && u.pathname==='/works' && u.searchParams.get('keyword')===title && u.searchParams.get('mediaType')==='TV');
    }
    await go('TIMEOUT'); await p.locator('main').screenshot({path:path.join(out,'tmdb-timeout-390.png')});
    let focused=false;
    for(let i=0;i<40&&!focused;i++){await p.keyboard.press('Tab'); focused=await back.evaluate(e=>e===document.activeElement);}
    check('keyboard can reach recovery link',focused);
    await Promise.all([p.waitForURL(u=>u.pathname==='/works'),p.keyboard.press('Enter')]);
    check('Enter restores filters on real local search',await p.locator('#keyword').inputValue()===title && await p.locator('#mediaType').inputValue()==='TV');
    check('no page JS errors or unexpected external requests',!errors.length && !unexpected.length);
    results.passed=true;
  } finally {
    await browser.close(); fs.rmSync(path.join(dir,'model.jsp'),{force:true}); fs.rmdirSync(dir);
    fs.writeFileSync(path.join(out,'tmdb-ui-results.json'),JSON.stringify(results,null,2));
  }
}
run().catch(e=>{console.error(e);process.exitCode=1;});
