from pathlib import Path
import json, secrets, socket, subprocess, time

project=Path(__file__).resolve().parents[1]
root=Path.home()/'dev/review-system'
tools=json.loads((root/'database-tools.json').read_text())
mysql=Path(tools['mysql']['path'])
config=root/'config';config.mkdir(exist_ok=True)
data=root/'mysql-data'
logs=root/'mysql-logs';logs.mkdir(exist_ok=True)
ini=root/'mysql.ini'
ini.write_text('[mysqld]\nbasedir='+mysql.as_posix()+'\ndatadir='+data.as_posix()+'\nport=3306\nbind-address=127.0.0.1\nmysqlx=0\ncharacter-set-server=utf8mb4\ncollation-server=utf8mb4_0900_ai_ci\ndefault-time-zone=+09:00\nlog-error='+str(logs/'mysql.err').replace('\\','/')+'\n[client]\ndefault-character-set=utf8mb4\n',encoding='utf-8')
new=not (data/'mysql').exists()
if new:
    with socket.socket() as sock:sock.bind(('127.0.0.1',3306))
    subprocess.run([str(mysql/'bin/mysqld.exe'),'--defaults-file='+str(ini),'--initialize-insecure'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=subprocess.CREATE_NO_WINDOW)
def running():
    try:
        with socket.create_connection(('127.0.0.1',3306),1):return True
    except OSError:return False
if not running():
    subprocess.Popen([str(mysql/'bin/mysqld.exe'),'--defaults-file='+str(ini)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=subprocess.CREATE_NO_WINDOW|subprocess.CREATE_NEW_PROCESS_GROUP)
    deadline=time.monotonic()+60
    while not running():
        if time.monotonic()>deadline:raise RuntimeError('MySQL did not start; inspect mysql-logs/mysql.err')
        time.sleep(1)
admin_file=config/'mysql-admin.cnf'
client=[str(mysql/'bin/mysql.exe')]
if new:
    admin_password=secrets.token_urlsafe(40)
    app_password=secrets.token_urlsafe(40)
    test_password=secrets.token_urlsafe(40)
    sql="ALTER USER 'root'@'localhost' IDENTIFIED BY '"+admin_password+"';\n"
    sql+="CREATE DATABASE IF NOT EXISTS review_system CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\nCREATE DATABASE IF NOT EXISTS review_system_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\nCREATE DATABASE IF NOT EXISTS review_system_perf CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n"
    sql+="CREATE USER 'review_app'@'localhost' IDENTIFIED BY '"+app_password+"';\nGRANT SELECT,INSERT,UPDATE,DELETE ON review_system.* TO 'review_app'@'localhost';\n"
    sql+="CREATE USER 'review_test'@'localhost' IDENTIFIED BY '"+test_password+"';\nGRANT ALL PRIVILEGES ON review_system_test.* TO 'review_test'@'localhost';\nGRANT ALL PRIVILEGES ON review_system_perf.* TO 'review_test'@'localhost';\n"
    result=subprocess.run(client+['--default-character-set=utf8mb4','--user=root','--host=localhost'],input=sql.encode(),stdout=subprocess.PIPE,stderr=subprocess.PIPE,creationflags=subprocess.CREATE_NO_WINDOW)
    if result.returncode:raise RuntimeError('Account provisioning failed (details suppressed to protect credentials)')
    admin_file.write_text('[client]\nuser=root\npassword='+admin_password+'\nhost=localhost\nport=3306\n',encoding='utf-8')
    url='jdbc:mysql://localhost:3306/{}?characterEncoding=UTF-8&connectionTimeZone=Asia/Seoul&sslMode=DISABLED&allowPublicKeyRetrieval=true'
    for name,db,user,password in [('application','review_system','review_app',app_password),('test','review_system_test','review_test',test_password),('performance','review_system_perf','review_test',test_password)]:
        (config/(name+'.properties')).write_text('db.url='+url.format(db)+'\ndb.user='+user+'\ndb.password='+password+'\n',encoding='utf-8')
    (config/'mysql-app.cnf').write_text('[client]\nuser=review_app\npassword='+app_password+'\nhost=localhost\nport=3306\n',encoding='utf-8')
    (config/'mysql-test.cnf').write_text('[client]\nuser=review_test\npassword='+test_password+'\nhost=localhost\nport=3306\n',encoding='utf-8')
if not admin_file.exists():raise RuntimeError('Existing instance: provide its administrative credentials in config/mysql-admin.cnf')
schema=(project/'review-system/db/schema.sql').read_bytes()
for db in ['review_system','review_system_test','review_system_perf']:
    subprocess.run(client+['--defaults-extra-file='+str(admin_file),'--default-character-set=utf8mb4','--database='+db],input=schema,check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,creationflags=subprocess.CREATE_NO_WINDOW)
    seed="INSERT IGNORE INTO genre(name) VALUES('드라마'),('SF'),('코미디'),('액션'),('스릴러'),('로맨스'),('다큐멘터리'),('애니메이션');"
    subprocess.run(client+['--defaults-extra-file='+str(admin_file),'--default-character-set=utf8mb4','--database='+db],input=seed.encode('utf-8'),check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,creationflags=subprocess.CREATE_NO_WINDOW)
subprocess.run(['icacls',str(config),'/inheritance:r','/grant:r',str(Path.home().name)+':(OI)(CI)F','SYSTEM:(OI)(CI)F'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=subprocess.CREATE_NO_WINDOW)
for name,body in {
    'start-db.cmd':'"'+str(mysql/'bin/mysqld.exe')+'" --defaults-file="'+str(ini)+'" --console\r\n',
    'stop-db.cmd':'"'+str(mysql/'bin/mysqladmin.exe')+'" --defaults-extra-file="'+str(admin_file)+'" shutdown\r\n',
    'start-dbeaver.cmd':'start "" "'+str(root/'dbeaver/dbeaver.exe')+'"\r\n',
}.items():(project/name).write_text('@echo off\r\n'+body,encoding='utf-8',newline='')
print('MYSQL_READY',tools['mysql']['version'],'localhost:3306; dedicated application/test/performance schemas provisioned; secrets stored only outside project',flush=True)
