from pathlib import Path
import argparse,json,shutil,socket,subprocess,time,urllib.request,xml.etree.ElementTree as ET

project=Path(__file__).resolve().parents[1]
env=json.loads((project/'.setup/environment.json').read_text(encoding='utf-8'))
parser=argparse.ArgumentParser();parser.add_argument('action',choices=['start','stop']);mode=parser.add_mutually_exclusive_group();mode.add_argument('--performance',action='store_true');mode.add_argument('--test',action='store_true');args=parser.parse_args()
home=Path(env['tomcat']);base=Path(env['tools'])/('test-tomcat' if args.test else 'performance-tomcat') if args.performance or args.test else Path(env['catalina_base'])
port=8082 if args.test else (8081 if args.performance else 8080)
config=Path(env['tools'])/'config'/('test.properties' if args.test else ('performance.properties' if args.performance else 'application.properties'))
if (args.performance or args.test) and not (base/'conf/server.xml').exists():
    for folder in ['conf','logs','temp','webapps','work']:(base/folder).mkdir(parents=True,exist_ok=True)
    for file in (Path(env['catalina_base'])/'conf').iterdir():
        if file.is_file():shutil.copy2(file,base/'conf'/file.name)
    tree=ET.parse(base/'conf/server.xml');tree.getroot().set('port','8007' if args.test else '8006')
    for connector in tree.getroot().iter('Connector'):
        if connector.get('port')=='8080':connector.set('port',str(port))
    tree.write(base/'conf/server.xml',encoding='utf-8',xml_declaration=True)
java=str(Path(env['jdk'])/'bin/java.exe')
classpath=str(home/'bin/bootstrap.jar')+';'+str(home/'bin/tomcat-juli.jar')
command=[java,'-Dfile.encoding=UTF-8','-Duser.timezone=Asia/Seoul','-Dreview.config='+str(config),'-Dcatalina.home='+str(home),'-Dcatalina.base='+str(base),'-Djava.io.tmpdir='+str(base/'temp'),'-cp',classpath,'org.apache.catalina.startup.Bootstrap']
log=project/'.setup/logs'/('test-server.log' if args.test else ('performance-server.log' if args.performance else 'application-server.log'));log.parent.mkdir(exist_ok=True)
if args.action=='stop':
    with log.open('ab') as output:subprocess.run(command+['stop'],stdout=output,stderr=output,check=True,creationflags=subprocess.CREATE_NO_WINDOW)
    print('SERVER_STOPPED',port,flush=True)
else:
    with socket.socket() as sock:sock.bind(('127.0.0.1',port))
    shutil.copy2(project/'review-system/target/ROOT.war',base/'webapps/ROOT.war')
    with log.open('wb') as output:process=subprocess.Popen(command+['start'],stdout=output,stderr=output,creationflags=subprocess.CREATE_NO_WINDOW|subprocess.CREATE_NEW_PROCESS_GROUP)
    deadline=time.monotonic()+100
    while time.monotonic()<deadline:
        if process.poll() is not None:raise RuntimeError('Tomcat stopped. Inspect .setup/logs server log.')
        try:
            with urllib.request.urlopen('http://localhost:'+str(port)+'/health',timeout=3) as r:
                if r.status==200:break
        except Exception:time.sleep(1)
    else:raise RuntimeError('Tomcat startup timed out. Inspect .setup/logs server log.')
    print('SERVER_READY http://localhost:'+str(port)+'/',flush=True)
