const fs=require('fs'),path=require('path');
const {chromium}=require('playwright');
const env=JSON.parse(fs.readFileSync(path.join(__dirname,'environment.json'),'utf8'));
const props=Object.fromEntries(fs.readFileSync(path.join(env.tools,'config','performance-account.properties'),'utf8').trim().split(/\r?\n/).map(l=>[l.slice(0,l.indexOf('=')),l.slice(l.indexOf('=')+1)]));
const output=path.resolve(__dirname,'../review-system/docs/evidence');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'});
 const context=await browser.newContext({viewport:{width:1440,height:1000},locale:'ko-KR',timezoneId:'Asia/Seoul'});const page=await context.newPage();page.setDefaultTimeout(40000);
 try {
  await page.goto('http://localhost:8081/login');await page.locator('#username').fill(props.username);await page.locator('#password').fill(props.password);await Promise.all([page.waitForURL('http://localhost:8081/'),page.getByRole('button',{name:'로그인',exact:true}).click()]);
  const report={database:'review_system_perf',mysql:'8.4.11',java:'15.0.2',spring:'5.3.39',browser:browser.version(),viewport:'1440x1000',data:{works:1000,records:10000,max_records_per_user:1000},unit:'milliseconds',measurement:'Before navigation through two animation frames after load; local browser overhead included',threshold:'Not yet confirmed by the team; the 3-second input-error requirement is not used for these queries',samples:{}};
  for(const [name,url] of [['records','http://localhost:8081/records?keyword=%EC%84%B1%EB%8A%A5&from=2026-10-01&to=2026-10-13'],['statistics','http://localhost:8081/stats?keyword=%EC%84%B1%EB%8A%A5&from=2026-10-01&to=2026-10-13']]) {
   const times=[];
   for(let i=0;i<4;i++){const start=Date.now();const response=await page.goto(url,{waitUntil:'load'});if(response.status()!==200)throw new Error('Performance page returned non-200 status');await page.locator(name==='records'?'.pagination':'.metric-grid').waitFor();const end=await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(()=>r(Date.now())))));times.push(end-start);}
   report.samples[name]={first:times[0],repeated:times.slice(1)};
  }
  await page.screenshot({path:path.join(output,'performance-stats-desktop.png'),fullPage:true});
  fs.writeFileSync(path.join(output,'performance.json'),JSON.stringify(report,null,2));console.log(JSON.stringify({samples:report.samples,data:report.data,browser:report.browser}));
 }finally{await context.close();await browser.close();}
})().catch(e=>{console.error(e.message);process.exitCode=1;});
