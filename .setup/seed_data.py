from pathlib import Path
import datetime,json,secrets,subprocess,sys
project=Path(__file__).resolve().parents[1]
env=json.loads((project/'.setup/environment.json').read_text(encoding='utf-8'))
root=Path(env['tools']);tools=json.loads((root/'database-tools.json').read_text());mysql=str(Path(tools['mysql']['path'])/'bin/mysql.exe')
private=root/'config'
def execute(sql,database):
    result=subprocess.run([mysql,'--defaults-extra-file='+str(private/'mysql-admin.cnf'),'--batch','--skip-column-names','--default-character-set=utf8mb4','--database='+database],input=sql.encode('utf-8'),stdout=subprocess.PIPE,stderr=subprocess.PIPE,creationflags=subprocess.CREATE_NO_WINDOW)
    if result.returncode:raise RuntimeError('Explicit seed import failed; no credentials were printed.')
    return result.stdout.decode('utf-8').strip()
classes=project/'.setup/helpers';classes.mkdir(exist_ok=True)
libs=project/'review-system/target/ROOT/WEB-INF/lib';classpath=';'.join(str(p) for pattern in ['spring-security-crypto-*.jar','spring-jcl-*.jar'] for p in libs.glob(pattern))
subprocess.run([str(Path(env['jdk'])/'bin/javac.exe'),'-cp',classpath,'-d',str(classes),str(project/'.setup/HashPassword.java')],check=True,creationflags=subprocess.CREATE_NO_WINDOW)
password=secrets.token_urlsafe(24)+'!9'
hashed=subprocess.check_output([str(Path(env['jdk'])/'bin/java.exe'),'-cp',str(classes)+';'+classpath,'HashPassword'],input=(password+'\n').encode(),creationflags=subprocess.CREATE_NO_WINDOW).decode()
mode=sys.argv[1]
if mode=='demo':
    if execute("SELECT COUNT(*) FROM app_user WHERE username='demo_viewer'",'review_system')=='0':
        execute("INSERT INTO app_user(username,password_hash,name,email,role) VALUES('demo_viewer','"+hashed+"','나의 감상 저널','demo@example.invalid','USER')",'review_system')
        (private/'demo-account.properties').write_text('username=demo_viewer\npassword='+password+'\n',encoding='utf-8')
    execute((project/'review-system/db/demo-data.sql').read_text(encoding='utf-8'),'review_system')
    print('DEMO_DATA_IMPORTED: local account credentials remain outside the project.',flush=True)
elif mode=='performance':
    database='review_system_perf'
    # The destructive reset is limited to the dedicated synthetic performance database.
    assert execute('SELECT DATABASE()',database)=='review_system_perf'
    sql=['SET time_zone="+09:00";']
    for table in ['record_submission','user_work_state','viewing_record','work_genre','work','app_user','genre']:sql.append('DELETE FROM '+table+';')
    for n,name in enumerate(['드라마','SF','코미디','액션','스릴러','로맨스'],1):sql.append("INSERT INTO genre(id,name) VALUES("+str(n)+",'"+name+"');")
    for n in range(1,11):sql.append("INSERT INTO app_user(id,username,password_hash,name,email) VALUES(%d,'perf%02d','%s','성능 시험 회원','perf%02d@example.invalid');"%(n,n,hashed,n))
    for n in range(1,1001):
        sql.append("INSERT INTO work(id,title,media_type,created_by) VALUES(%d,'성능 작품 %04d','%s',1);"%(n,n,'MOVIE' if n%2 else 'TV'))
        sql.append('INSERT INTO work_genre(work_id,genre_id) VALUES(%d,%d),(%d,%d);'%(n,n%6+1,n,(n+2)%6+1))
    values=[]
    for member in range(1,11):
        for n in range(1000):
            work=(n*7+member)%800+1;date=datetime.date(2026,1,1)+datetime.timedelta(days=n%286);rating='NULL' if n%5==0 else str((n%10+1)/2)
            values.append("(%d,%d,'%s',%s,'합성 성능 기록')"%(member,work,date,rating))
    for i in range(0,len(values),500):sql.append('INSERT INTO viewing_record(user_id,work_id,viewed_on,rating,review) VALUES'+','.join(values[i:i+500])+';')
    sql.append("INSERT INTO user_work_state(user_id,work_id,status) SELECT 1,id,'WANT' FROM work WHERE id<=20;")
    execute('\n'.join(sql),database)
    (private/'performance-account.properties').write_text('username=perf01\npassword='+password+'\n',encoding='utf-8')
    counts=execute('SELECT (SELECT COUNT(*) FROM work),(SELECT COUNT(*) FROM viewing_record),(SELECT MAX(n) FROM (SELECT COUNT(*) n FROM viewing_record GROUP BY user_id) x)',database)
    print('PERFORMANCE_DATA_IMPORTED',counts,flush=True)
else:raise ValueError('Use demo or performance')
