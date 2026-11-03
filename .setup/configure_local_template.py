"""Provide a clearly labelled course template using STS's bundled template format."""
from pathlib import Path
import json
import zipfile

PROJECT = Path(__file__).resolve().parents[1]
env = json.loads((PROJECT / '.setup/environment.json').read_text(encoding='utf-8'))
workspace = Path(env['workspace'])
root = Path(env['tools'])
template = workspace / '.metadata/.sts/content/team.e.templates.mvc-1.0.0'
template.mkdir(parents=True, exist_ok=True)
descriptor = '''<descriptor id="team.e.templates.mvc" name="Spring MVC Project (course 5.3.39)"
kind="template" local="true" requiresbundle="org.eclipse.m2e.core" version="1.0.0" size="0">
<description>Course starter: Spring 5.3.39, Java 1.8, JSP UTF-8, Tomcat 9. Package: org.mnu.controller.</description>
</descriptor>'''
(template / 'template.xml').write_text('<?xml version="1.0" encoding="UTF-8"?><template>' + descriptor + '<project path="template.zip"/><json path="wizard.json"/></template>', encoding='utf-8')
(template / 'wizard.json').write_text(json.dumps({'info': {'elements': {}, 'projectName': 'review-system', 'pages': {'page': [{'order': 0, 'description': 'Course project settings'}]}}}), encoding='utf-8')
with zipfile.ZipFile(template / 'template.zip', 'w', zipfile.ZIP_DEFLATED) as z:
    for p in (PROJECT / 'review-system').rglob('*'):
        relative = p.relative_to(PROJECT / 'review-system')
        if p.is_file() and relative.parts[0] != 'target': z.write(p, str(relative).replace('\\', '/'))
    z.writestr('src/main/resources/', '')
descriptors = '<?xml version="1.0" encoding="UTF-8"?><descriptors>' + descriptor + '</descriptors>'
descriptor_file = root / 'course-descriptors.xml'
descriptor_file.write_text(descriptors, encoding='utf-8')
cache = workspace / '.metadata/.plugins/org.springsource.ide.eclipse.commons.content.core'
cache.mkdir(parents=True, exist_ok=True)
(cache / 'https-content.xml').write_text(descriptors, encoding='utf-8')
prefs = workspace / '.metadata/.plugins/org.eclipse.core.runtime/.settings'
(prefs / 'org.springsource.ide.eclipse.commons.core.prefs').write_text('eclipse.preferences.version=1\ncontent.descriptors=' + descriptor_file.as_uri() + '\n', encoding='utf-8')
for name in ['org.eclipse.wst.sse.core', 'org.eclipse.wst.html.core', 'org.eclipse.jst.jsp.core', 'org.eclipse.wst.css.core']:
    (prefs / (name + '.prefs')).write_text('eclipse.preferences.version=1\nencoding=UTF-8\noutputCodeset=UTF-8\n', encoding='utf-8')
print('COURSE_MVC_TEMPLATE_CONFIGURED', template, flush=True)
