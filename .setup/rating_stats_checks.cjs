'use strict';
// Run after repov_fixture.py seed against the isolated 8082/test database only.
const fs=require('fs'),path=require('path'),{execFileSync}=require('child_process'),{chromium}=require('playwright');
const out=path.resolve(process.env.MOA_EVIDENCE_DIR),env=JSON.parse(fs.readFileSync('.setup/environment.json'));
const fixture=JSON.parse(fs.readFileSync(path.join(out,'fixture.json'))),cfg=JSON.parse(fs.readFileSync(path.join(env.tools,'config/repov-browser-account.json')));
const dbtools=JSON.parse(fs.readFileSync(path.join(env.tools,'database-tools.json'))),base='http://localhost:8082';
const report={cases:[],errors:[],browser:''};
function check(name,ok){report.cases.push({name,passed:!!ok});if(!ok)throw Error(name);}
function sql(query){return execFileSync(path.join(dbtools.mysql.path,'bin/mysql.exe'),['--defaults-extra-file='+path.join(env.tools,'config/mysql-admin.cnf'),'--database=review_system_test','--batch','--skip-column-names','--default-character-set=utf8mb4'],{input:query,encoding:'utf8',windowsHide:true}).trim();}
const hex=s=>s?'CONVERT(0x'+Buffer.from(s).toString('hex')+' USING utf8mb4)':"''";
async function submit(p,name){const [r]=await Promise.all([p.waitForNavigation(),p.getByRole('button',{name,exact:true}).click()]);return r.status();}
async function assertField(p,id,value){
 check(id+' error keeps submitted value',await p.locator('#'+id).inputValue()===value);
 check(id+' error is associated with input',await p.locator('#'+id).getAttribute('aria-invalid')==='true'&&(await p.locator('#'+id).getAttribute('aria-describedby')).includes(id==='viewedOn'?'date-error':id+'-error'));
 check(id+' summary gets initial focus',await p.locator('.notice.error').evaluate(e=>e===document.activeElement));
 await p.locator('.error-field-link').focus();await p.locator('.error-field-link').press('Enter');
 check(id+' keyboard recovery focuses input',await p.locator('#'+id).evaluate(e=>e===document.activeElement));
}
(async()=>{
 check('fixture database is isolated',fixture.database==='review_system_test'&&sql('SELECT DATABASE()')==='review_system_test');
 const title=('긴한글<&" 작품 제목 ').repeat(16).slice(0,200),review=('오래전과 지금의 생각이 달라졌습니다.\n').repeat(55);
 const work=Number(sql('INSERT INTO work(title,media_type,created_by) VALUES('+hex(title)+",'MOVIE',"+fixture.user_ids[0]+');SELECT LAST_INSERT_ID()'));
 sql('INSERT INTO work_genre(work_id,genre_id) VALUES('+work+','+fixture.genre_id+')');
 function record(day,rating,text){return Number(sql('INSERT INTO viewing_record(user_id,work_id,viewed_on,rating,review) VALUES('+fixture.user_ids[0]+','+work+",'2026-02-0"+day+"',"+rating+','+hex(text)+');SELECT LAST_INSERT_ID()'));}
 const ids=[record(1,'NULL',''),record(2,'4.0',''),record(3,'NULL',''),record(4,'4.0',review),record(5,'5.0','이번 감상'),record(6,'NULL','')];
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||'C:/Program Files/Google/Chrome/Application/chrome.exe'});
 report.browser=browser.version();const context=await browser.newContext({viewport:{width:1440,height:1000},locale:'ko-KR',timezoneId:'Asia/Seoul'}),p=await context.newPage();
 p.on('pageerror',e=>report.errors.push(e.message));
 try{
 await p.goto(base+'/login');await p.locator('#username').fill(cfg.username);await p.locator('#password').fill(cfg.password);await Promise.all([p.waitForURL(base+'/'),p.getByRole('button',{name:'로그인',exact:true}).click()]);
 for(const [index,text] of [[0,'평점 비교 없음'],[1,'첫 유효 평점'],[2,'평점 비교 없음'],[3,'이전 유효 평점과 같음'],[4,'이전보다 +1.0점']]){
  await p.goto(base+'/records/'+ids[index]);check('detail comparison case '+index,(await p.locator('.record-value').innerText()).includes(text));
  if(index>=3)check('comparison includes actual previous rating '+index,(await p.locator('.record-value').innerText()).includes('이전 유효 평점 ★ 4.0'));
  if(index===0)check('first unrated empty review is explicit',(await p.locator('.review-text').innerText()).includes('남긴 감상평이 없습니다.')&&!(await p.locator('.record-meta').innerText()).includes('첫 유효 평점'));
  if(index===3)check('long Korean review is unchanged',await p.locator('.review-text').textContent()===review);
 }
 // A tampered select must remain visible after rejection, never silently become NULL.
 for(const value of ['0','0.7','5.5','문자"><script>']){
  await p.goto(base+'/records/new?workId='+work+'&action=PAST');await p.locator('#viewedOn').fill('2026-03-01');await p.locator('#review').fill('평점 오류 뒤에도 보존할 초안');
  await p.locator('#rating').evaluate((s,v)=>{s.add(new Option(v,v));s.value=v;},value);
  check('invalid rating rejected '+value,await submit(p,'감상 기록 저장')===400);await assertField(p,'rating',value);
  check('invalid rating keeps draft and action '+value,await p.locator('#review').inputValue()==='평점 오류 뒤에도 보존할 초안'&&await p.locator('[name=action][value=PAST]').isChecked());
  check('no injected script in select '+value,await p.locator('#rating script').count()===0);
 }
 await p.bringToFront();await p.screenshot({path:path.join(out,'after-rating-error-1440.png'),fullPage:true});
 await p.locator('#rating').selectOption('0.5');check('rating recovery saves lower boundary',await submit(p,'감상 기록 저장')===200&&/\/records\/\d+$/.test(p.url()));
 const recovered=Number(p.url().split('/').pop());check('recovered rating persisted exactly',sql('SELECT rating FROM viewing_record WHERE id='+recovered)==='0.5');
 await p.goto(base+'/records/new?workId='+work+'&action=PAST');await p.locator('#viewedOn').evaluate(e=>e.type='text');await p.locator('#viewedOn').fill('2026-02-30');await p.locator('#rating').selectOption('5.0');await p.locator('#review').fill('날짜 복구 초안');
 check('malformed date rejected',await submit(p,'감상 기록 저장')===400);await assertField(p,'viewedOn','2026-02-30');
 check('date error preserves rating and review',await p.locator('#rating').inputValue()==='5.0'&&await p.locator('#review').inputValue()==='날짜 복구 초안');
 await p.getByRole('button',{name:'오늘',exact:true}).click();check('date shortcut recovers invalid text',await p.locator('#viewedOn').inputValue()===await p.locator('#viewedOn').getAttribute('max'));
 check('date recovery saves upper boundary',await submit(p,'감상 기록 저장')===200);
 const dateRecovered=Number(p.url().split('/').pop());
 await p.goto(base+'/records/'+dateRecovered+'/edit');const overlong='가'.repeat(4001);await p.locator('#review').evaluate(e=>e.removeAttribute('maxlength'));await p.locator('#review').fill(overlong);
 check('overlong review rejected',await submit(p,'수정 저장')===400);await assertField(p,'review',overlong);
 await p.locator('#review').fill('가'.repeat(4000));check('review recovery saves maximum length',await submit(p,'수정 저장')===200&&Number(sql('SELECT CHAR_LENGTH(review) FROM viewing_record WHERE id='+dateRecovered))===4000);
 // Existing version contract: preserve a stale draft, show the latest blank review.
 const stale=await context.newPage();await stale.goto(base+'/records/'+ids[1]+'/edit');await p.goto(base+'/records/'+ids[1]+'/edit');await p.locator('#rating').selectOption('5.0');await p.locator('#review').fill('');check('latest edit saved',await submit(p,'수정 저장')===200);
 await stale.locator('#review').fill('충돌 후 보존할 초안\n한글과 줄바꿈');await stale.locator('#rating').selectOption('0.5');check('stale edit conflicts',await submit(stale,'수정 저장')===409);
 check('conflict preserves full draft and rating',await stale.locator('#review').inputValue()==='충돌 후 보존할 초안\n한글과 줄바꿈'&&await stale.locator('#rating').inputValue()==='0.5');
 check('conflict cannot resubmit stale version',await stale.getByRole('button',{name:'수정 저장',exact:true}).count()===0&&await stale.locator('#review').getAttribute('readonly')!==null);
 check('latest blank review is explicit',await stale.locator('[aria-label="동시 수정 복구"] .review-text').innerText()==='감상평 없음');
 const [popup]=await Promise.all([stale.waitForEvent('popup'),stale.getByRole('link',{name:'최신 기록 다시 편집 (새 창)',exact:true}).click()]);await popup.waitForLoadState();check('conflict recovery opens actual latest editor',await popup.locator('#rating').inputValue()==='5.0'&&await popup.locator('#review').inputValue()==='');await popup.close();
 // Restore this fixture's rating so the scoped expected values stay independent.
 sql('UPDATE viewing_record SET rating=4.0 WHERE id='+ids[1]);
 const conditions={keyword:'긴한글<&"',mediaType:'MOVIE',genreId:String(fixture.genre_id),workId:String(work),from:'2026-02-02',to:'2026-02-03',sort:'recent'};
 const stats='/stats?'+new URLSearchParams(conditions);await p.goto(base+stats);
 check('activity metrics distinguish records works and rewatches',JSON.stringify(await p.locator('.metric>strong').allTextContents())===JSON.stringify(['2건','1개','1개','2회']));
 check('latest NULL uses in-period rated source',await p.locator('.genre-rating').filter({hasText:'드라마'}).innerText().then(t=>t.includes('n=1')&&t.includes('표본 적음')&&t.includes('4.00'))&&await p.locator('#rated-percent').innerText()==='50%');
 const source=p.getByRole('link',{name:'원본 보기 →',exact:true});check('representative points to actual rated record',(await source.getAttribute('href'))==='/records/'+ids[1]);
 const recordLink=p.locator('.stats-records-link'),u=new URL(await recordLink.getAttribute('href'),base);
 check('source list preserves all conditions without page',!u.searchParams.has('page')&&Object.entries(conditions).every(([k,v])=>u.searchParams.get(k)===v));
 await recordLink.focus();check('source link keyboard focus is visible',await recordLink.evaluate(e=>getComputedStyle(e).outlineStyle!=='none'));await recordLink.press('Enter');check('source list contains exactly scoped records',await p.locator('.record-table tbody tr').count()===2);await p.goBack();check('Back restores statistics conditions',await p.locator('#from').inputValue()===conditions.from&&await p.locator('input[name=workId]').inputValue()===String(work));
 await p.goto(base+'/stats?workId='+work+'&from=2026-02-01&to=2026-02-01');check('all unrated differs from zero records',(await p.locator('[data-rating-empty]').innerText()).includes('모두 미평점')&&await p.locator('#rated-percent').innerText()==='0%'&&(await p.locator('.genre-rating').filter({hasText:'드라마'}).innerText()).includes('평점 없음'));
 await p.goto(base+'/stats?workId='+work+'&from=2026-01-01&to=2026-01-01');check('zero records has explicit empty state',(await p.locator('[data-rating-empty]').innerText()).includes('집계할 감상 기록이 없습니다.')&&await p.locator('#rated-percent').innerText()==='—');
 const screens=[['detail-first','/records/'+ids[0]],['detail-same','/records/'+ids[3]],['detail-rated','/records/'+ids[4]],['form','/records/new?workId='+work],['stats',stats],['stats-empty','/stats?workId='+work+'&from=2026-01-01&to=2026-01-01']];
 for(const width of [1440,390,320]){
  await p.setViewportSize({width,height:width===1440?1000:844});await stale.setViewportSize({width,height:width===1440?1000:844});
  for(const [name,url] of screens){check(name+' HTTP200 '+width,(await p.goto(base+url)).status()===200);check(name+' fits viewport '+width,await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await p.bringToFront();await p.screenshot({path:path.join(out,'after-'+name+'-'+width+'.png'),fullPage:true});}
  await p.goto(base+'/records/new?workId='+work);await p.locator('#rating').evaluate(s=>{s.add(new Option('0','0'));s.value='0';});check('rating error HTTP400 '+width,await submit(p,'감상 기록 저장')===400);check('error fits viewport '+width,await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await p.bringToFront();await p.screenshot({path:path.join(out,'after-error-'+width+'.png'),fullPage:true});
  check('conflict fits viewport '+width,await stale.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await stale.bringToFront();await stale.screenshot({path:path.join(out,'after-conflict-'+width+'.png'),fullPage:true});
 }
 await stale.close();check('no JavaScript errors',report.errors.length===0);report.passed=true;report.count=report.cases.length;
 }catch(e){report.passed=false;report.failure=e.message;throw e;}finally{fs.writeFileSync(path.join(out,'rating-stats-checks.json'),JSON.stringify(report,null,2));await browser.close();}
 console.log(JSON.stringify({passed:true,count:report.count,browser:report.browser}));
})().catch(e=>{console.error(e.message);process.exitCode=1;});
