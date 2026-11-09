from pathlib import Path
import json
import shutil
import urllib.request
import zipfile

PROJECT = Path(__file__).resolve().parents[1]
ROOT = Path.home() / 'dev/review-system'
STS = ROOT / 'sts-bundle/sts-3.9.18.RELEASE'
WORKSPACE = ROOT / 'sts-bundle/workspace'
JDK = ROOT / 'jdk-15.0.2'
MAVEN = ROOT / 'apache-maven-3.9.9'
TOMCAT = next(ROOT.glob('apache-tomcat-9.*'))
BASE = ROOT / 'tomcat-instance'
WORKSPACE.mkdir(parents=True, exist_ok=True)
ini = STS / 'STS.ini'
backup = STS / 'STS.ini.original'
if not backup.exists():
    shutil.copy2(ini, backup)
original = backup.read_text()
ini.write_text(original.replace('-vmargs', f'-vm\n{JDK / "bin/javaw.exe"}\n-data\n{WORKSPACE}\n-vmargs') + '\n-Dfile.encoding=UTF-8\n', encoding='utf-8')

settings = ROOT / 'maven-settings.xml'
settings.write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <localRepository>{ROOT / 'maven-repository'}</localRepository>
  <interactiveMode>false</interactiveMode>
</settings>
''', encoding='utf-8')
prefs = WORKSPACE / '.metadata/.plugins/org.eclipse.core.runtime/.settings'
prefs.mkdir(parents=True, exist_ok=True)
for name, content in {
    'org.eclipse.core.resources.prefs': 'encoding=UTF-8\n',
    'org.eclipse.wst.jsdt.ui.prefs': 'org.eclipse.wst.jsdt.ui.text.custom_code_templates=\n',
    'org.eclipse.wst.sse.core.prefs': 'encoding=UTF-8\noutputCodeset=UTF-8\n',
    'org.eclipse.wst.html.core.prefs': 'encoding=UTF-8\noutputCodeset=UTF-8\n',
    'org.eclipse.jst.jsp.core.prefs': 'encoding=UTF-8\noutputCodeset=UTF-8\n',
    'org.eclipse.wst.css.core.prefs': 'encoding=UTF-8\noutputCodeset=UTF-8\n',
    'org.eclipse.ui.prefs': 'defaultPerspectiveId=org.springframework.ide.eclipse.ui.springperspective\nshowIntro=false\n',
    'org.eclipse.m2e.core.prefs': 'userSettingsFile=' + str(settings).replace('\\', '/') + '\nupdateIndexes=false\n',
}.items():
    (prefs / name).write_text('eclipse.preferences.version=1\n' + content, encoding='utf-8')

for name in ['conf', 'logs', 'temp', 'webapps', 'work']:
    (BASE / name).mkdir(parents=True, exist_ok=True)
for file in (TOMCAT / 'conf').iterdir():
    if file.is_file(): shutil.copy2(file, BASE / 'conf' / file.name)
server = BASE / 'conf/server.xml'
xml = server.read_text()
xml = xml.replace('<Server port="-1"', '<Server port="8005" address="127.0.0.1"')
xml = xml.replace('<Connector port="8080"', '<Connector address="127.0.0.1" URIEncoding="UTF-8" port="8080"')
server.write_text(xml, encoding='utf-8')
environment = dict(tools=str(ROOT), jdk=str(JDK), sts=str(STS), workspace=str(WORKSPACE),
                   maven=str(MAVEN), tomcat=str(TOMCAT), catalina_base=str(BASE), settings=str(settings), project=str(PROJECT / 'review-system'))
(PROJECT / '.setup/environment.json').write_text(json.dumps(environment, ensure_ascii=False, indent=2), encoding='utf-8')

common = f'@echo off\r\nsetlocal\r\nset "JAVA_HOME={JDK}"\r\nset "PATH={JDK}\\bin;{MAVEN}\\bin;%PATH%"\r\nset "CATALINA_HOME={TOMCAT}"\r\nset "CATALINA_BASE={BASE}"\r\nset "CATALINA_OPTS=-Dfile.encoding=UTF-8"\r\n'
scripts = {
    'dev-shell.cmd': common + 'cd /d "%~dp0"\r\ncmd /k\r\n',
    'start-sts.cmd': common + f'start "" "{STS / "STS.exe"}" -data "{WORKSPACE}" -vm "{JDK / "bin/javaw.exe"}"\r\n',
    'build.cmd': common + f'call "{MAVEN / "bin/mvn.cmd"}" -s "{settings}" -f "%~dp0review-system\\pom.xml" clean package %*\r\nexit /b %ERRORLEVEL%\r\n',
    'start-server.cmd': common + 'powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0.setup\\check_server_port.ps1"\r\nif errorlevel 1 exit /b %ERRORLEVEL%\r\n' + 'call "%~dp0build.cmd"\r\nif errorlevel 1 exit /b %ERRORLEVEL%\r\ncopy /y "%~dp0review-system\\target\\ROOT.war" "%CATALINA_BASE%\\webapps\\ROOT.war" >nul\r\nif errorlevel 1 exit /b %ERRORLEVEL%\r\ncall "%CATALINA_HOME%\\bin\\catalina.bat" run\r\n',
    'stop-server.cmd': common + 'call "%CATALINA_HOME%\\bin\\catalina.bat" stop\r\n',
}
for name, content in scripts.items(): (PROJECT / name).write_text(content, encoding='utf-8', newline='')
print(json.dumps(environment, indent=2), flush=True)

# Probe the original template rather than relying on a third-party copy.
url = 'https://dist.springsource.com/release/STS/help/org.springframework.templates.mvc-3.2.2.zip'
try:
    with urllib.request.urlopen(url, timeout=30) as response:
        data = response.read()
    archive = ROOT / 'downloads/org.springframework.templates.mvc-3.2.2.zip'
    archive.write_bytes(data)
    template = WORKSPACE / '.metadata/.sts/content/org.springframework.templates.mvc-3.2.2'
    template.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as z: z.extractall(template)
    print('ORIGINAL_MVC_TEMPLATE_AVAILABLE', flush=True)
except Exception as error:
    print('ORIGINAL_MVC_TEMPLATE_UNAVAILABLE', str(error), flush=True)
