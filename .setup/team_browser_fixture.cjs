'use strict';

// These checks run only on the dedicated test Tomcat and accounts from repov_fixture.py.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {execFileSync} = require('node:child_process');
const {chromium} = require('playwright');

const base = 'http://localhost:8082';
const readJson = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const out = path.resolve(process.env.MOA_EVIDENCE_DIR);
const fixture = readJson(path.join(out, 'fixture.json'));
const {tools} = readJson(path.join(__dirname, 'environment.json'));
const config = path.join(tools, 'config');
const account = readJson(path.join(config, 'repov-browser-account.json'));
const mysql = path.join(readJson(path.join(tools, 'database-tools.json')).mysql.path, 'bin/mysql.exe');

function sql(query) {
  return execFileSync(mysql, ['--defaults-extra-file=' + path.join(config, 'mysql-admin.cnf'),
    '--host=127.0.0.1', '--protocol=TCP', '--database=review_system_test',
    '--batch', '--skip-column-names', '--default-character-set=utf8mb4'],
    {input: query, encoding: 'utf8', windowsHide: true}).trim();
}

function literal(value) {
  return value === '' ? "''" : 'CONVERT(0x' + Buffer.from(value).toString('hex') + ' USING utf8mb4)';
}

async function login(browser, username) {
  const context = await browser.newContext({locale: 'ko-KR', timezoneId: 'Asia/Seoul',
    viewport: {width: 1440, height: 960}});
  const page = await context.newPage();
  page.setDefaultTimeout(7000);
  await page.goto(base + '/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(account.password);
  await Promise.all([page.waitForURL(base + '/'),
    page.getByRole('button', {name: '로그인', exact: true}).click()]);
  return page;
}

async function submit(page, control) {
  const [response] = await Promise.all([page.waitForNavigation(), control.click()]);
  assert.equal(response.status(), 200, 'unexpected submit response ' + new URL(page.url()).pathname);
}

async function go(page, relative) {
  assert.equal((await page.goto(base + relative)).status(), 200, relative);
}

function idOf(uri, collection) {
  const parts = new URL(uri).pathname.split('/');
  assert.equal(parts.length, 3, 'expected a detail page at ' + uri);
  assert.equal(parts[1], collection);
  assert.match(parts[2], /^[1-9]\d*$/);
  return Number(parts[2]);
}

async function run(name, scenario) {
  const report = {checks: [], passed: false};
  const check = (label, actual, expected) => {
    report.checks.push({label, actual, expected});
    assert.deepEqual(actual, expected, label);
  };
  assert.equal(fixture.database, 'review_system_test');
  assert.equal(sql('SELECT DATABASE()'), fixture.database);
  assert.match(account.username, /^repov_[0-9a-f]+_ui$/);
  assert.equal(sql('SELECT username FROM app_user WHERE id=' + Number(fixture.user_ids[0])), account.username);
  const browser = await chromium.launch({headless: true,
    executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe'});
  try {
    report.browser = browser.version();
    report.fixture = fixture.prefix;
    const page = await login(browser, account.username);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await scenario({page, browser, report, check});
    check('no browser script errors', errors, []);
    report.passed = true;
  } catch (error) {
    report.failure = error.message;
    throw error;
  } finally {
    fs.writeFileSync(path.join(out, name + '-results.json'), JSON.stringify(report, null, 2));
    await browser.close();
  }
  console.log(JSON.stringify({scenario: name, passed: true, checks: report.checks.length}));
}

module.exports = {account, base, config, fixture, go, idOf, literal, login, out, run, sql, submit};
