"""Install the course tools as a separate, portable environment."""
from pathlib import Path
import hashlib
import json
import re
import urllib.request
import zipfile

ROOT = Path.home() / 'dev/review-system'
CACHE = ROOT / 'downloads'
ROOT.mkdir(parents=True, exist_ok=True)
CACHE.mkdir(exist_ok=True)

def fetch_text(url):
    with urllib.request.urlopen(url, timeout=90) as response:
        return response.read().decode('utf-8')

def install(label, url, digest_url=None, algorithm='sha512'):
    archive = CACHE / url.rsplit('/', 1)[-1]
    print(f'{label}: {url}', flush=True)
    if not archive.exists():
        partial = archive.with_suffix(archive.suffix + '.part')
        with urllib.request.urlopen(url, timeout=120) as response, partial.open('wb') as output:
            size = 0
            milestone = 0
            while chunk := response.read(1024 * 1024):
                output.write(chunk)
                size += len(chunk)
                if size // (50 * 1024 * 1024) > milestone:
                    milestone = size // (50 * 1024 * 1024)
                    print(f'{label}: {size // (1024 * 1024)} MB', flush=True)
        partial.replace(archive)
    digest = hashlib.new(algorithm, archive.read_bytes()).hexdigest()
    verified = False
    if digest_url:
        expected = re.search(r'\b[0-9a-fA-F]{' + str(len(digest)) + r'}\b', fetch_text(digest_url))
        if not expected or expected.group().lower() != digest:
            raise RuntimeError(f'{label}: checksum mismatch')
        verified = True
    with zipfile.ZipFile(archive) as package:
        target = ROOT.resolve()
        for entry in package.infolist():
            dest = (target / entry.filename).resolve()
            if not dest.is_relative_to(target):
                raise RuntimeError('Unsafe archive path: ' + entry.filename)
        package.extractall('\\\\?\\' + str(ROOT.resolve()))
    print(f'{label}: extracted, {algorithm}={digest}', flush=True)
    return dict(name=label, source=url, checksum=digest, algorithm=algorithm, publisher_checksum_verified=verified)

manifest = []
archive_page = fetch_text('https://jdk.java.net/archive/')
jdk_url = re.search(r'https://download\.java\.net/[^"<>\s]+/openjdk-15\.0\.2_windows-x64_bin\.zip', archive_page).group()
manifest.append(install('OpenJDK 15.0.2', jdk_url, jdk_url + '.sha256', 'sha256'))
manifest.append(install('STS 3.9.18', 'https://download.springsource.com/release/STS/3.9.18.RELEASE/dist/e4.21/spring-tool-suite-3.9.18.RELEASE-e4.21.0-win32-x86_64.zip', algorithm='sha256'))
tomcat_url = 'https://archive.apache.org/dist/tomcat/tomcat-9/v9.0.123/bin/apache-tomcat-9.0.123.zip'
manifest.append(install('Tomcat 9.0.123', tomcat_url, tomcat_url + '.sha512'))
maven_url = 'https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip'
manifest.append(install('Maven 3.9.9', maven_url, maven_url + '.sha512'))
(ROOT / 'installation-manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
print('TOOLS_INSTALLED', ROOT, flush=True)
