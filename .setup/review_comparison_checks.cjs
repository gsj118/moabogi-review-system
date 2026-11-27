'use strict';

// Exercise the production controller, database, and JSP through ordinary browser forms.
const fs = require('node:fs');
const path = require('node:path');
const {account, base, fixture, go, idOf, login, out, run, sql, submit} = require('./team_browser_fixture.cjs');

async function saveRecord(page, work, day, rating, review) {
  await go(page, '/records/new?workId=' + work + '&action=PAST');
  await page.locator('#viewedOn').fill(day);
  await page.locator('#rating').selectOption(rating);
  await page.locator('#review').fill(review);
  await submit(page, page.getByRole('button', {name: '감상 기록 저장', exact: true}));
  return idOf(page.url(), 'records');
}

async function scenario({page, browser, report, check}) {
  // Use the imported movie when a verified live import was run in this same fixture.
  const importsFile = path.join(out, 'tmdb-live-results.json');
  const imports = fs.existsSync(importsFile) ? JSON.parse(fs.readFileSync(importsFile, 'utf8')) : null;
  let work;
  if (imports?.passed && imports.fixture === fixture.prefix) {
    work = Number(imports.works[0].localId);
    check('imported movie has no prior records for this fixture',
      Number(sql('SELECT COUNT(*) FROM viewing_record WHERE user_id=' + fixture.user_ids[0] + ' AND work_id=' + work)), 0);
    report.workSource = 'verified live import';
  } else {
    await go(page, '/works/new');
    await page.locator('#title').fill('통합 비교 ' + fixture.prefix + ' ' + Date.now());
    await page.locator('[name=genreIds][value="' + fixture.genre_id + '"]').check();
    await submit(page, page.getByRole('button', {name: '작품 등록', exact: true}));
    work = idOf(page.url(), 'works');
    report.workSource = 'manual registration';
  }
  const reviews = ['처음 보니 마지막 장면이 낯설었다.', '다시 보니 침묵이 기억에 남았다.', '이번에는 표정이 먼저 보였다.'];
  const a = await saveRecord(page, work, '2026-09-01', '3.0', reviews[0]);
  const b = await saveRecord(page, work, '2026-10-01', '', reviews[1]);
  const c = await saveRecord(page, work, '2026-11-01', '4.5', reviews[2]);
  const current = page.locator('.current-review'), previous = page.locator('.previous-review');
  const previousLink = previous.getByRole('link'), basisLink = page.locator('.record-value a');
  const p = id => '/records/' + id;
  report.records = {work, a, b, c};
  check('T09-05 actual UI submits match chronological database rows',
    sql('SELECT GROUP_CONCAT(CONCAT(id,\':\',COALESCE(CAST(rating AS CHAR),\'NULL\')) ORDER BY viewed_on,created_at,id) FROM viewing_record WHERE user_id=' + fixture.user_ids[0] + ' AND work_id=' + work), a + ':3.0,' + b + ':NULL,' + c + ':4.5');
  check('T09-05 previous review is B', await previous.locator('.review-text').innerText(), reviews[1]);
  check('T09-05 B link and date', [await previousLink.getAttribute('href'), (await previousLink.innerText()).includes('2026-10-01')], [p(b), true]);
  check('T09-05 rated source is A', [await basisLink.getAttribute('href'), (await basisLink.innerText()).includes('2026-09-01')], [p(a), true]);
  check('T09-05 C score difference', (await page.locator('.record-value').innerText()).includes('+1.5점'), true);
  check('current C and unrated B labels', [await current.locator('.review-text').innerText(), (await previous.innerText()).includes('미평점')], [reviews[2], true]);
  const left = await current.boundingBox(), right = await previous.boundingBox();
  check('desktop reviews side by side', left.y === right.y && left.x < right.x, true);
  await page.locator('.review-comparison').screenshot({path: path.join(out, 'comparison-desktop.png')});
  await page.setViewportSize({width: 390, height: 844});
  const top = await current.boundingBox(), below = await previous.boundingBox();
  check('mobile current before previous', top.y + top.height <= below.y && Math.abs(top.x - below.x) < 1, true);
  await page.locator('.form-card').first().screenshot({path: path.join(out, 'comparison-390.png')});
  for (const [link, id, review] of [[previousLink,b,reviews[1]], [basisLink,a,reviews[0]]]) {
    await go(page, p(c));
    await link.focus();
    await Promise.all([page.waitForURL(base + p(id)), link.press('Enter')]);
    check('keyboard opens owned original ' + id, await current.locator('.review-text').innerText(), review);
  }
  const stats = '/stats?workId=' + work;
  await go(page, stats);
  check('T09-01 records, distinct work, rewatched works, repeat events',
    await page.locator('.metric>strong').allTextContents(), ['3건','1개','1개','2회']);
  check('T09-01 two rated records out of three', await page.locator('#rated-percent').innerText(), '66.7%');
  for (const sample of fixture.cases) {
    const params = new URLSearchParams({workId: sample.work_id, from: sample.date, to: sample.date});
    await go(page, '/stats?' + params);
    check('T09-01 displayed rated fraction ' + sample.rated + '/' + sample.total,
      await page.locator('#rated-percent').innerText(), sample.expected);
  }
  await go(page, stats + '&from=2026-09-01&to=2026-10-31');
  const representative = page.getByRole('link', {name: '원본 보기 →', exact: true});
  check('T09-02 latest unrated B keeps in-period A as representative', await representative.getAttribute('href'), p(a));
  await go(page, stats + '&from=2026-10-01&to=2026-10-31');
  check('T09-02 only unrated B in period gives no rated source', await representative.count(), 0);

  // Preserve the previous suite's long Korean/escaped text coverage on a real record.
  const longText = ('지난 감상의 <마지막 장면> & 여운 ').repeat(250).slice(0, 4000);
  await go(page, p(b) + '/edit');
  await page.locator('#review').fill(longText);
  await page.locator('#viewedOn').fill('2026-08-01');
  await submit(page, page.getByRole('button', {name: '수정 저장', exact: true}));
  await go(page, p(c));
  check('T09-05 date edit reselects both originals as A', [await previousLink.getAttribute('href'), await basisLink.getAttribute('href')], [p(a), p(a)]);
  await go(page, p(a) + '/delete');
  await submit(page, page.getByRole('button', {name: '이 기록 삭제', exact: true}));
  await go(page, p(c));
  check('T09-05 deleting A leaves only B and no prior rating', [await previousLink.getAttribute('href'), await basisLink.count(), (await page.locator('.record-value').innerText()).includes('첫 유효 평점')], [p(b), 0, true]);
  check('long previous review rendered as text', [(await previous.locator('.review-text').textContent()) === longText, await previous.locator('.review-text *').count(), await page.evaluate(()=>document.documentElement.scrollWidth <= innerWidth)], [true, 0, true]);
  await go(page, stats);
  check('T09-03 totals recalculate after delete', [await page.locator('.metric>strong').allTextContents(), await page.locator('#rated-percent').innerText(), await representative.getAttribute('href')],
    [['2건','1개','1개','1회'], '50%', p(c)]);
  await go(page, p(c) + '/edit');
  await page.locator('#rating').selectOption('');
  await submit(page, page.getByRole('button', {name: '수정 저장', exact: true}));
  await go(page, stats);
  check('T09-03 clearing last rating recalculates source and ratio', [await representative.count(), await page.locator('#rated-percent').innerText()], [0, '0%']);
  const extra = await saveRecord(page, work, '2026-11-01', '4.0', '같은 날 한 번 더 본 기록');
  check('same-day second viewing saves another row and reports the existing count',
    [extra !== c, (await page.locator('#content').innerText()).includes('기존 기록 1건'),
      Number(sql("SELECT COUNT(*) FROM viewing_record WHERE viewed_on='2026-11-01' AND user_id=" + fixture.user_ids[0] + ' AND work_id=' + work))],
    [true, true, 2]);
  const other = await login(browser, account.other);
  check('other member cannot read previous review', (await other.goto(base + p(b))).status(), 404);
}

run('comparison', scenario).catch(error => { console.error(error.message); process.exitCode = 1; });
