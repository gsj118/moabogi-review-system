'use strict';
// Additional view regressions only; domain contracts stay in the existing MySQL suite.
const fs=require('fs'),path=require('path'),{execFileSync}=require('child_process'),{chromium}=require('playwright');
const out=path.resolve(process.env.MOA_EVIDENCE_DIR||'review-system/docs/evidence/handover-20261016');
const env=JSON.parse(fs.readFileSync('.setup/environment.json')),f=JSON.parse(fs.readFileSync(path.join(out,'fixture.json'))),cfg=JSON.parse(fs.readFileSync(path.join(env.tools,'config/repov-browser-account.json')));
const dbtools=JSON.parse(fs.readFileSync(path.join(env.tools,'database-tools.json'))),base='http://localhost:8082';
const report={cases:[],errors:[],layouts:[]};
function check(name,ok){report.cases.push({name,passed:!!ok});if(!ok)throw Error(name);}
function sql(s){return execFileSync(path.join(dbtools.mysql.path,'bin/mysql.exe'),['--defaults-extra-file='+path.join(env.tools,'config/mysql-admin.cnf'),'--database=review_system_test','--batch','--skip-column-names','--default-character-set=utf8mb4'],{input:s,encoding:'utf8',windowsHide:true}).trim();}
const hex=s=>'CONVERT(0x'+Buffer.from(s).toString('hex')+' USING utf8mb4)';
async function login(p){await p.goto(base+'/login');await p.locator('#username').fill(cfg.username);await p.locator('#password').fill(cfg.password);await Promise.all([p.waitForURL(base+'/'),p.getByRole('button',{name:'로그인',exact:true}).click()]);}
(async()=>{
 const b=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||'C:/Program Files/Google/Chrome/Application/chrome.exe'});
 const p=await b.newPage({viewport:{width:1440,height:1000},locale:'ko-KR',timezoneId:'Asia/Seoul'});p.on('pageerror',e=>report.errors.push(e.message));
 try{
 await login(p);
 const expected={keyword:'페이지',mediaType:'MOVIE',genreId:String(f.genre_id),from:'2026-10-01',to:'2026-10-15',sort:'oldest',workId:String(f.page_work_id)};
 const scoped='/records?'+new URLSearchParams({...expected,page:'2'});
 await p.goto(base+scoped);
 check('compound filters page2 renders 1 of16',await p.locator('.record-table tbody tr').count()===1&&(await p.locator('.pagination').innerText()).includes('2 / 2'));
 for(const key of ['keyword','mediaType','genreId','from','to','sort']){
   const link=p.locator('[data-clear-filter="'+key+'"]'),u=new URL(await link.getAttribute('href'),base);
   check('partial clear '+key+' removes only target and page',!u.searchParams.has(key)&&!u.searchParams.has('page')&&Object.entries(expected).filter(([k])=>k!==key).every(([k,v])=>u.searchParams.get(k)===v));
 }
 await p.locator('[data-clear-filter=from]').focus();
 check('condition link has visible keyboard focus',await p.evaluate(()=>document.activeElement.matches('[data-clear-filter=from]')&&getComputedStyle(document.activeElement).outlineStyle!=='none'));
 await p.locator('[data-clear-filter=from]').press('Enter');
 check('keyboard partial clear returns to first valid page',!new URL(p.url()).searchParams.has('from')&&(await p.locator('.pagination').innerText()).includes('1 / 2'));
 await p.getByRole('link',{name:'다음 →',exact:true}).click();
 check('pagination retains remaining compound conditions',new URL(p.url()).searchParams.get('page')==='2'&&Object.entries(expected).filter(([k])=>k!=='from').every(([k,v])=>new URL(p.url()).searchParams.get(k)===v));
 await p.goto(base+scoped);
 const clear=new URL(await p.getByRole('link',{name:'작품 범위 해제',exact:true}).getAttribute('href'),base);
 check('existing scope clear preserves6conditions and resets page',!clear.searchParams.has('workId')&&!clear.searchParams.has('page')&&Object.entries(expected).filter(([k])=>k!=='workId').every(([k,v])=>clear.searchParams.get(k)===v));
 await p.goto(base+'/records?workId='+f.page_work_id+'&keyword='+encodeURIComponent('없는 제목 "<script>&')+'&from=2000-01-01&sort=rating');
 check('empty results keep active conditions and scope recovery',await p.locator('.empty').count()===1&&await p.locator('[data-clear-filter=keyword]').count()===1&&await p.getByRole('link',{name:'작품 범위 해제',exact:true}).count()===1);
 const safe=new URL(await p.locator('[data-clear-filter=from]').getAttribute('href'),base);
 check('unsafe title roundtrips as text in remaining condition',safe.searchParams.get('keyword')==='없는 제목 "<script>&'&&await p.locator('.applied-filters script').count()===0);
 await p.goto(base+'/stats?workId='+f.page_work_id+'&from=2026-10-01&to=2026-10-15');
 check('statistics announces work scope and keeps hidden input',await p.locator('[data-clear-filter=workId]').innerText().then(t=>t.includes(String(f.page_work_id)))&&await p.locator('input[name=workId]').inputValue()===String(f.page_work_id));
 await Promise.all([p.waitForNavigation(),p.getByRole('button',{name:'조회하기',exact:true}).click()]);
 check('statistics submit retains work scope',new URL(p.url()).searchParams.get('workId')===String(f.page_work_id));
 await p.locator('[data-clear-filter=workId]').click();
 check('statistics scope clear retains chosen period',!new URL(p.url()).searchParams.has('workId')&&new URL(p.url()).searchParams.get('from')==='2026-10-01'&&new URL(p.url()).searchParams.get('to')==='2026-10-15');
 await p.goto(base+'/works?keyword='+encodeURIComponent('페이지')+'&mediaType=MOVIE&genreId='+f.genre_id);
 check('search reuses exactly3condition chips',await p.locator('[data-clear-filter]').count()===3);
 await p.locator('[data-clear-filter=genreId]').click();
 check('search partial clear keeps title/type',new URL(p.url()).searchParams.get('keyword')==='페이지'&&new URL(p.url()).searchParams.get('mediaType')==='MOVIE'&&!new URL(p.url()).searchParams.has('genreId'));
 await p.goto(base+'/stats?from=2026-10-15&to=2026-10-01');
 check('invalid interval keeps error focus and individual recovery',await p.locator('.notice.error').evaluate(e=>e===document.activeElement)&&await p.locator('.metric').count()===0&&await p.locator('[data-clear-filter=from]').count()===1);
 await p.locator('[data-clear-filter=from]').click();
 check('removing bad start date recovers stats without erasing end date',await p.locator('.notice.error').count()===0&&await p.locator('#to').inputValue()==='2026-10-01');
 await p.goto(base+'/records/new?workId='+f.conflict_work_id+'&action=PAST');
 await p.evaluate(()=>{window.__pageRestores=[];addEventListener('pageshow',e=>window.__pageRestores.push(e.persisted));});
 await p.locator('#review').fill('뒤로가기 후에도 저장 방식을 확인합니다.');
 await Promise.all([p.waitForURL(/\/records\/\d+$/),p.getByRole('button',{name:'감상 기록 저장',exact:true}).click()]);
 const savedId=Number(p.url().split('/').pop());
 check('save success identifies preserved current state',await p.locator('.notice.success').innerText().then(t=>t.includes('현재 상태는 유지')));
 await p.goBack();
 check('real browser Back retains save explanation and usable submit',await p.locator('#save-effect').count()===1&&await p.getByRole('button',{name:'감상 기록 저장',exact:true}).isEnabled()&&await p.locator('.submit-progress').count()===0);
 report.realBackPersistedEvents=await p.evaluate(()=>window.__pageRestores||[]);
 // Exercise persisted=true deterministically even when Chromium chooses a fresh document.
 await p.evaluate(()=>{const form=document.querySelector('form[data-progress]');form.dataset.submitting='true';const m=document.createElement('p');m.className='submit-progress';m.setAttribute('role','status');form.appendChild(m);form.querySelector('button[type=submit]').disabled=true;dispatchEvent(new PageTransitionEvent('pageshow',{persisted:true}));});
 check('persisted pageshow clears only progress and preserves save effect',await p.locator('#save-effect').count()===1&&await p.locator('.submit-progress').count()===0&&await p.getByRole('button',{name:'감상 기록 저장',exact:true}).isEnabled());
 await p.locator('input[name=action][value=COMPLETE]').check();
 check('restored save explanation responds to action choice',await p.locator('#save-effect').innerText().then(t=>t.includes('현재 상태를 완료로 변경')));
 // Maximum permitted continuous strings expose wrapping that short Korean samples miss.
 const longTitle='LongUnbrokenMovieTitle'.repeat(9).slice(0,200),longName='LongMemberName'.repeat(5).slice(0,60);
 sql('UPDATE work SET title='+hex(longTitle)+' WHERE id='+f.conflict_work_id+';UPDATE app_user SET name='+hex(longName)+' WHERE id='+f.user_ids[0]);
 // Reload principal to reflect the changed test member name.
 await Promise.all([p.waitForURL(/\/login/),p.getByRole('button',{name:'로그아웃',exact:true}).click()]);await login(p);
 const routes=[['home','/'],['works','/works'],['work','/works/'+f.conflict_work_id],['form','/records/new?workId='+f.conflict_work_id],['edit','/records/'+savedId+'/edit'],['detail','/records/'+savedId],['rated-detail','/records/'+f.conflict_record_id],['delete','/records/'+savedId+'/delete'],['records',scoped],['stats','/stats?workId='+f.conflict_work_id],['states','/states'],['empty','/records?workId='+f.conflict_work_id+'&keyword=absent'],['error','/stats?from=2026-10-15&to=2026-10-01'],['missing','/records/999999999']];
 for(const width of [1440,390,320]){
  await p.setViewportSize({width,height:width===1440?1000:844});
  for(const[name,url]of routes){
   const response=await p.goto(base+url);
   if(name==='work')await p.locator('.state-only summary').click();
   if(name==='stats')await p.locator('.stats-basis summary').click();
   const metrics=await p.evaluate(()=>({width:innerWidth,scrollWidth:document.documentElement.scrollWidth,overflowElements:[...document.querySelectorAll('main *')].filter(e=>{const r=e.getBoundingClientRect();return r.width&&r.right>innerWidth+1&&!e.closest('.table-wrap');}).slice(0,8).map(e=>({tag:e.tagName,cls:e.className,right:e.getBoundingClientRect().right})),clippedHeadings:[...document.querySelectorAll('main h2')].filter(e=>e.scrollWidth>e.clientWidth+1).map(e=>e.className),accountFits:document.querySelector('.account strong').getBoundingClientRect().right<=document.querySelector('.sidebar').getBoundingClientRect().right}));
   report.layouts.push({name,width,status:response.status(),...metrics});
   // Capture before assertion so a failed layout remains reviewable.
   await p.screenshot({path:path.join(out,'final-'+name+'-'+width+'.png'),fullPage:true});
   check(name+' page no overflow at'+width,metrics.scrollWidth<=width);
   check(name+' headings and member name fit at'+width,metrics.clippedHeadings.length===0&&metrics.accountFits);
   check(name+' correct response at'+width,response.status()===(name==='error'?400:name==='missing'?404:200));
   check(name+' form controls labelled at'+width,await p.evaluate(()=>[...document.querySelectorAll('main input:not([type=hidden]),main select,main textarea')].every(e=>e.labels.length||e.hasAttribute('aria-label'))));
   if(width===320)check(name+' date filters show full date at320',await p.locator('.filter-date input').evaluateAll(inputs=>inputs.every(e=>e.clientWidth>=145)));
  }
 }
 const anonymous=await b.newPage({locale:'ko-KR'});
 for(const width of [1440,390,320]){await anonymous.setViewportSize({width,height:844});for(const route of ['login','signup']){await anonymous.goto(base+'/'+route+(route==='login'?'?error':''));check(route+' no overflow at'+width,await anonymous.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await anonymous.screenshot({path:path.join(out,'final-'+route+'-'+width+'.png'),fullPage:true});}}
 await anonymous.close();
 check('no javascript page errors',report.errors.length===0);report.passed=true;report.browser=b.version();
 }catch(e){report.passed=false;report.failure=e.message;throw e;}
 finally{
  sql('UPDATE work SET title='+hex('반응형 검수 긴 작품 제목')+' WHERE id='+f.conflict_work_id+';UPDATE app_user SET name='+hex('<script>unsafe</script> 긴 이름')+' WHERE id='+f.user_ids[0]);
  report.count=report.cases.length;fs.writeFileSync(path.join(out,'handover-checks.json'),JSON.stringify(report,null,2));await b.close();console.log(JSON.stringify({passed:report.passed,count:report.count,failure:report.failure}));
 }
})().catch(e=>{console.error(e.message);process.exitCode=1;});
