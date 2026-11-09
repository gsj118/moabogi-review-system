from pathlib import Path
import hashlib, html, json, re, urllib.request, zipfile

root = Path.home() / 'dev/review-system'
downloads = root / 'downloads'
downloads.mkdir(parents=True, exist_ok=True)
def text(url):
    with urllib.request.urlopen(url, timeout=60) as r: return r.read().decode('utf-8')
def download(url, name):
    path = downloads / name
    if not path.exists():
        partial = path.with_suffix('.part')
        with urllib.request.urlopen(url, timeout=180) as r, partial.open('wb') as out:
            size = milestone = 0
            while chunk := r.read(1024*1024):
                out.write(chunk); size += len(chunk)
                if size//(50*1024*1024) > milestone:
                    milestone = size//(50*1024*1024); print(name, size//(1024*1024), 'MB', flush=True)
        partial.replace(path)
    with zipfile.ZipFile(path) as z:
        for item in z.infolist():
            if not (root / item.filename).resolve().is_relative_to(root.resolve()): raise RuntimeError('Unsafe archive path')
        z.extractall('\\\\?\\' + str(root.resolve()))
    return path

version = '8.4.11'
name = 'mysql-' + version + '-winx64.zip'
url = 'https://cdn.mysql.com/Downloads/MySQL-8.4/' + name
print('MYSQL_VERSION', version, flush=True)
path = download(url, name)
digest = hashlib.md5(path.read_bytes()).hexdigest()
if digest != '2e833921898a9a030ea6bfe81bd811bc': raise RuntimeError('MySQL published checksum mismatch')
mysql_manifest = dict(version=version, path=str(root / ('mysql-' + version + '-winx64')), source=url, md5=digest)
(root / 'database-tools.json').write_text(json.dumps(dict(mysql=mysql_manifest), indent=2), encoding='utf-8')
version = '26.2.2'
name = 'dbeaver-ce-' + version + '-windows-x86_64.zip'
url = 'https://dbeaver.io/files/' + version + '/' + name
print('DBEAVER_VERSION', version, flush=True)
path = download(url, name)
digest = hashlib.sha256(path.read_bytes()).hexdigest()
try:
    expected = text(url + '.sha256')
    if digest not in expected: raise RuntimeError('DBeaver checksum mismatch')
    checksum_verified = True
except urllib.error.HTTPError:
    checksum_verified = False
manifest = dict(mysql=mysql_manifest, dbeaver=dict(version=version, path=str(root / 'dbeaver'), source=url, sha256=digest, publisher_checksum_verified=checksum_verified))
(root / 'database-tools.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
print('DATABASE_TOOLS_INSTALLED', flush=True)
