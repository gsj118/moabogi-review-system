'use strict';
// Run after repov_fixture.py seed, only on a dedicated local --test Tomcat.
const fs = require('fs');
const path = require('path');
const {execFileSync} = require('child_process');
const {chromium} = require('playwright');
const assert = require('assert/strict');

const root = path.resolve(__dirname, '..');
const read = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const env = read(path.join(__dirname, 'environment.json'));
const out = path.resolve(process.env.MOA_EVIDENCE_DIR);
const fixture = read(path.join(out, 'fixture.json'));
const config = path.join(env.tools, 'config');
const account = read(path.join(config, 'repov-browser-account.json'));
const mysql = path.join(read(path.join(env.tools, 'database-tools.json')).mysql.path, 'bin/mysql.exe');
const base = 'http://localhost:8082';
const results = {checks: [], model: 'previousViewing and ratingBasis supplied by test only'};
function sql(query) {
  return execFileSync(mysql, ['--defaults-extra-file=' + path.join(config, 'mysql-admin.cnf'),
    '--database=review_system_test', '--batch', '--skip-column-names', '--default-character-set=utf8mb4'],
    {input: query, encoding: 'utf8', windowsHide: true}).trim();
}
const literal = s => "CONVERT(0x" + Buffer.from(s).toString('hex') + " USING utf8mb4)";
function check(name, passed) { assert.ok(passed, name); results.checks.push(name); }
function javaText(s) { return '"' + Buffer.from(s).toString('base64') + '"'; }
function fixturePage(dir, name, currentId, previous, basis) {
  // Explicit examples of the two model values. Normal controller still loads the
  // selected record, enforces ownership, computes its delta and renders the real JSP.
  const setPrevious = previous ? [
    'Map<String,Object> prev = new HashMap<>();',
    'prev.put("id",' + previous.id + 'L);',
    'prev.put("viewedOn","' + previous.day + '");',
    'prev.put("review",new String(Base64.getDecoder().decode(' + javaText(previous.review) + '),"UTF-8"));',
    'prev.put("rating",' + (previous.rating === null ? 'null' : previous.rating) + ');',
    'request.setAttribute("previousViewing",prev);'
  ].join('\n') : '';
  const setBasis = basis ? 'Map<String,Object> basis = new HashMap<>(); basis.put("id",' + basis.id +
    'L); basis.put("viewedOn","' + basis.day + '"); request.setAttribute("ratingBasis",basis);' : '';
  const source = '<%@ page pageEncoding="UTF-8" import="java.util.*" %><%\n' +
    'if (!java.net.InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress() || !"' +
    account.username + '".equals(request.getRemoteUser())) { response.sendError(404); return; }\n' +
    setPrevious + '\n' + setBasis + '\n%><jsp:forward page="/records/' + currentId + '"/>';
  fs.writeFileSync(path.join(dir, name + '.jsp'), source, {flag: 'wx'});
  return base + '/' + path.basename(dir) + '/' + name + '.jsp';
}
async function login(browser, username) {
  const context = await browser.newContext({viewport: {width: 1440, height: 960}, locale: 'ko-KR'});
  const page = await context.newPage();
  await page.goto(base + '/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(account.password);
  await Promise.all([page.waitForURL(base + '/'), page.getByRole('button', {name:'로그인', exact:true}).click()]);
  return page;
}
async function run() {
  assert.equal(fixture.database, 'review_system_test');
  assert.equal(sql('SELECT DATABASE()'), 'review_system_test');
  const user = Number(fixture.user_ids[0]);
  assert.match(account.username, /^repov_[0-9a-f]+_ui$/);
  assert.equal(sql('SELECT username FROM app_user WHERE id=' + user), account.username);
  const webroot = path.join(env.tools, 'test-tomcat/webapps/ROOT');
  const detail = 'WEB-INF/views/record-detail.jsp';
  assert.deepEqual(fs.readFileSync(path.join(webroot, detail)), fs.readFileSync(path.join(root, 'review-system/src/main/webapp', detail)));
  const work = Number(sql("INSERT INTO work(title,media_type,created_by) VALUES(" + literal('여름의 끝에서') + ",'MOVIE'," + user + ');SELECT LAST_INSERT_ID()'));
  function record(day, rating, review) {
    const id = Number(sql('INSERT INTO viewing_record(user_id,work_id,viewed_on,rating,review) VALUES(' +
      [user,work,"'" + day + "'",rating === null ? 'NULL' : rating,literal(review)].join(',') + ');SELECT LAST_INSERT_ID()'));
    return {id,day,rating,review};
  }
  const a = record('2026-09-01', 3, '처음에는 마지막 장면이 낯설었다.');
  const b = record('2026-10-01', null, '두 번째 보니 그때의 침묵이 기억에 남았다.');
  const c = record('2026-11-01', 4.5, '이번에는 대사보다 표정이 먼저 보였다.\n마지막 장면을 다시 보고 싶다.');
  const browser = await chromium.launch({headless:true, executablePath:process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe'});
  let dir;
  try {
    dir = fs.mkdtempSync(path.join(webroot, 'pm-compare-'));
    results.browser = browser.version();
    const p = await login(browser, account.username);
    const errors = [];
    p.on('pageerror', e => errors.push(e.message));
    const url = id => base + '/records/' + id;
    const go = async address => assert.equal((await p.goto(address)).status(), 200, address);
    const current = p.locator('.current-review'), previous = p.locator('.previous-review');
    const previousLink = previous.getByRole('link'), basisLink = p.locator('.record-value a');
    await go(url(c.id));
    check('live DB delta with missing source fields shows no guessed links', (await p.locator('.record-value').innerText()).includes('+1.5점') && !await previous.count() && !await basisLink.count());
    const example = fixturePage(dir,'original',c.id,b,a);
    await go(example);
    check('different review and rating sources', await current.locator('.review-text').innerText() === c.review &&
      await previous.locator('.review-text').innerText() === b.review && (await previous.innerText()).includes('미평점') &&
      (await basisLink.innerText()).includes(a.day) && (await previousLink.innerText()).includes(b.day));
    const left = await current.boundingBox(), right = await previous.boundingBox();
    check('desktop side by side', left.y === right.y && left.x < right.x);
    await p.locator('.review-comparison').screenshot({path:path.join(out,'comparison-desktop.png')});
    await p.setViewportSize({width:390,height:844});
    const top = await current.boundingBox(), below = await previous.boundingBox();
    check('390px current before previous', top.y + top.height <= below.y && Math.abs(top.x - below.x) < 1);
    await p.locator('.form-card').first().screenshot({path:path.join(out,'comparison-390.png')});
    for (const [link, source] of [[basisLink,a],[previousLink,b]]) {
      await go(example);
      let reached = false;
      for (let i = 0; i < 35 && !reached; i++) {
        await p.keyboard.press('Tab');
        reached = await link.evaluate(el => document.activeElement === el);
      }
      check('Tab reaches original ' + source.day, reached);
      await Promise.all([p.waitForURL(url(source.id)),p.keyboard.press('Enter')]);
      check('Enter opens actual owned source ' + source.day, await current.locator('.review-text').innerText() === source.review);
    }
    const markup = '<img src=x onerror=alert(1)>';
    const longText = ('영화<&>를 다시 보니 ').repeat(400).slice(0,4000 - markup.length) + markup;
    await go(url(b.id) + '/edit');
    await p.locator('#review').fill(longText);
    await p.locator('#viewedOn').fill('2026-08-01');
    await Promise.all([p.waitForURL(url(b.id)),p.getByRole('button',{name:'수정 저장',exact:true}).click()]);
    b.day = '2026-08-01'; b.review = longText;
    check('edit via app persists date and text', (await current.innerText()).includes(b.day) && await current.locator('.review-text').innerText() === b.review);
    await go(fixturePage(dir,'edited',c.id,a,a));
    check('post-edit example links both point to A', await previousLink.getAttribute('href') === '/records/' + a.id &&
      await basisLink.getAttribute('href') === '/records/' + a.id && (await p.locator('.record-value').innerText()).includes('+1.5점'));
    await go(url(a.id) + '/delete');
    await Promise.all([p.waitForURL(base + '/records'), p.getByRole('button',{name:'이 기록 삭제',exact:true}).click()]);
    await go(url(c.id));
    check('live DB delta recalculates after source deletion', (await p.locator('.record-value').innerText()).includes('첫 유효 평점'));
    await go(fixturePage(dir,'deleted',c.id,b,null));
    check('post-delete example points to B without stale rating link', await previousLink.getAttribute('href') === '/records/' + b.id &&
      (await previousLink.innerText()).includes(b.day) && !await basisLink.count());
    check('390px full long text, safely escaped, fits viewport', await previous.locator('.review-text').textContent() === b.review &&
      !await previous.locator('.review-text *').count() && await p.evaluate(()=>document.documentElement.scrollWidth <= innerWidth));
    const other = await login(browser, account.other);
    check('other member cannot read original', (await other.goto(url(b.id))).status() === 404);
    check('other member cannot read test model', (await other.goto(example)).status() === 404);
    check('no page script errors', errors.length === 0);
    results.passed = true;
  } finally {
    await browser.close();
    if (dir) {
      for (const name of ['original.jsp','edited.jsp','deleted.jsp']) fs.rmSync(path.join(dir,name),{force:true});
      fs.rmdirSync(dir);
    }
    fs.writeFileSync(path.join(out,'comparison-results.json'), JSON.stringify(results,null,2));
  }
}
run().catch(error=>{console.error(error);process.exitCode=1;});
