"""Prepare reviewed browser publication payloads; never writes to Redmine."""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/doxygen-design'
BASE = '/projects/e-team-2026/wiki/'


def load(name):
    return json.loads((OUT / name).read_text(encoding='utf-8-sig'))


def main():
    inventory = load('type-inventory.json')
    captures = load('capture-manifest.json')
    if inventory.get('warnings'):
        raise ValueError('Resolve inventory warnings before publication')
    assert inventory['counts']['union'] == 29
    assert len(captures) == 32
    jobs = {}
    for cap in captures:
        key = (cap['fqn'], cap['variant'])
        assert key not in jobs, key
        for name in ('overview', 'details'):
            path = OUT / cap[name]
            assert path.is_file() and 1000 < path.stat().st_size < 5_000_000, path
        jobs[key] = cap
    issues = []
    for typ in inventory['types']:
        images = []
        paths = []
        variants = ['pm'] if typ['variants']['pm']['present'] else []
        if typ['changed']:
            variants.append('qa')
        for variant in variants:
            cap = jobs[(typ['fqn'], variant)]
            label = 'PM 8fa730d' if variant == 'pm' else 'QA_JO dfc9a33'
            images.append('### ' + label)
            for part, caption in [('overview', '클래스 구조·멤버'), ('details', '설명·메소드 문서')]:
                path = OUT / cap[part]
                images.append(f'![{label} {caption}]({path.name})')
                paths.append(str(path.resolve()))
        body = typ['issueDraft']['description'].replace('DOC_IMAGES_TOKEN', '\n\n'.join(images))
        sources = []
        for variant in ('pm', 'qa'):
            meta = typ['variants'][variant]
            if meta['present']:
                commit = '8fa730d69a941e5f8487be652aaaa806c99f74eb' if variant == 'pm' else 'dfc9a33138f1099bc234cb1841fd122c5a50fffc'
                sources.append(f'[{variant.upper()} 원본 소스](https://github.com/gsj118/moabogi-review-system/blob/{commit}/{meta["sourcefile"]})')
        body += '\n\n## 작성·검증 기록\n2026-11-17 구서준의 Codex 지원으로 설명 주석·문서·캡처를 작성하고 확인했다. 담당자 필드는 기존 기능별 책임자를 따른다. 해당 팀원이 이번 문서를 직접 작성하거나 독립 검토했다고 기록하지 않는다.\n\n'
        body += ' · '.join(sources) + '\n\n전체 HTML/XML·주석 소스·재현 자료는 [[설계_문서]] 첨부 묶음에서 확인한다.\n'
        issues.append({'fqn': typ['fqn'], 'kind': typ['kind'], 'subject': typ['issueDraft']['subject'], 'description': body, 'ownerId': typ['ownerId'], 'reqIds': typ['reqIds'], 'ucIds': typ['ucIds'], 'relationTargets': typ['ucIds'] or typ['reqIds'], 'files': paths, 'startDate': '2026-11-17', 'completionScope': 'documentation only'})
    data = {'issues': issues, 'counts': inventory['counts'], 'mergeAuthorized': False, 'baselineRefs': load('source-baselines.json')}
    (OUT / 'publication-manifest.json').write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'issues': len(issues), 'captures': len(captures), 'files': sum(len(i['files']) for i in issues), 'relationTargets': sum(len(i['relationTargets']) for i in issues)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
