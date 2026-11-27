"""Loopback-only artifact viewer and browser-driven evidence saver."""
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import base64
import html
import json
import urllib.parse

ROOT = Path(__file__).resolve().parents[1]
OUT = (ROOT / 'output' / 'doxygen-design').resolve()
OUT.mkdir(parents=True, exist_ok=True)


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(OUT), **kwargs)

    def log_message(self, *_):
        pass

    def do_GET(self):
        if self.path.split('?')[0] == '/bridge':
            body = '''<!doctype html><meta charset="utf-8"><title>문서 증거 저장</title>
<h1>문서 증거 저장</h1><form method="post" action="/save">
<p><label>저장 이름 <input name="name" id="name" size="100" required></label></p>
<p><label>형식 <select name="kind" id="kind"><option value="text">텍스트</option><option value="image">JPEG 캡처</option></select></label></p>
<p><label>내용 <textarea name="data" id="data" rows="12" cols="120"></textarea></label></p>
<button type="submit">로컬에 저장</button></form>'''.encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        super().do_GET()

    def do_POST(self):
        if self.path != '/save':
            self.send_error(404)
            return
        length = int(self.headers.get('Content-Length', '0'))
        if length > 20_000_000:
            self.send_error(413)
            return
        form = urllib.parse.parse_qs(self.rfile.read(length).decode('utf-8'), keep_blank_values=True)
        try:
            name = form['name'][0]
            path = (OUT / name).resolve()
            if not path.is_relative_to(OUT) or path == OUT:
                raise ValueError('Output path outside artifact directory')
            data = form['data'][0]
            path.parent.mkdir(parents=True, exist_ok=True)
            if form.get('kind', ['text'])[0] == 'image':
                content = base64.b64decode(data, validate=True)
                if not content.startswith(b'\xff\xd8'):
                    raise ValueError('Expected JPEG browser screenshot')
                path.write_bytes(content)
            else:
                path.write_text(data, encoding='utf-8')
            result = {'saved': name, 'bytes': path.stat().st_size}
            body = ('<!doctype html><meta charset="utf-8"><title>저장 완료</title><h1>저장 완료</h1><pre>'
                    + html.escape(json.dumps(result, ensure_ascii=False)) + '</pre><a href="/bridge">다음 저장</a>').encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except (KeyError, ValueError) as exc:
            self.send_error(400, str(exc))


if __name__ == '__main__':
    print('Documentation viewer: http://127.0.0.1:8765/', flush=True)
    ThreadingHTTPServer(('127.0.0.1', 8765), Handler).serve_forever()
