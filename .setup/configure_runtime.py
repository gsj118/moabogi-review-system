from pathlib import Path
import json,xml.etree.ElementTree as ET
project=Path(__file__).resolve().parents[1]
env=json.loads((project/'.setup/environment.json').read_text(encoding='utf-8'))
for file in [Path(env['catalina_base'])/'conf/context.xml',Path(env['workspace'])/'Servers/Tomcat v9.0 Server at localhost-config/context.xml']:
    if not file.exists():continue
    tree=ET.parse(file);context=tree.getroot();cookie=context.find('CookieProcessor')
    needs_change=cookie is None or cookie.get('className')!='org.apache.tomcat.util.http.Rfc6265CookieProcessor' or cookie.get('sameSiteCookies')!='lax'
    if needs_change:
        if cookie is None:cookie=ET.SubElement(context,'CookieProcessor')
        cookie.set('className','org.apache.tomcat.util.http.Rfc6265CookieProcessor');cookie.set('sameSiteCookies','lax')
        tree.write(file,encoding='utf-8',xml_declaration=True)
start=project/'start-server.cmd'
content=start.read_text(encoding='utf-8')
content=content.replace('set "CATALINA_OPTS=-Dfile.encoding=UTF-8"','set "CATALINA_OPTS=-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Seoul"')
if 'start-db.cmd' not in content:content=content.replace('call "%~dp0build.cmd"','call "%~dp0start-db.cmd"\nif errorlevel 1 exit /b %ERRORLEVEL%\ncall "%~dp0build.cmd"')
start.write_text(content.replace('\n','\r\n'),encoding='utf-8',newline='')
(project/'start-db.cmd').write_text('@echo off\r\npowershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0.setup\\start_mysql.ps1"\r\nexit /b %ERRORLEVEL%\r\n',encoding='utf-8',newline='')
print('Runtime cookie and database startup configured')
