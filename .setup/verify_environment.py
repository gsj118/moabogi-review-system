from pathlib import Path
import json, struct, urllib.request, zipfile
project = Path(__file__).resolve().parents[1]
with zipfile.ZipFile(project / 'review-system/target/ROOT.war') as archive:
    major = struct.unpack('>H', archive.read('WEB-INF/classes/org/mnu/controller/HomeController.class')[6:8])[0]
    assert major == 52
    assert 'WEB-INF/lib/spring-webmvc-5.3.39.jar' in archive.namelist()
with urllib.request.urlopen('http://localhost:8080/health', timeout=10) as reply:
    body = reply.read().decode('utf-8')
    assert 'Spring 5.3.39, Java 15.0.2' in body
with urllib.request.urlopen('http://localhost:8080/login', timeout=20) as reply:
    page = reply.read().decode('utf-8')
    assert '모아보기' in page and '아이디' in page
    content_type = reply.headers.get('Content-Type')
report = dict(java8_class_major=major, health=body, login_jsp_utf8=True, content_type=content_type)
(project / '.setup/environment-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print('CURRENT_APPLICATION_SMOKE_VERIFIED')
