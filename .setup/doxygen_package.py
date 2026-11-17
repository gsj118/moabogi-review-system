"""Render actual project-type references and package branch-specific Doxygen output.

Run --graphs-only while annotations are being prepared. After source generation
and validation finish, run --package-only to produce the final ZIP artifacts.
No Git operation, server request, application configuration, or DB access occurs.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
import subprocess
import sys
import textwrap
import xml.etree.ElementTree as ET
import zipfile
from collections import Counter, defaultdict
from pathlib import Path


DATE = "20261117"
MAX_PACKAGE_BYTES = 5_000_000
VARIANTS = ("pm", "qa")
FONT = "Malgun Gothic"
JAVA_PREFIX = "review-system/src/main/java/"
PACKAGE_LABELS = {
    "config": "설정 · config",
    "controller": "요청 처리 · controller",
    "service": "업무 규칙 · service",
    "mapper": "SQL 인터페이스 · mapper",
    "domain": "입력·인증 객체 · domain",
    "external": "외부 연동 · external",
}
COLORS = {
    "config": ("#f5f0ff", "#ab94ce"),
    "controller": ("#fff3ed", "#d8a58c"),
    "service": ("#edf5ff", "#88afd5"),
    "mapper": ("#edf8f2", "#8ebca4"),
    "domain": ("#f1f4f8", "#a4b0bf"),
    "external": ("#fff4d9", "#c99b3b"),
}
PACKAGE_GRIDS = {
    "controller": (2, 100, 710),
    "service": (1, 570, 710),
    "mapper": (1, 840, 710),
    "config": (3, 100, 280),
    "domain": (3, 840, 280),
    "external": (3, 300, 80),
}
FORBIDDEN_SUFFIXES = {
    ".exe", ".dll", ".pdb", ".class", ".jar", ".war", ".db", ".sqlite",
    ".sqlite3", ".sql", ".properties", ".pem", ".key", ".p12", ".pfx", ".env",
}


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def file_record(path: Path, base_dir: Path) -> dict:
    data = path.read_bytes()
    return {
        "path": str(path.resolve()),
        "relativePath": path.relative_to(base_dir).as_posix(),
        "sizeBytes": len(data),
        "sha256": sha256(data),
    }


def quote(text: str) -> str:
    return json.dumps(text, ensure_ascii=False)


def types_for(inventory: dict, variant: str) -> dict:
    result = {
        item["fqn"]: item for item in inventory["types"]
        if item.get("variants", {}).get(variant, {}).get("present")
    }
    expected = inventory["counts"][variant]
    if len(result) != expected:
        raise ValueError(f"{variant}: expected {expected} types, found {len(result)}")
    return result


def package_name(item: dict, variant: str) -> str:
    path = item["variants"][variant]["sourcefile"]
    if not path.startswith(JAVA_PREFIX):
        raise ValueError(f"Unexpected Java source path: {path}")
    return Path(path[len(JAVA_PREFIX):]).parent.as_posix().replace("/", ".")


def relations_for(types: dict, variant: str) -> list[tuple[str, str, str]]:
    edges = set()
    for fqn, item in types.items():
        for relation in item["variants"][variant].get("relations", []):
            target = relation["fqn"]
            if target not in types:
                raise ValueError(f"{variant}: reference endpoint is not a present project type: {target}")
            edges.add((fqn, target, relation["relation"]))
    return sorted(edges)


def graph_signature(inventory: dict, variant: str) -> str:
    types = types_for(inventory, variant)
    record = {
        "baseline": inventory["baselines"][variant],
        "types": [{"fqn": name, "kind": item["variants"][variant]["kind"], "package": package_name(item, variant)} for name, item in sorted(types.items())],
        "relations": relations_for(types, variant),
    }
    return sha256(json.dumps(record, ensure_ascii=False, sort_keys=True).encode("utf-8"))


def node_label(fqn: str, package: str, kind: str) -> str:
    short = fqn[len(package) + 1:]
    if len(short) > 28 and "." in short:
        parent, name = short.rsplit(".", 1)
        short = parent + "\n" + name
    label = {"class": "클래스", "interface": "인터페이스", "enum": "열거형"}.get(kind, kind)
    return short + "\n" + label


def dot_source(inventory: dict, variant: str) -> str:
    types = types_for(inventory, variant)
    baseline = inventory["baselines"][variant]
    branch, commit = baseline["branch"], baseline["commit"][:7]
    scope = "현재 PM 작업본 · 설명 주석 기준" if variant == "pm" else "미통합 QA 개발본 · 실제 API·DB 통합 미검증"
    limitation = "실제 프로젝트 타입만 표시 · DB 테이블·런타임 프록시는 제외"
    if variant == "qa":
        limitation = "영화 상세·외부 ID 저장·장르 매핑 보완 필요 · PM에 병합되지 않음"
    title = (
        f"모아보기 · {'PM' if variant == 'pm' else 'QA'} 클래스 전체 관계도\n"
        f"{branch} @ {commit} · {len(types)}개 타입\n{scope}\n"
        f"점선 화살표: 유형 참조 · 중첩: 선언 위치 · {limitation}"
    )
    lines = [
        "digraph project_types {",
        f"  graph [charset=\"UTF-8\", layout=neato, fontname={quote(FONT)}, fontsize=16, label={quote(title)}, labelloc=t, labeljust=l, bgcolor=\"white\", pad=0.25, splines=polyline, overlap=true, size=\"16,10\", ratio=compress, dpi=120];",
        f"  node [shape=box, style=\"rounded,filled\", fontname={quote(FONT)}, fontsize=12, fontcolor=\"#172b40\", color=\"#91a4b8\", fillcolor=\"#ffffff\", margin=\"0.14,0.09\", width=2.15, height=0.62, penwidth=1.1];",
        f"  edge [fontname={quote(FONT)}, fontsize=9, color=\"#70859a\", fontcolor=\"#526b82\", arrowsize=0.65, penwidth=0.9];",
    ]
    groups = defaultdict(list)
    for name, item in sorted(types.items()):
        groups[package_name(item, variant)].append((name, item))
    order = {name: index for index, name in enumerate(PACKAGE_LABELS)}
    for index, package in enumerate(sorted(groups, key=lambda name: (order.get(name.rsplit(".", 1)[-1], 99), name))):
        leaf = package.rsplit(".", 1)[-1]
        fill, border = COLORS.get(leaf, ("#f4f6f8", "#a6b0bb"))
        if leaf not in PACKAGE_GRIDS:
            raise ValueError(f"Add a non-overlapping package grid for: {package}")
        columns, origin_x, origin_y = PACKAGE_GRIDS[leaf]
        label = PACKAGE_LABELS.get(leaf, leaf) + "\n" + package
        positions = [(origin_x + (position % columns) * 190, origin_y - (position // columns) * 70) for position in range(len(groups[package]))]
        minimum_x, maximum_x = min(point[0] for point in positions), max(point[0] for point in positions)
        minimum_y, maximum_y = min(point[1] for point in positions), max(point[1] for point in positions)
        bounds = f"{minimum_x - 98},{minimum_y - 36},{maximum_x + 98},{maximum_y + 73}"
        label_position = f"{(minimum_x + maximum_x) / 2},{maximum_y + 52}"
        lines.append(f"  subgraph cluster_{index} {{")
        lines.append(f"    label={quote(label)}; bb={quote(bounds)}; lp={quote(label_position)}; fontname={quote(FONT)}; fontsize=12; fontcolor=\"#334b62\"; color={quote(border)}; bgcolor={quote(fill)}; style=rounded; margin=16;")
        for position, (name, item) in enumerate(groups[package]):
            view = item["variants"][variant]
            kind = view["kind"]
            label_text = node_label(name, package, kind)
            special = variant == "qa" and not item.get("variants", {}).get("pm", {}).get("present")
            extra = ', color="#b37c14", fillcolor="#fff6d9", penwidth=1.7' if special else ""
            x = origin_x + (position % columns) * 190
            y = origin_y - (position // columns) * 70
            lines.append(f"    {quote(name)} [label={quote(label_text)}, tooltip={quote(name)}, pos={quote(f'{x},{y}!')}, pin=true{extra}];")
        lines.append("  }")
    for source, target, relation in relations_for(types, variant):
        if "상속" in relation or "구현" in relation:
            label = "구현" if "구현" in relation else "상속"
            style = f'label={quote(label)}, style=solid, arrowhead=empty'
        elif relation == "포함하는 타입":
            style = 'label="중첩", style=dotted, arrowhead=vee, color="#ad8a42", fontcolor="#8a6828"'
        else:
            style = 'label="참조", style=dashed, arrowhead=vee'
        lines.append(f"  {quote(source)} -> {quote(target)} [{style}];")
    lines.append("}")
    return "\n".join(lines) + "\n"


def graph_tools(toolchain) -> tuple[Path, dict]:
    entries = toolchain if isinstance(toolchain, list) else list(toolchain.values())
    candidates = [entry for entry in entries if str(entry.get("name", "")).startswith("graphviz")]
    if len(candidates) != 1:
        raise ValueError("Expected exactly one recorded Graphviz tool")
    exe = Path(candidates[0]["exe"])
    if not exe.is_file():
        raise FileNotFoundError(exe)
    return exe, candidates[0]


def render_graphs(base_dir: Path, inventory: dict, toolchain) -> dict:
    dot, tool = graph_tools(toolchain)
    output_dir = base_dir / "assets"
    output_dir.mkdir(exist_ok=True)
    results = {}
    for variant in VARIANTS:
        stem = output_dir / f"{variant}-class-overview"
        dot_path = stem.with_suffix(".dot")
        dot_path.write_text(dot_source(inventory, variant), encoding="utf-8")
        for extension in ("svg", "png"):
            result = subprocess.run([str(dot), "-Kneato", "-n2", f"-T{extension}", str(dot_path), "-o", str(stem.with_suffix('.' + extension))], capture_output=True, text=True, encoding="utf-8", errors="replace")
            if result.returncode:
                raise RuntimeError(f"Graphviz {variant} {extension}: {result.stderr}")
        svg_path, png_path = stem.with_suffix(".svg"), stem.with_suffix(".png")
        svg = ET.parse(svg_path).getroot()
        node_groups = [node for node in svg.iter() if node.tag.endswith("g") and node.get("class") == "node"]
        if len(node_groups) != inventory["counts"][variant]:
            raise ValueError(f"Rendered node count differs for {variant}")
        png_header = png_path.read_bytes()[:24]
        if png_header[:8] != b"\x89PNG\r\n\x1a\n":
            raise ValueError(f"Invalid PNG: {png_path}")
        width, height = struct.unpack(">II", png_header[16:24])
        results[variant] = {
            "baseline": inventory["baselines"][variant],
            "typeCount": len(node_groups),
            "relationCount": len(relations_for(types_for(inventory, variant), variant)),
            "relationSource": "type-inventory.json / types[].variants[variant].relations (Doxygen XML)",
            "graphSignature": graph_signature(inventory, variant),
            "font": FONT,
            "layout": "Graphviz neato -n2; fixed package grids with actual project nodes and reference edges only",
            "pngWidth": width,
            "pngHeight": height,
            "svgViewBox": svg.get("viewBox"),
            "graphviz": tool,
            "files": [file_record(stem.with_suffix('.' + suffix), base_dir) for suffix in ("dot", "svg", "png")],
        }
    return results


def safe_files(directory: Path) -> list[Path]:
    if not directory.is_dir():
        raise FileNotFoundError(directory)
    result = []
    root = directory.resolve()
    for path in sorted(directory.rglob("*")):
        if path.is_symlink():
            raise ValueError(f"Symlinks are not packaged: {path}")
        if not path.is_file():
            continue
        path.resolve().relative_to(root)
        if path.suffix.lower() in FORBIDDEN_SUFFIXES or path.name.lower().startswith(".env"):
            raise ValueError(f"Unexpected non-documentation file in generated output: {path}")
        result.append(path)
    return result


def json_bytes(value) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def package_entries(base_dir: Path, inventory: dict, toolchain, validation: dict, variant: str) -> tuple[dict[str, bytes], dict[str, bytes]]:
    types = types_for(inventory, variant)
    source_dir = base_dir / f"{variant}-source"
    source_report = validation["sources"][variant]
    reported = source_report["files"]
    if not validation.get("refs_unchanged") or validation.get("merge_performed"):
        raise ValueError("Source validation does not confirm unchanged refs and no merge")
    if not all(row.get("executable_tokens_equal") for row in reported):
        raise ValueError(f"{variant}: source validation contains changed executable tokens")
    if validation["baselines"][variant] != inventory["baselines"][variant]:
        raise ValueError(f"{variant}: validation/inventory baseline mismatch")
    actual_java = sorted(source_dir.rglob("*.java"))
    if len(actual_java) != source_report["file_count"]:
        raise ValueError(f"{variant}: prepared Java file count differs from source validation")
    sources = {}
    for row in reported:
        if not row["path"].startswith(JAVA_PREFIX):
            raise ValueError(f"Unexpected Java source: {row['path']}")
        relative = row["path"][len(JAVA_PREFIX):]
        path = source_dir / relative
        if path.is_symlink():
            raise ValueError(f"Symlink source is not packaged: {path}")
        path.resolve().relative_to(source_dir.resolve())
        contents = path.read_bytes()
        if sha256(contents) != row["prepared_sha256"]:
            raise ValueError(f"Stale prepared source validation: {path}")
        sources["source/" + relative] = contents

    documents = {}
    for folder in ("html", "xml"):
        directory = base_dir / variant / folder
        for path in safe_files(directory):
            documents[folder + "/" + path.relative_to(directory).as_posix()] = path.read_bytes()
    if "html/index.html" not in documents or "xml/index.xml" not in documents:
        raise ValueError(f"Missing Doxygen entrypoints: {variant}")
    for suffix in ("dot", "svg", "png"):
        path = base_dir / "assets" / f"{variant}-class-overview.{suffix}"
        documents["assets/" + path.name] = path.read_bytes()
    sources["annotations.patch"] = (base_dir / "patches" / f"{variant}-annotations.patch").read_bytes()
    repo = Path(__file__).resolve().parents[1]
    sources["config/Doxyfile"] = (repo / "review-system" / "Doxyfile").read_bytes()
    sources["validation/source-validation.json"] = json_bytes({
        "baseline": validation["baselines"][variant],
        "status": validation["status"],
        "source": source_report,
        "doxygen": validation["doxygen"][variant],
        "refs_unchanged": validation["refs_unchanged"],
        "merge_performed": validation["merge_performed"],
        "annotation_note": validation["annotation_note"],
    })
    tools = toolchain if isinstance(toolchain, list) else list(toolchain.values())
    sources["metadata/toolchain.json"] = json_bytes([{key: value for key, value in tool.items() if key != "exe"} for tool in tools])
    documents["metadata/type-inventory.json"] = json_bytes({
        "schemaVersion": inventory["schemaVersion"],
        "variant": variant,
        "baseline": inventory["baselines"][variant],
        "typeCount": len(types),
        "types": [{"fqn": name, "kind": item["variants"][variant]["kind"], "brief": item["variants"][variant]["brief"], "sourcefile": item["variants"][variant]["sourcefile"], "htmlRelativeUrl": item["variants"][variant]["htmlRelativeUrl"].removeprefix(variant + "/"), "relations": item["variants"][variant]["relations"]} for name, item in sorted(types.items())],
    })
    return documents, sources


def readme(inventory: dict, variant: str, mode: str) -> bytes:
    baseline = inventory["baselines"][variant]
    warning = "현재 PM 기준 코드의 설명 주석 산출물입니다." if variant == "pm" else "PM/main에 병합되지 않은 QA 개발본입니다. 외부 연동 결함 보완과 실제 API·DB 통합 검증이 필요합니다."
    return textwrap.dedent(f"""\
        # 모아보기 클래스 설계 문서 — {variant.upper()}

        - 기준 브랜치: {baseline['branch']}
        - 기준 커밋: {baseline['commit']}
        - 문서화 타입: {inventory['counts'][variant]}개
        - 묶음 종류: {mode}
        - {warning}

        `html/index.html`을 브라우저에서 열어 클래스·멤버·소스 설명을 확인합니다.
        `assets/{variant}-class-overview.svg`와 PNG는 실제 Doxygen XML의 프로젝트 타입 참조만 표시합니다.
        점선 화살표는 유형 참조이며, DB 테이블·Spring 런타임 프록시를 가상 클래스로 만들지 않았습니다.

        source/는 실행 토큰을 유지한 Java 설명 주석 사본이고 annotations.patch는 해당 기준 커밋 대비 주석·배치 차이입니다.
        config/Doxyfile, validation/source-validation.json, metadata/toolchain.json에 재생성 설정·검증·도구 버전을 기록했습니다.
        실제 비밀번호·API 키 설정·개인 설정 파일·DB 데이터·설치 바이너리는 포함하지 않습니다.
        그림과 소스 문서가 있다는 사실을 기능 시험·독립 인수·실제 API 호출 완료로 해석하지 않습니다.

        ## 재생성

        Doxygen과 Graphviz의 기록된 버전을 별도로 준비한 뒤 압축을 푼 폴더에서 다음 환경변수를 지정합니다.
        DOXYGEN_INPUT=source, DOXYGEN_OUTPUT=generated, DOXYGEN_BASELINE={baseline['branch']} @ {baseline['commit']}.
        DOXYGEN_DOT_PATH는 준비한 Graphviz의 dot 실행 파일이 있는 폴더로 지정하고 `doxygen config/Doxyfile`을 실행합니다.
        html/source로 분리된 묶음은 같은 폴더에 풀어 동일한 디렉터리 구조로 사용합니다.
        """).encode("utf-8")


def write_zip(path: Path, entries: dict[str, bytes]) -> None:
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            if name.startswith("/") or ".." in Path(name).parts:
                raise ValueError(f"Unsafe archive entry: {name}")
            item = zipfile.ZipInfo(name, (2026, 11, 17, 0, 0, 0))
            item.compress_type = zipfile.ZIP_DEFLATED
            item.external_attr = 0o644 << 16
            archive.writestr(item, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(path) as archive:
        bad = archive.testzip()
        if bad:
            raise ValueError(f"ZIP CRC check failed: {bad}")
        if sorted(archive.namelist()) != sorted(entries):
            raise ValueError("ZIP entry inventory differs")


def make_packages(base_dir: Path, inventory: dict, toolchain, graphs: dict) -> list[dict]:
    validation = read_json(base_dir / "source-validation.json")
    for key in ("missingTypeBrief", "missingMembers", "missingMemberBrief", "unicodeReplacementPresent", "briefWithoutKorean"):
        if inventory.get("quality", {}).get(key):
            raise ValueError(f"Finish documentation quality fixes before packaging: {key}")
    packages_dir = base_dir / "packages"
    packages_dir.mkdir(exist_ok=True)
    results = []
    for variant in VARIANTS:
        if graphs.get(variant, {}).get("graphSignature") != graph_signature(inventory, variant):
            raise ValueError(f"{variant}: overview graph is missing or stale; run --graphs-only first")
        for item in graphs[variant]["files"]:
            path = base_dir / item["relativePath"]
            if sha256(path.read_bytes()) != item["sha256"]:
                raise ValueError(f"Overview graph changed after manifest: {path}")
        documents, sources = package_entries(base_dir, inventory, toolchain, validation, variant)
        stem = f"moabogi-design-{variant}-{DATE}"
        candidate = packages_dir / f"{stem}.candidate"
        combined = {**documents, **sources, "README.md": readme(inventory, variant, "combined")}
        write_zip(candidate, combined)
        selected = []
        if candidate.stat().st_size <= MAX_PACKAGE_BYTES:
            final = packages_dir / f"{stem}.zip"
            candidate.replace(final)
            selected.append((final, "combined", combined))
        else:
            candidate.unlink()
            for mode, entries in (("html", documents), ("source", sources)):
                entries = {**entries, "README.md": readme(inventory, variant, mode)}
                output = packages_dir / f"{stem}-{mode}.zip"
                write_zip(output, entries)
                if output.stat().st_size > MAX_PACKAGE_BYTES:
                    raise ValueError(f"Even the explicit HTML/source split exceeds 5,000,000 bytes: {output}")
                selected.append((output, mode, entries))
        for output, mode, entries in selected:
            results.append({
                **file_record(output, base_dir),
                "variant": variant,
                "baseline": inventory["baselines"][variant],
                "mode": mode,
                "entryCount": len(entries),
                "sourceFileCount": len([name for name in entries if name.startswith("source/") and name.endswith(".java")]),
                "underUploadLimit": output.stat().st_size <= MAX_PACKAGE_BYTES,
                "includedDirectories": sorted({name.split("/", 1)[0] for name in entries}),
            })
    return results


def make_capture_packages(base_dir: Path) -> tuple[list[dict], dict]:
    manifest_path = base_dir / "capture-manifest.json"
    captures = read_json(manifest_path)
    if not isinstance(captures, list) or not captures:
        raise ValueError("Expected the actual capture-manifest.json entry list")
    groups = {variant: {} for variant in VARIANTS}
    image_records = []
    seen = set()
    capture_root = (base_dir / "captures").resolve()
    for row in captures:
        variant = row["variant"]
        if variant not in groups:
            raise ValueError(f"Unexpected capture variant: {variant}")
        for view in ("overview", "details"):
            relative = row[view]
            path = base_dir / relative
            if path.is_symlink():
                raise ValueError(f"Capture symlinks are not included: {path}")
            path.resolve().relative_to(capture_root)
            if relative in seen or path.suffix.lower() not in (".jpg", ".jpeg", ".png"):
                raise ValueError(f"Duplicate or unsupported capture: {relative}")
            seen.add(relative)
            data = path.read_bytes()
            if len(data) != row[view + "Bytes"]:
                raise ValueError(f"Capture size differs from the capture manifest: {path}")
            groups[variant][relative] = data
            image_records.append({"fqn": row["fqn"], "variant": variant, "view": view, "relativePath": relative, "sizeBytes": len(data), "sha256": sha256(data)})
    if len(seen) != 64:
        raise ValueError(f"Expected the reviewed 64-image capture set, found {len(seen)}")
    output_dir = base_dir / "packages"
    output_dir.mkdir(exist_ok=True)
    packages = []
    for variant, entries in groups.items():
        if not entries:
            raise ValueError(f"No captures for {variant}")
        output = output_dir / f"moabogi-captures-{variant}-{DATE}.zip"
        write_zip(output, entries)
        if output.stat().st_size >= MAX_PACKAGE_BYTES:
            raise ValueError(f"Capture ZIP must be smaller than 5,000,000 bytes: {output}")
        packages.append({**file_record(output, base_dir), "variant": variant, "mode": "captures", "imageCount": len(entries), "underUploadLimit": True})
    metadata = {
        "schemaVersion": 1,
        "date": "2026-11-17",
        "sourceManifest": file_record(manifest_path, base_dir),
        "imageCount": len(seen),
        "imagesByVariant": dict(Counter(row["variant"] for row in image_records)),
        "actualManifestImagesOnly": True,
        "packages": packages,
        "images": image_records,
    }
    (base_dir / "capture-package-manifest.json").write_bytes(json_bytes(metadata))
    return packages, metadata


def audit_archives(base_dir: Path, packages: list[dict], capture_packages: list[dict], capture_metadata: dict) -> dict:
    patterns = {
        "github-classic-token": re.compile(rb"gh[pousr]_[A-Za-z0-9]{30,}"),
        "github-fine-grained-token": re.compile(rb"github_pat_[A-Za-z0-9_]{50,}"),
        "openai-secret-key": re.compile(rb"sk-(?:proj-)?[A-Za-z0-9_-]{24,}"),
        "aws-access-key": re.compile(rb"AKIA[0-9A-Z]{16}"),
        "private-key": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    }
    text_suffixes = {".md", ".patch", ".json", ".xml", ".xsd", ".html", ".htm", ".css", ".js", ".mjs", ".dot", ".java", ".txt", ".svg"}
    allowed_roots = {"html", "xml", "source", "config", "validation", "metadata", "assets", "README.md", "annotations.patch"}
    reports = []
    all_capture_entries = set()
    expected_captures = {row["relativePath"] for row in capture_metadata["images"]}
    for item in packages + capture_packages:
        path = Path(item["path"])
        if sha256(path.read_bytes()) != item["sha256"] or path.stat().st_size != item["sizeBytes"]:
            raise ValueError(f"Package changed before audit: {path}")
        if item["sizeBytes"] >= MAX_PACKAGE_BYTES:
            raise ValueError(f"Package is not under the publication size limit: {path}")
        counts = Counter()
        scanned = 0
        with zipfile.ZipFile(path) as archive:
            if archive.testzip():
                raise ValueError(f"ZIP CRC validation failed: {path}")
            for entry in archive.infolist():
                name = entry.filename
                parts = Path(name).parts
                if name.startswith("/") or ".." in parts:
                    raise ValueError(f"Unsafe ZIP path: {name}")
                suffix = Path(name).suffix.lower()
                if suffix in FORBIDDEN_SUFFIXES or Path(name).name.lower().startswith(".env"):
                    raise ValueError(f"Private configuration, DB data or executable file is included: {name}")
                root = name.split("/", 1)[0]
                if item["mode"] == "captures":
                    if name not in expected_captures:
                        raise ValueError(f"Unlisted capture is included: {name}")
                    if name in all_capture_entries:
                        raise ValueError(f"Capture included in multiple packages: {name}")
                    all_capture_entries.add(name)
                elif root not in allowed_roots:
                    raise ValueError(f"Non-documentation root included: {root}")
                if root == "source" and suffix != ".java":
                    raise ValueError(f"Non-Java source included: {name}")
                if root == "config" and name != "config/Doxyfile":
                    raise ValueError(f"Unexpected configuration file included: {name}")
                counts[root] += 1
                if suffix in text_suffixes or name == "config/Doxyfile":
                    scanned += 1
                    data = archive.read(entry)
                    for label, pattern in patterns.items():
                        if pattern.search(data):
                            raise ValueError(f"Potential credential pattern {label} found in {name}; value is not printed")
        reports.append({"path": str(path), "sha256": item["sha256"], "sizeBytes": item["sizeBytes"], "entryCounts": dict(counts), "textFilesScanned": scanned, "crcValid": True, "forbiddenFileCount": 0, "credentialPatternMatches": 0})
    if all_capture_entries != expected_captures:
        raise ValueError("Capture packages do not contain exactly the actual 64-image manifest set")
    result = {
        "schemaVersion": 1,
        "date": "2026-11-17",
        "status": "passed",
        "sizeLimitBytesExclusive": MAX_PACKAGE_BYTES,
        "captureCount": len(all_capture_entries),
        "captureSetMatchesManifest": True,
        "auditMethod": "ZIP filename/category allowlists, CRC checks, SHA-256 checks, and high-confidence credential-pattern scanning of text entries. Capture image membership and hashes are verified against the capture manifest; image pixels are not OCR-scanned by this script.",
        "archives": reports,
    }
    (base_dir / "package-audit.json").write_bytes(json_bytes(result))
    return result


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-dir", type=Path, default=Path(__file__).resolve().parents[1] / "output" / "doxygen-design")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--graphs-only", action="store_true")
    mode.add_argument("--package-only", action="store_true")
    args = parser.parse_args()
    base_dir = args.base_dir.resolve()
    inventory = read_json(base_dir / "type-inventory.json")
    toolchain = read_json(base_dir / "toolchain.json")
    path = base_dir / "package-manifest.json"
    manifest = read_json(path) if path.is_file() else {}
    manifest.update({"schemaVersion": 1, "date": "2026-11-17", "maxPackageBytes": MAX_PACKAGE_BYTES, "tools": toolchain})
    if not args.package_only:
        manifest["graphs"] = render_graphs(base_dir, inventory, toolchain)
    if not args.graphs_only:
        manifest["packages"] = make_packages(base_dir, inventory, toolchain, manifest.get("graphs", {}))
        manifest["capturePackages"], capture_metadata = make_capture_packages(base_dir)
        audit = audit_archives(base_dir, manifest["packages"], manifest["capturePackages"], capture_metadata)
        manifest["archiveAudit"] = {"path": str(base_dir / "package-audit.json"), "status": audit["status"], "captureCount": audit["captureCount"]}
    manifest["packagePolicy"] = "Combined ZIP when at most 5,000,000 bytes; otherwise separate html (HTML/XML/graphs) and source (Java/patch/config/validation) ZIPs, each checked against the same limit."
    path.write_bytes(json_bytes(manifest))
    print(json.dumps({"manifest": str(path), "graphs": {name: {"types": data["typeCount"], "relations": data["relationCount"], "png": [data["pngWidth"], data["pngHeight"]]} for name, data in manifest.get("graphs", {}).items()}, "packages": [{"name": Path(item["path"]).name, "bytes": item["sizeBytes"]} for item in manifest.get("packages", [])]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
