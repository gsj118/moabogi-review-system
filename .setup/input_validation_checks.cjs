const fs=require('fs'),path=require('path'),crypto=require('crypto');
const {chromium}=require('playwright');
const env=JSON.parse(fs.readFileSync(path.join(__dirname,'environment.json'),'utf8'));
const props=Object.fromEntries(fs.readFileSync(path.join(env.tools,'config','demo-account.properties'),'utf8').trim().split(/\r?\n/).map(l=>[l.slice(0,l.indexOf('=')),l.slice(l.indexOf('=')+1)]));
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'});
 const context=await browser.newContext({viewport:{width:1440,height:1000},locale:'ko-KR',timezoneId:'Asia/Seoul'});const page=await context.newPage();
 const report={browser:browser.version(),measurement:'Click or submit to two animation frames after error alert appears',limit_ms:3000,samples:[]};
 async function submit(name,button){const start=Date.now();await Promise.all([page.waitForNavigation({waitUntil:'load'}),page.getByRole('button',{name:button,exact:true}).click()]);await page.locator('.notice.error').waitFor();const end=await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(()=>r(Date.now())))));report.samples.push({case:name,milliseconds:end-start,passed:end-start<=3000});if(end-start>3000)throw new Error('Input error exceeded 3 seconds: '+name);}
 try{
  const base={username:'validate_'+crypto.randomBytes(4).toString('hex'),password:crypto.randomBytes(16).toString('hex')+'!9',name:'입력 검증',email:'validation@example.invalid'};
  for(const [label,changes] of [['required username',{username:''}],['password policy',{password:'abcdefgh'}],['required name',{name:''}],['email format',{email:'invalid-mail'}],['duplicate username',{username:'demo_viewer'}]]){
   await page.goto('http://localhost:8080/signup');const input={...base,...changes};for(const [key,value] of Object.entries(input))await page.locator('#'+key).fill(value);await submit(label,'회원가입');if(await page.locator('#password').inputValue()!=='')throw new Error('Password was retained after signup error');
  }
  await page.goto('http://localhost:8080/login');await page.locator('#username').fill(props.username);await page.locator('#password').fill(props.password);await Promise.all([page.waitForURL('http://localhost:8080/'),page.getByRole('button',{name:'로그인',exact:true}).click()]);
  await page.goto('http://localhost:8080/works/new');await submit('required work title','작품 등록');
  await page.goto('http://localhost:8080/works');const workUrl=await page.locator('.work-info h2 a').first().getAttribute('href');const workId=Number(workUrl.split('/').pop());
  await page.goto('http://localhost:8080/records/new?workId='+workId);await page.locator('#viewedOn').fill('');await submit('required viewing date','감상 기록 저장');
  await page.goto('http://localhost:8080/records/new?workId='+workId);await page.locator('#viewedOn').fill('2026-10-13');await page.locator('#rating').evaluate(select=>{const option=document.createElement('option');option.value='0.7';option.text='0.7 (negative test)';select.appendChild(option);select.value='0.7';});await submit('invalid rating step (tampered option)','감상 기록 저장');
  await page.goto('http://localhost:8080/records');await page.locator('#from').fill('2026-10-13');await page.locator('#to').fill('2026-10-01');await submit('reversed date interval','조회하기');if(await page.locator('#from').inputValue()!=='2026-10-13')throw new Error('Query conditions were not preserved');
  fs.writeFileSync(path.resolve(__dirname,'../review-system/docs/evidence/input-validation.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report.samples));
 }finally{await context.close();await browser.close();}
})().catch(e=>{console.error(e.message);process.exitCode=1;});
