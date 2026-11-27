'use strict';

const path = require('node:path');
const {fixture, go, idOf, literal, out, run, sql, submit} = require('./team_browser_fixture.cjs');
const live = process.argv.includes('--live');

async function fallback({page, check}) {
  const title = '로컬 & 직접 등록 ' + fixture.prefix;
  await go(page, '/works/search-external?' + new URLSearchParams({query:title,mediaType:'TV'}));
  check('offline explanation is visible', await page.getByText(/지금은 TMDB 검색을 이용할 수 없습니다/).count(), 1);
  check('offline page has no external query form', await page.locator('#external-query').count(), 0);
  await submit(page, page.getByRole('link',{name:'등록된 작품 검색',exact:true}));
  const local = new URL(page.url());
  check('local fallback keeps title and medium', [local.pathname, local.searchParams.get('keyword'), local.searchParams.get('mediaType')], ['/works',title,'TV']);
  await page.goBack();
  await submit(page, page.getByRole('link',{name:'작품 직접 등록',exact:true}));
  check('manual fallback preserves title', await page.locator('#title').inputValue(), title);

  // A stale provider entry must not delete or change the member's own work history.
  const user = Number(fixture.user_ids[0]);
  const expiredTitle = '만료 시험 ' + fixture.prefix;
  const work = Number(sql('INSERT INTO work(title,media_type,release_year,source,external_id,external_fetched_at,created_by) VALUES (' +
    literal(expiredTitle) + ",'MOVIE',2015,'TMDB'," + Date.now() + ",'2000-01-01'," + user + ');SELECT LAST_INSERT_ID()'));
  sql('INSERT INTO work_genre(work_id,genre_id) VALUES (' + work + ',' + Number(fixture.genre_id) + ')');
  const memo = '내가 직접 남긴 평점과 감상평';
  const record = Number(sql('INSERT INTO viewing_record(user_id,work_id,viewed_on,rating,review) VALUES (' + user +
    ',' + work + ",'2026-10-03',4.0," + literal(memo) + ');SELECT LAST_INSERT_ID()'));
  await go(page, '/records/' + record);
  check('T02-08 expired provider title is hidden in history detail', await page.locator('.form-card h2').first().innerText(), '외부 작품 정보 갱신 필요');
  check('T02-08 member review and rating survive expiry', [await page.locator('.current-review .review-text').innerText(),
    (await page.locator('.record-value').innerText()).includes('4.0')], [memo, true]);
  await go(page, '/stats?workId=' + work);
  check('T02-08 private activity count and rated source still present',
    [await page.locator('.metric>strong').allTextContents(), await page.getByRole('link',{name:'원본 보기 →',exact:true}).getAttribute('href')],
    [['1건','1개','0개','0회'], '/records/' + record]);
  check('T02-08 expiry excludes source genre from its average', (await page.locator('.genre-rating').filter({hasText:'드라마'}).innerText()).includes('평점 없음'), true);
}

async function expectedDetails(kind, id) {
  // Never send the credential to a test URL and never include it in evidence.
  const token = process.env.TMDB_READ_ACCESS_TOKEN;
  if (!token) throw Error('TMDB_READ_ACCESS_TOKEN is required for independent live verification');
  const response = await fetch('https://api.themoviedb.org/3/' + kind + '/' + id + '?language=ko-KR',
    {headers: {Authorization:'Bearer ' + token}, redirect:'error', signal:AbortSignal.timeout(6000)});
  if (!response.ok) throw Error('TMDB detail unavailable: HTTP ' + response.status);
  return response.json();
}

async function realImports({page, report, check}) {
  report.works = [];
  for (const item of [
    {query:'인셉션', mediaType:'MOVIE', kind:'movie', id:27205},
    {query:'브레이킹 배드', mediaType:'TV', kind:'tv', id:1396}
  ]) {
    const details = await expectedDetails(item.kind, item.id);
    const isMovie = item.kind === 'movie';
    const expectedTitle = isMovie ? details.title || details.original_title : details.name || details.original_name;
    const year = Number((isMovie ? details.release_date : details.first_air_date).slice(0,4));
    const sourceGenres = details.genres.map(g => Number(g.id));
    const localGenreNames = new Set();
    for (const id of sourceGenres) {
      const match = sql("SELECT g.name FROM external_genre_map eg JOIN genre g ON g.id=eg.genre_id WHERE eg.source='TMDB' AND eg.media_type=" + literal(item.mediaType) + ' AND eg.external_genre_id=' + id);
      localGenreNames.add(match || '미분류');
    }
    const names = [...localGenreNames].sort();
    const before = sql('SELECT COUNT(*) FROM viewing_record WHERE user_id=' + fixture.user_ids[0]);
    let localId;
    for (let attempt = 0; attempt < 2; attempt++) {
      await go(page, '/works/search-external');
      await page.locator('#external-query').fill(item.query);
      await page.locator('#external-type').selectOption(item.mediaType);
      await submit(page, page.getByRole('button', {name:'TMDB 검색',exact:true}));
      const form = page.locator('.tmdb-result form').filter({has:page.locator('input[name="externalId"][value="' + item.id + '"]')});
      check(item.kind + ' official source is in search results ' + attempt, await form.count(), 1);
      // Duplicate title confirmation may return a 409 page.
      await Promise.all([page.waitForNavigation(), form.getByRole('button').click()]);
      const duplicate = page.locator('form').filter({has:page.locator('input[name="confirmDuplicate"][value="true"]')});
      if (await duplicate.count()) await submit(page, duplicate.getByRole('button'));
      const savedId = idOf(page.url(), 'works');
      if (attempt === 0) localId = savedId;
      check(item.kind + ' reuses stable ID ' + attempt, savedId, localId);
    }
    await go(page, '/works/' + localId);
    check(item.kind + ' saved title on detail', await page.locator('.work-title').innerText(), expectedTitle);
    const meta = await page.locator('.work-detail p.muted').first().innerText();
    check(item.kind + ' saved year and genres visible', [meta.includes(String(year)), ...names.map(n => meta.includes(n))], Array(1 + names.length).fill(true));
    const stored = sql('SELECT title,release_year,source,media_type,external_id FROM work WHERE id=' + localId).split('\t');
    check(item.kind + ' local metadata matches fresh TMDB response', stored, [expectedTitle,String(year),'TMDB',item.mediaType,String(item.id)]);
    check(item.kind + ' mapped genre names match official genre IDs',
      sql('SELECT g.name FROM work_genre wg JOIN genre g ON g.id=wg.genre_id WHERE wg.work_id=' + localId).split(/\r?\n/).filter(Boolean).sort(), names);
    check(item.kind + ' importing did not create a personal record', sql('SELECT COUNT(*) FROM viewing_record WHERE user_id=' + fixture.user_ids[0]), before);
    report.works.push({type:item.mediaType,externalId:item.id,localId,title:expectedTitle,year,genres:names});
    await page.locator('.work-detail').screenshot({path:path.join(out, 'tmdb-live-' + item.kind + '.png')});
  }
}

run(live ? 'tmdb-live' : 'tmdb-offline', live ? realImports : fallback)
  .catch(error => { console.error(error.message); process.exitCode = 1; });
