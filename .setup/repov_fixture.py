"""Synthetic F01-F08 browser fixtures; writes credentials only outside the project."""
from pathlib import Path
import argparse, hashlib, json, os, secrets, subprocess

root=Path(__file__).resolve().parents[1]
env=json.loads((root/'.setup/environment.json').read_text(encoding='utf-8'))
tools=Path(env['tools']);private=tools/'config'
dbtools=json.loads((tools/'database-tools.json').read_text(encoding='utf-8'))
mysql=str(Path(dbtools['mysql']['path'])/'bin/mysql.exe')
out=Path(os.environ.get('MOA_EVIDENCE_DIR', str(root/'review-system/docs/evidence/repov-20261016'))).resolve();out.mkdir(parents=True,exist_ok=True)
tables=['record_submission','user_work_state','viewing_record','work_genre','work','app_user','genre']

def sql(text,database='review_system_test'):
    p=subprocess.run([mysql,'--defaults-extra-file='+str(private/'mysql-admin.cnf'),'--host=127.0.0.1','--protocol=TCP','--batch','--skip-column-names','--default-character-set=utf8mb4','--database='+database],input=text.encode('utf-8'),stdout=subprocess.PIPE,stderr=subprocess.PIPE,creationflags=subprocess.CREATE_NO_WINDOW)
    if p.returncode:raise RuntimeError('Fixture SQL failed; details withheld to protect credentials')
    return p.stdout.decode('utf-8').strip()

def literal(text):return "CONVERT(0x"+text.encode('utf-8').hex()+" USING utf8mb4)"

def snapshot(stage):
    result={}
    for table in tables:
        raw=sql('SELECT * FROM '+table+' ORDER BY 1,2','review_system')
        result[table]={'rows':int(sql('SELECT COUNT(*) FROM '+table,'review_system')),'sha256':hashlib.sha256(raw.encode()).hexdigest()}
    (out/('app-data-'+stage+'.json')).write_text(json.dumps(result,indent=2),encoding='utf-8')
    if stage=='after':
        before=json.loads((out/'app-data-before.json').read_text(encoding='utf-8'))
        assert result==before,'App data changed during testing'
    print('APP_DATA_'+stage.upper(),{k:v['rows'] for k,v in result.items()})

def seed():
    assert sql('SELECT DATABASE()')=='review_system_test'
    suffix=secrets.token_hex(4);prefix='repov_'+suffix
    password=secrets.token_urlsafe(24)+'!9'
    libs=root/'review-system/target/ROOT/WEB-INF/lib'
    cp=';'.join(str(p) for pattern in ['spring-security-crypto-*.jar','spring-jcl-*.jar'] for p in libs.glob(pattern))
    classes=root/'.setup/helpers';classes.mkdir(exist_ok=True)
    subprocess.run([str(Path(env['jdk'])/'bin/javac.exe'),'-cp',cp,'-d',str(classes),str(root/'.setup/HashPassword.java')],check=True,creationflags=subprocess.CREATE_NO_WINDOW)
    hashed=subprocess.check_output([str(Path(env['jdk'])/'bin/java.exe'),'-cp',str(classes)+';'+cp,'HashPassword'],input=(password+'\n').encode(),creationflags=subprocess.CREATE_NO_WINDOW).decode()
    usernames=[prefix+'_ui',prefix+'_other']
    users=[]
    for name in usernames:
        users.append(int(sql("INSERT INTO app_user(username,password_hash,name,email) VALUES(%s,%s,'보완 시험 회원',%s);SELECT LAST_INSERT_ID();"%(literal(name),literal(hashed),literal(name+'@example.invalid')))))
    sql("INSERT IGNORE INTO genre(name) VALUES('드라마'),('SF');")
    genre=int(sql("SELECT id FROM genre WHERE name='드라마'"));sf=int(sql("SELECT id FROM genre WHERE name='SF'"))
    def work(title):
        wid=int(sql("INSERT INTO work(title,media_type,created_by) VALUES(%s,'MOVIE',%d);SELECT LAST_INSERT_ID();"%(literal(title),users[0])))
        sql('INSERT INTO work_genre(work_id,genre_id) VALUES(%d,%d),(%d,%d)'%(wid,genre,wid,sf))
        return wid
    def record(wid,date,rating,review='합성 기록'):
        return int(sql("INSERT INTO viewing_record(user_id,work_id,viewed_on,rating,review) VALUES(%d,%d,'%s',%s,%s);SELECT LAST_INSERT_ID();"%(users[0],wid,date,rating,literal(review))))
    cases=[]
    for index,(rated,total,expected) in enumerate([(0,0,'—'),(0,4,'0%'),(4,4,'100%'),(1,4,'25%'),(2,4,'50%'),(2,3,'66.7%'),(3,4,'75%'),(3,17,'17.6%')],1):
        date='2026-01-%02d'%index;wid=work('비율 경계 %d'%index)
        for n in range(total):record(wid,date,'4.0' if n<rated else 'NULL')
        cases.append({'date':date,'rated':rated,'total':total,'expected':expected,'work_id':wid})
    paged=work('페이지 경계 작품');ids=[record(paged,'2026-10-10','4.0') for n in range(16)]
    conflict=work('동시 수정 시험 작품');conflict_id=record(conflict,'2026-10-01','4.0','원본 감상평')
    sql("INSERT INTO user_work_state(user_id,work_id,status) VALUES(%d,%d,'WATCHING')"%(users[0],conflict))
    manifest={'database':'review_system_test','prefix':prefix,'user_ids':users,'cases':cases,'genre_id':genre,'page_work_id':paged,'page_record_ids':ids,'conflict_work_id':conflict,'conflict_record_id':conflict_id}
    (out/'fixture.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    (private/'repov-browser-account.json').write_text(json.dumps({'username':usernames[0],'other':usernames[1],'password':password,'signup':prefix+'_signup'}),encoding='utf-8')
    print('FIXTURE_READY',json.dumps(manifest,ensure_ascii=True))

def cleanup():
    assert sql('SELECT DATABASE()')=='review_system_test'
    fixture=json.loads((out/'fixture.json').read_text(encoding='utf-8'))
    ids=sql("SELECT GROUP_CONCAT(id) FROM app_user WHERE username LIKE "+literal(fixture['prefix']+'%'))
    assert all(part.isdigit() for part in ids.split(','))
    for table in ['record_submission','user_work_state','viewing_record']:sql('DELETE FROM '+table+' WHERE user_id IN ('+ids+')')
    sql('DELETE FROM work WHERE created_by IN ('+ids+');DELETE FROM app_user WHERE id IN ('+ids+')')
    remaining=int(sql('SELECT COUNT(*) FROM app_user WHERE username LIKE '+literal(fixture['prefix']+'%')))
    assert remaining==0
    (private/'repov-browser-account.json').unlink(missing_ok=True)
    (out/'cleanup.json').write_text(json.dumps({'database':'review_system_test','deleted_user_ids':ids.split(','),'remaining_fixture_users':remaining,'app_database_untouched':True},indent=2),encoding='utf-8')
    print('FIXTURE_CLEANED',ids)

parser=argparse.ArgumentParser();parser.add_argument('action',choices=['snapshot-before','snapshot-after','seed','cleanup']);args=parser.parse_args()
if args.action.startswith('snapshot'):snapshot(args.action.split('-')[1])
elif args.action=='seed':seed()
else:cleanup()
