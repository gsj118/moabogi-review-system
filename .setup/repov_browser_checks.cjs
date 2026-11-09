const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { execFileSync } = require('child_process');
const { chromium } = require('playwright');
const project = path.resolve(__dirname, '..');
const env = JSON.parse(fs.readFileSync(path.join(__dirname, 'environment.json'), 'utf8'));
const tools = JSON.parse(fs.readFileSync(path.join(env.tools, 'database-tools.json'), 'utf8'));
const mysql = path.join(tools.mysql.path, 'bin', 'mysql.exe');
const report = { browser: '', cases: [], input_error_render_ms: [] };
const executionDate = new Intl.DateTimeFormat('en-CA', {timeZone:'Asia/Seoul'}).format(new Date());
const output = path.resolve(process.env.MOA_EVIDENCE_DIR || path.join(project, 'review-system', 'docs', 'evidence', 'repov-20261016'));
fs.mkdirSync(output, {recursive:true});
function check(name, condition) { if (!condition) throw new Error('Failed: ' + name); report.cases.push({name, passed:true}); }
function sql(statement) { return execFileSync(mysql, ['--defaults-extra-file='+path.join(env.tools,'config','mysql-admin.cnf'),'--database=review_system_test','--batch','--skip-column-names','--default-character-set=utf8mb4'], {input:statement,encoding:'utf8',windowsHide:true}).trim(); }
function properties(file) { return Object.fromEntries(fs.readFileSync(file,'utf8').trim().split(/\r?\n/).filter(l=>l.includes('=')).map(l=>[l.slice(0,l.indexOf('=')),l.slice(l.indexOf('=')+1)])); }
async function csrf(page) { return page.locator('input[name="_csrf"]').first().inputValue(); }
async function login(page, username, password) { await page.goto('http://localhost:8082/login'); await page.locator('#username').fill(username); await page.locator('#password').fill(password); await Promise.all([page.waitForURL('http://localhost:8082/'), page.getByRole('button',{name:'로그인',exact:true}).click()]); }
async function register(page, account) { await page.goto('http://localhost:8082/signup'); await page.locator('#username').fill(account.username); await page.locator('#password').fill(account.password); await page.locator('#name').fill(account.name); await page.locator('#email').fill(account.username+'@example.invalid'); await Promise.all([page.waitForNavigation({waitUntil:'load'}), page.getByRole('button',{name:'회원가입',exact:true}).click()]); if(!new URL(page.url()).searchParams.has('registered'))throw new Error('Signup did not succeed: '+page.url()+' '+await page.locator('body').innerText()); }
async function saveRecord(page, workId, rating, action) {
  await page.goto('http://localhost:8082/records/new?workId='+workId+'&action='+action);
  await page.locator('#viewedOn').fill('2026-10-10'); await page.locator('#rating').selectOption(rating);
  await page.locator('#review').fill('<script>alert("unsafe")</script> 한글 감상 😀');
  const payload={workId:String(workId),viewedOn:'2026-10-10',rating,review:'<script>alert("unsafe")</script> 한글 감상 😀',action,token:await page.locator('input[name=token]').inputValue(),_csrf:await csrf(page)};
  await Promise.all([page.waitForURL(/\/records\/\d+$/),page.getByRole('button',{name:'감상 기록 저장',exact:true}).click()]);
  return {id:Number(page.url().split('/').pop()),payload};
}
(async()=>{
  const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'});
  report.browser=browser.version();
  const context=await browser.newContext({viewport:{width:1440,height:1000},locale:'ko-KR',timezoneId:'Asia/Seoul'});
  const page=await context.newPage();page.setDefaultTimeout(30000);
  let dialogs=0;page.on('dialog',async dialog=>{dialogs++;await dialog.dismiss();});
  const suffix=crypto.randomBytes(5).toString('hex');
  const a={username:'qa_a_'+suffix,password:crypto.randomBytes(20).toString('hex')+'!9',name:'<script>unsafe</script>'};
  const b={username:'qa_b_'+suffix,password:crypto.randomBytes(20).toString('hex')+'!9',name:'다른 회원'};
  let otherContext;
  try {
    await register(page,a);check('signup redirects to login',new URL(page.url()).searchParams.has('registered'));
    const before=(await context.cookies()).find(c=>c.name==='JSESSIONID').value;
    await page.locator('#username').fill(a.username);await page.locator('#password').fill(a.password);
    await Promise.all([page.waitForURL('http://localhost:8082/'),page.getByRole('button',{name:'로그인',exact:true}).click()]);
    const after=(await context.cookies()).find(c=>c.name==='JSESSIONID');
    check('login rotates the session identifier',before!==after.value);check('HttpOnly and SameSite=Lax on local HTTP',after.httpOnly&&after.sameSite==='Lax'&&!after.secure);
    check('member name is escaped',await page.locator('.account strong').innerText()===a.name && (await page.content()).includes('&lt;script&gt;unsafe&lt;/script&gt;'));
    await page.goto('http://localhost:8082/works/new');await page.locator('#title').fill('화면 검증 작품 '+suffix);await page.locator('#releaseYear').fill('2026');await page.getByLabel('드라마',{exact:true}).check();await page.getByLabel('SF',{exact:true}).check();
    await Promise.all([page.waitForURL(/\/works\/\d+$/),page.getByRole('button',{name:'작품 등록',exact:true}).click()]);const workId=Number(page.url().split('/').pop());check('manual work and multiple genres registered',workId>0);
    await page.locator('#status').selectOption('WATCHING');await Promise.all([page.waitForNavigation({waitUntil:'load'}),page.getByRole('button',{name:'상태 변경',exact:true}).click()]);
    const first=await saveRecord(page,workId,'4.5','PAST');check('JSP review output is escaped',await page.locator('.review-text').innerText()==='<script>alert("unsafe")</script> 한글 감상 😀');
    const replay=await context.request.post('http://localhost:8082/records',{form:first.payload});check('replay returns original record',replay.status()===200&&replay.url().endsWith('/records/'+first.id));
    await page.goto('http://localhost:8082/works/'+workId);check('past record preserves current state',(await page.locator('p').filter({hasText:'나의 현재 상태'}).innerText()).includes('시청 중'));
    const second=await saveRecord(page,workId,'','COMPLETE');check('same work/date separate submissions are allowed',second.id!==first.id);
    await page.goto('http://localhost:8082/works/'+workId);check('complete action sets current state',(await page.locator('p').filter({hasText:'나의 현재 상태'}).innerText()).includes('완료'));
    const stateOnly=await context.request.post('http://localhost:8082/works/'+workId+'/state',{form:{status:'COMPLETED',_csrf:await csrf(page)}});check('state-only completion does not create record',stateOnly.status()===200&&Number(sql("SELECT COUNT(*) FROM viewing_record WHERE work_id="+workId))===2);
    await page.goto('http://localhost:8082/stats?from=2026-10-01&to=2026-10-31');check('statistics JSP renders latest non-null rating',(await page.locator('.genre-rating').filter({hasText:'드라마'}).innerText()).includes('4.50'));
    check('valid rating percentage reaches actual JSP', await page.locator('#rated-percent').innerText()==='50%');
    const denied=await context.request.post('http://localhost:8082/works/'+workId+'/state',{form:{status:'DROPPED'}});check('CSRF rejects unauthenticated change token',denied.status()===403);
    const adminDenied=await context.request.get('http://localhost:8082/admin/works/'+workId+'/edit');check('creator receives no automatic admin privilege',adminDenied.status()===403);
    await page.goto('http://localhost:8082/records/new?workId='+workId);
    const serverToday=await page.locator('#viewedOn').getAttribute('max');
    const futureDate=new Date(Date.parse(serverToday+'T00:00:00Z')+86400000).toISOString().slice(0,10);
    for(let n=0;n<3;n++){
      await page.locator('#viewedOn').fill(futureDate);const start=Date.now();
      await Promise.all([page.waitForNavigation({waitUntil:'load'}),page.getByRole('button',{name:'감상 기록 저장',exact:true}).click()]);
      await page.locator('.notice.error').waitFor();const visible=await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(()=>resolve(Date.now())))));
      report.input_error_render_ms.push(visible-start);check('future date error render '+(n+1)+' within 3 seconds',visible-start<=3000);
      check('error preserves viewing date input '+(n+1),await page.locator('#viewedOn').inputValue()===futureDate);
    }
    await page.screenshot({path:path.join(output,'input-error-desktop.png'),fullPage:true});
    otherContext=await browser.newContext({locale:'ko-KR'});const otherPage=await otherContext.newPage();await register(otherPage,b);await login(otherPage,b.username,b.password);
    check('other member cannot view personal record',(await otherContext.request.get('http://localhost:8082/records/'+first.id)).status()===404);
    await otherPage.goto('http://localhost:8082/works/'+workId);check('other member cannot delete personal record',(await otherContext.request.post('http://localhost:8082/records/'+first.id+'/delete',{form:{_csrf:await csrf(otherPage)}})).status()===404);
    check('new accounts always have USER role',sql("SELECT role FROM app_user WHERE username='"+b.username+"'")==='USER');
    sql("UPDATE app_user SET role='ADMIN' WHERE username='"+b.username+"'");await Promise.all([otherPage.waitForNavigation({waitUntil:'load'}),otherPage.getByRole('button',{name:'로그아웃',exact:true}).click()]);await login(otherPage,b.username,b.password);
    check('administrator still cannot view another private record',(await otherContext.request.get('http://localhost:8082/records/'+first.id)).status()===404);
    await otherPage.goto('http://localhost:8082/admin/genres');check('administrator genre page renders',await otherPage.locator('#newGenre').count()===1);
    await otherPage.goto('http://localhost:8082/admin/works/'+workId+'/edit');check('administrator shared work editor renders',await otherPage.locator('#title').inputValue()==='화면 검증 작품 '+suffix);
    const blocked=await otherContext.request.post('http://localhost:8082/admin/works/'+workId+'/delete',{form:{_csrf:await csrf(otherPage)}});check('referenced work deletion is blocked',blocked.status()===409);
    await page.goto('http://localhost:8082/records/'+first.id+'/edit');await page.locator('#rating').selectOption('3.0');await page.locator('#review').fill('수정한 감상평 😀');await Promise.all([page.waitForURL('http://localhost:8082/records/'+first.id),page.getByRole('button',{name:'수정 저장',exact:true}).click()]);check('record edit and UTF-8 persist',await page.locator('.review-text').innerText()==='수정한 감상평 😀');
    await page.goto('http://localhost:8082/records/'+second.id+'/delete');check('delete confirmation is a read-only GET',Number(sql('SELECT COUNT(*) FROM viewing_record WHERE work_id='+workId))===2);await Promise.all([page.waitForURL('http://localhost:8082/records'),page.getByRole('button',{name:'이 기록 삭제',exact:true}).click()]);check('record deletion keeps shared work and state',Number(sql('SELECT COUNT(*) FROM work WHERE id='+workId))===1&&sql('SELECT status FROM user_work_state WHERE work_id='+workId)==='COMPLETED');
    await Promise.all([page.waitForNavigation({waitUntil:'load'}),page.getByRole('button',{name:'로그아웃',exact:true}).click()]);await page.goto('http://localhost:8082/records');check('logout blocks protected screens',page.url().includes('/login'));
    check('malicious content never executes',dialogs===0);
    await login(page,a.username,a.password);
    await improvements(page,context,workId,first.id);
    report.count=report.cases.length;report.passed=true;fs.writeFileSync(path.join(output,'browser-flow.json'),JSON.stringify(report,null,2));console.log(JSON.stringify({browser:report.browser,checks:report.count,input_error_render_ms:report.input_error_render_ms,passed:true}));
  } finally {
    // Remove only the two temporary QA users and the shared works they created.
    const ids=sql("SELECT GROUP_CONCAT(id) FROM app_user WHERE username IN ('"+a.username+"','"+b.username+"')");
    if(ids&&/^\d+(,\d+)*$/.test(ids))sql('DELETE FROM record_submission WHERE user_id IN ('+ids+');DELETE FROM user_work_state WHERE user_id IN ('+ids+');DELETE FROM viewing_record WHERE user_id IN ('+ids+');DELETE FROM work WHERE created_by IN ('+ids+');DELETE FROM app_user WHERE id IN ('+ids+');');
    if(otherContext)await otherContext.close();await context.close();await browser.close();
  }
})().catch(error=>{fs.writeFileSync(path.join(output,'browser-failure.json'),JSON.stringify({passed:false,error:error.message,cases:report.cases},null,2));console.error(error.message);process.exitCode=1;});
async function improvements(page,context,workId,firstId) {
 const base='http://localhost:8082';
 const fixture=JSON.parse(fs.readFileSync(path.join(output,'fixture.json'),'utf8'));
 const fixtureAccount=JSON.parse(fs.readFileSync(path.join(env.tools,'config','repov-browser-account.json'),'utf8'));
 const fixtureContext=await page.context().browser().newContext({viewport:{width:1440,height:1000},locale:'ko-KR',timezoneId:'Asia/Seoul'});const q=await fixtureContext.newPage();
 try {
  await login(q,fixtureAccount.username,fixtureAccount.password);
  for (const c of fixture.cases) {await q.goto(base+'/stats?from='+c.date+'&to='+c.date);check('F01 actual JSP ratio '+c.expected,await q.locator('#rated-percent').innerText()===c.expected);}
  await q.goto(base+'/stats?from=2026-10-13&to=2026-10-01');check('F02 invalid interval keeps form and no metrics',await q.locator('#from').inputValue()==='2026-10-13'&&await q.locator('.metric').count()===0);
  await q.locator('#from').fill('2026-01-07');await q.locator('#to').fill('2026-01-07');await Promise.all([q.waitForNavigation(),q.getByRole('button',{name:'조회하기',exact:true}).click()]);check('F02 recovery displays75%',await q.locator('#rated-percent').innerText()==='75%');
  await q.goto(base+'/records?workId='+fixture.page_work_id+'&sort=oldest&page=999');check('F04 page clamp with16records',await q.locator('.pagination').innerText().then(t=>t.includes('2 / 2'))&&await q.locator('.record-table tbody tr').count()===1);
  check('comparison on second page sees earlier rating',await q.locator('.rating-change').innerText()==='이전 유효 평점과 같음');
  const draft=await fixtureContext.newPage();await draft.goto(base+'/records/'+fixture.conflict_record_id+'/edit');await q.goto(base+'/records/'+fixture.conflict_record_id+'/edit');await q.locator('#rating').selectOption('5.0');await Promise.all([q.waitForNavigation(),q.getByRole('button',{name:'수정 저장',exact:true}).click()]);await draft.locator('#review').fill('보존할 초안');const conflictResponse=await Promise.all([draft.waitForNavigation(),draft.getByRole('button',{name:'수정 저장',exact:true}).click()]);check('F03 actual409 preserves draft withoutsave',conflictResponse[0].status()===409&&await draft.locator('#review').inputValue()==='보존할 초안'&&await draft.getByRole('button',{name:'수정 저장',exact:true}).count()===0);await draft.screenshot({path:path.join(output,'after-conflict.png'),fullPage:true});await draft.close();
 } finally {await fixtureContext.close();}
 await page.goto(base+'/works?keyword='+encodeURIComponent('화면 검증 작품'));const card=page.locator('.work-card').filter({hasText:'화면 검증 작품'});check('search cards carry private counts/state',await card.locator('.work-personal').innerText().then(t=>t.includes('완료')&&t.includes('1건')));
 await Promise.all([page.waitForURL(/\/records\/new/),card.getByRole('link',{name:/감상 남기기/}).click()]);check('search direct entry uses COMPLETE and shows summary',await page.locator('input[name=action][value=COMPLETE]').isChecked()&&(await page.locator('.history-summary').innerText()).includes('1건'));
 await page.getByRole('button',{name:'어제',exact:true}).click();const today=await page.locator('#viewedOn').getAttribute('max');check('yesterday shortcut uses server calendar',await page.locator('#viewedOn').inputValue()===new Date(Date.parse(today+'T12:00:00Z')-86400000).toISOString().slice(0,10));await page.getByRole('button',{name:'오늘',exact:true}).click();check('today shortcut uses server calendar',await page.locator('#viewedOn').inputValue()===today);
 await page.locator('input[name=action][value=PAST]').check();check('save effect explicitly keeps current state',(await page.locator('#save-effect').innerText()).includes('완료 유지'));
 await page.locator('#review').fill('한글😀');check('counter matches server UTF16 limit',await page.locator('#review-count').innerText()==='4 / 4,000자');
 async function add(date,rating,review){await page.goto(base+'/records/new?workId='+workId+'&action=PAST');await page.locator('#viewedOn').fill(date);await page.locator('#rating').selectOption(rating);await page.locator('#review').fill(review);await Promise.all([page.waitForURL(/\/records\/\d+$/),page.getByRole('button',{name:'감상 기록 저장',exact:true}).click()]);return Number(page.url().split('/').pop());}
 const past=await add('2026-09-20','4.0','과거의 생각');await add('2026-10-12','','미평점도 독립적인 감상');const latest=await add('2026-10-13','4.5','이번에는 달라진 생각');
 check('saved record skips intermediate null for ratingchange',(await page.locator('.rating-change').innerText())==='이전보다 +1.5점');check('saved detail distinguishes event and fullhistory',(await page.locator('.history-summary').innerText()).includes('4건'));
 await page.screenshot({path:path.join(output,'after-record-detail.png'),fullPage:true});
 await page.goto(base+'/records?workId='+workId+'&from=2026-10-13&to=2026-10-13');check('filtered record list retains fourth order and full comparison',await page.locator('.record-table').innerText().then(t=>t.includes('4번째')&&t.includes('+1.5점')));
 await page.goto(base+'/works/'+workId+'?from=2026-10-13&to=2026-10-13');check('work summary unaffected by period and preserves state',await page.locator('.history-summary').innerText().then(t=>t.includes('4건')&&t.includes('완료')));
 await page.goto(base+'/works/'+workId);await page.screenshot({path:path.join(output,'after-work-test-desktop.png'),fullPage:true});
 for(const width of [390,320]) {await page.setViewportSize({width,height:844});for(const [name,url] of [['work','/works/'+workId],['records','/records?workId='+workId],['form','/records/new?workId='+workId+'&action=PAST'],['edit','/records/'+latest+'/edit'],['stats','/stats?from=2026-10-01&to=2026-10-31'],['works','/works']]){await page.goto(base+url);check(name+' no page overflow at'+width,await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));if(['work','records','form','edit'].includes(name))await page.screenshot({path:path.join(output,'after-'+name+'-'+width+'.png'),fullPage:true});if(name==='records')check('mobile record rows fit container at'+width,await page.locator('.record-table').evaluate(e=>e.scrollWidth<=e.clientWidth+1));}}
 await page.setViewportSize({width:1440,height:1000});await page.goto(base+'/records?workId='+workId+'&from=2000-01-01&to=2000-01-01');check('filtered empty state gives reset and nextaction',await page.getByRole('link',{name:'조회 조건 초기화',exact:true}).count()>=1&&await page.getByRole('link',{name:'작품 찾기',exact:true}).count()>=1);
 await page.goto(base+'/works?mediaType=INVALID');check('F05 search error keeps form without fake empty',await page.locator('#keyword').count()===1&&await page.locator('.empty').count()===0);
 await page.goto(base+'/works?keyword='+encodeURIComponent('한글 "제목" < > & 공백'));await page.getByRole('link',{name:'＋ 작품 직접 등록',exact:true}).click();check('F06 safe search draft survives',await page.locator('#title').inputValue()==='한글 "제목" < > & 공백');
 for(const url of ['/','/works/'+workId,'/records','/records/'+latest,'/records/'+latest+'/edit','/states','/stats']){const response=await page.goto(base+url);check('rendered JSP route '+url,response.status()===200);}
 report.newHistory={workId,firstId,pastId:past,latestId:latest,totalRecords:4,expectedDelta:'+1.5',sameDaySeparateEvents:true};
}
