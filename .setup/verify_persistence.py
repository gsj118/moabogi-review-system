from pathlib import Path
import http.cookiejar,json,re,socket,subprocess,sys,time,urllib.parse,urllib.request
project=Path(__file__).resolve().parents[1]
env=json.loads((project/'.setup/environment.json').read_text(encoding='utf-8'))
tools=json.loads((Path(env['tools'])/'database-tools.json').read_text())
mysql=str(Path(tools['mysql']['path'])/'bin/mysql.exe')
private=Path(env['tools'])/'config'
def snapshot():
    query='SELECT id,user_id,work_id,viewed_on,IFNULL(rating,"NULL"),IFNULL(review,""),version,created_at,updated_at FROM viewing_record ORDER BY id; SELECT user_id,work_id,status,updated_at FROM user_work_state ORDER BY user_id,work_id;'
    return subprocess.check_output([mysql,'--defaults-extra-file='+str(private/'mysql-admin.cnf'),'--default-character-set=utf8mb4','--database=review_system','--batch','--skip-column-names'],input=query.encode(),creationflags=subprocess.CREATE_NO_WINDOW)
before=snapshot()
subprocess.run([sys.executable,str(project/'.setup/server_control.py'),'stop'],check=True)
deadline=time.monotonic()+20
while time.monotonic()<deadline:
    try:
        with socket.create_connection(('127.0.0.1',8080),.5):pass
        time.sleep(.3)
    except OSError:break
subprocess.run([sys.executable,str(project/'.setup/server_control.py'),'start'],check=True)
after=snapshot()
assert before==after,'Record/state rows changed after restarting Tomcat'
props=dict(line.split('=',1) for line in (private/'demo-account.properties').read_text().splitlines() if '=' in line)
opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
with opener.open('http://localhost:8080/login') as r:page=r.read().decode('utf-8')
token=re.search(r'name="_csrf" value="([^"]+)"',page).group(1)
request=urllib.request.Request('http://localhost:8080/login',data=urllib.parse.urlencode(dict(username=props['username'],password=props['password'],_csrf=token)).encode(),headers={'Content-Type':'application/x-www-form-urlencoded'})
with opener.open(request) as r:assert r.status==200 and r.geturl()=='http://localhost:8080/'
with opener.open('http://localhost:8080/stats?from=2026-10-01&to=2026-10-31') as r:
    page=r.read().decode('utf-8');assert r.status==200 and '4.25' in page and '4.50' in page
report=dict(tomcat_restart_data_unchanged=True,login_after_restart=True,statistics_after_restart=True,server_url='http://localhost:8080/',server_left_running=True)
(project/'review-system/docs/evidence/persistence.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print('PERSISTENCE_AND_RESTART_VERIFIED',flush=True)
