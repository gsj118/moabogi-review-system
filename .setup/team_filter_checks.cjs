'use strict';

const {account, fixture, go, literal, login, run, sql, submit} = require('./team_browser_fixture.cjs');

async function scenario({page, browser, check}) {
  const user = Number(fixture.user_ids[0]);
  const title = '개인 설정 ' + fixture.prefix + ' ' + Date.now();
  const work = Number(sql("INSERT INTO work(title,media_type,created_by) VALUES(" + literal(title) + ",'MOVIE'," + user + '); SELECT LAST_INSERT_ID()'));
  const genre = Number(fixture.genre_id);
  sql('INSERT INTO work_genre(work_id,genre_id) SELECT ' + work + ",id FROM genre WHERE name IN ('드라마','SF')");
  const ratings = [...Array(16).fill('5.0'), ...Array(16).fill('0.5'), '3.0', 'NULL'];
  const rows = ratings.map(rating => '(' + [user,work,"'2026-10-01'",rating].join(',') + ')');
  sql('INSERT INTO viewing_record(user_id,work_id,viewed_on,rating) VALUES ' + rows.join(','));
  const query = '/works?' + new URLSearchParams({keyword:title});
  const prefs = '/works/hidden-genres';
  const stats = '/stats?workId=' + work;
  const workLink = target => target.locator('.work-card h2 a[href="/works/' + work + '"]');
  const other = await login(browser, account.other);
  await go(page, query);
  check('owner sees test work initially', await workLink(page).count(), 1);
  await go(page, prefs);
  const hidden = page.locator('input[name="hide"][value="' + genre + '"]');
  check('no hidden preference by default', await hidden.isChecked(), false);
  await hidden.check();
  await submit(page, page.locator('form[action$="/hidden-genres"] button[type=submit]'));
  await go(page, prefs);
  check('hidden preference persisted', await hidden.isChecked(), true);
  await go(page, query);
  check('a match on one of the work genres hides it for owner', await workLink(page).count(), 0);
  await go(other, query);
  check('the same public work remains visible for another account', await workLink(other).count(), 1);
  await go(page, '/works/' + work);
  check('saved work detail remains readable', await page.locator('.work-title').innerText(), title);
  await go(page, stats);
  const expectedStats = ['34건','1개','1개','33회'];
  check('hidden genres leave recorded viewing statistics unchanged', await page.locator('.metric>strong').allTextContents(), expectedStats);
  await go(page, prefs);
  await hidden.uncheck();
  await submit(page, page.locator('form[action$="/hidden-genres"] button[type=submit]'));
  await go(page, query);
  check('clearing the hidden genre restores the same work', await workLink(page).count(), 1);

  const records = '/records?workId=' + work;
  const rowLinks = page.locator('.record-table .record-open a');
  const readIds = async () => rowLinks.evaluateAll(links => links.map(link => Number(new URL(link.href).pathname.split('/').pop())));
  for (const [level, rating] of [['MAX','5.0'], ['MIN','0.5']]) {
    await go(page, records);
    await page.locator('#ratingLevel').selectOption(level);
    await page.locator('#sort').selectOption('ratingLow');
    await submit(page, page.locator('form.filters button[type=submit]'));
    const firstPage = await readIds();
    check(level + ' has 15 records on page 1', firstPage.length, 15);
    await submit(page, page.locator('.pagination a').filter({hasText:'다음'}));
    const secondPage = await readIds();
    check(level + ' has one record on page 2', secondPage.length, 1);
    check(level + ' and sort retained on page 2',
      [new URL(page.url()).searchParams.get('ratingLevel'), await page.locator('#sort').inputValue()], [level,'ratingLow']);
    const expectedIds = sql('SELECT id FROM viewing_record WHERE user_id=' + user + ' AND work_id=' + work +
      ' AND rating=' + rating + ' ORDER BY viewed_on DESC,created_at DESC,id DESC').split(/\s+/).map(Number);
    check(level + ' exact record IDs across both pages match DB', firstPage.concat(secondPage), expectedIds);
    await submit(page, page.locator('.pagination a').filter({hasText:'이전'}));
    check(level + ' retained on return to page 1', await readIds(), firstPage);
    await submit(page, page.getByRole('link',{name:'작품 범위 해제',exact:true}));
    const retained = new URL(page.url()).searchParams;
    check(level + ' clearing the work scope keeps rating and sort', [retained.get('workId'), retained.get('ratingLevel'), retained.get('sort')], [null, level, 'ratingLow']);
    await go(page, stats + '&ratingLevel=' + level);
    check(level + ' list-only filter leaves stats denominator unchanged', await page.locator('.metric>strong').allTextContents(), expectedStats);
  }
  await go(page, records + '&sort=ratingLow&page=3');
  const endingIds = await readIds();
  const sorted = sql('SELECT id FROM viewing_record WHERE user_id=' + user + ' AND work_id=' + work +
    ' ORDER BY rating IS NULL,rating ASC,viewed_on DESC,created_at DESC,id DESC LIMIT 15 OFFSET 30').split(/\s+/).map(Number);
  check('low-rating sort puts unrated records last', endingIds, sorted);
}

run('team-filters', scenario).catch(error => { console.error(error.message); process.exitCode = 1; });
