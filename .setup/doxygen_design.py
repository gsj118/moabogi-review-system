#!/usr/bin/env python3
"""Prepare two comment-only Java snapshots and run the existing Doxyfile.

This utility reads Git objects but never changes branches, the index, or refs.
Only the generated output directory is written. Python's standard library is
enough; Doxygen and Graphviz must already be installed.
"""

from __future__ import annotations

import argparse
import bisect
import difflib
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime, timezone


REPO = Path(__file__).resolve().parents[1]
JAVA_ROOT = "review-system/src/main/java/"
DEFAULT_OUTPUT = REPO / "output/doxygen-design"
QA_ANNOTATION_SOURCE = REPO / "review-system/docs/doxygen/qa-annotation/TmdbClient.java"
DEFAULT_PM = "8fa730d69a941e5f8487be652aaaa806c99f74eb"
DEFAULT_QA = "dfc9a33138f1099bc234cb1841fd122c5a50fffc"
DEFAULT_PM_BRANCH = "구서준_작업"

# Literal tokens retain their exact contents, including spaces and escapes.
# Whitespace and comments are excluded only when they occur outside literals.
JAVA_LEXER = re.compile(
    r"(?P<space>\s+)"
    r"|(?P<comment>//[^\r\n]*|/\*[\s\S]*?\*/)"
    r'|(?P<literal>"(?:\\.|[^"\\\r\n])*"|\'(?:\\.|[^\'\\\r\n])*\')'
    r"|(?P<number>0[xX][0-9a-fA-F_]+(?:\.[0-9a-fA-F_]*)?(?:[pP][+-]?[0-9_]+)?[fFdDlL]?"
    r"|0[bB][01_]+[lL]?|(?:[0-9][0-9_]*(?:\.[0-9_]*)?|\.[0-9_]+)(?:[eE][+-]?[0-9_]+)?[fFdDlL]?)"
    r"|(?P<identifier>(?:[^\W\d]|\$)(?:\w|\$)*)"
    r"|(?P<operator>>>>=|>>>|>>=|<<=|>>|<<|->|::|\.\.\.|==|!=|<=|>=|&&|\|\||\+\+|--|[+*/%&|^!-]=|[^\s])",
    re.UNICODE,
)


@dataclass(frozen=True)
class Token:
    kind: str
    value: str
    start: int
    end: int


def lex_java(source: str) -> tuple[list[Token], list[Token]]:
    """Lex the Java 8 syntax used here without discarding literal whitespace."""
    tokens, comments = [], []
    position = 0
    while position < len(source):
        match = JAVA_LEXER.match(source, position)
        if match is None:
            raise ValueError("Java tokenization failed at character %d" % position)
        kind, value = match.lastgroup, match.group()
        if value in ('"', "'") or (source.startswith("/*", position) and kind != "comment"):
            raise ValueError("Unterminated Java literal/comment at character %d" % position)
        token = Token(kind, value, position, match.end())
        if kind == "comment":
            comments.append(token)
        elif kind != "space":
            tokens.append(token)
        position = match.end()
    return tokens, comments


def execution_tokens(source: str) -> list[tuple[str, str]]:
    return [(token.kind, token.value) for token in lex_java(source)[0]]


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def token_sha256(source: str) -> str:
    return sha256(json.dumps(execution_tokens(source), ensure_ascii=False, separators=(",", ":")).encode("utf-8"))


def report_path(path: Path) -> str:
    """Keep repository paths portable while allowing an external output folder."""
    try:
        return str(path.relative_to(REPO))
    except ValueError:
        return str(path)


def assert_execution_unchanged(original: str, annotated: str, path: str) -> None:
    before, after = execution_tokens(original), execution_tokens(annotated)
    if before != after:
        mismatch = next((index for index, pair in enumerate(zip(before, after)) if pair[0] != pair[1]), min(len(before), len(after)))
        raise AssertionError("Executable Java token mismatch: %s, token %d (%d -> %d tokens)" % (path, mismatch, len(before), len(after)))


def validate_lexer() -> None:
    assert execution_tokens('String s = "a b"; // comment\n') == execution_tokens('String/** note */ s="a b";')
    assert execution_tokens('String s="a b";') != execution_tokens('String s="ab";')
    assert execution_tokens('String s="// /* \\""; char c=\' \';') != execution_tokens('String s="// /* \\""; char c=\'x\';')
    assert execution_tokens('a + + b') != execution_tokens('a ++ b')


def git(*arguments: str, required: bool = True) -> bytes:
    result = subprocess.run(["git", *arguments], cwd=str(REPO), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if required and result.returncode:
        raise RuntimeError("Read-only git command failed: git %s\n%s" % (" ".join(arguments), result.stderr.decode("utf-8", errors="replace")))
    return result.stdout if result.returncode == 0 else b""


def resolve_ref(ref: str, required: bool = True) -> str | None:
    value = git("rev-parse", "--verify", ref, required=required).decode("utf-8").strip()
    return value or None


def ref_snapshot() -> dict:
    return {
        "HEAD": resolve_ref("HEAD"),
        "branch": git("symbolic-ref", "--quiet", "--short", "HEAD", required=False).decode("utf-8").strip() or None,
        "main": resolve_ref("refs/heads/main", required=False),
        "origin/QA_JO": resolve_ref("refs/remotes/origin/QA_JO", required=False),
    }


def java_paths(ref: str) -> list[str]:
    names = git("ls-tree", "-r", "-z", "--name-only", ref, "--", JAVA_ROOT).decode("utf-8").split("\0")
    return sorted(path for path in names if path.endswith(".java"))


def canonical_comment(comment: str) -> str:
    lines = [line.strip() for line in comment.splitlines()]
    return "\n".join(line if index == 0 else " " + line for index, line in enumerate(lines))


def leading_insertion(source: str, offset: int, comment: str) -> str:
    prefix = source[source.rfind("\n", 0, offset) + 1:offset]
    inline = bool(prefix.strip())
    indent = "    " if inline else prefix
    rendered = canonical_comment(comment).replace("\n", "\n" + indent)
    return ("\n" + indent if inline else "") + rendered + "\n" + indent


def transfer_comments(pm_annotated: str, qa_original: str, path: str) -> tuple[str, int]:
    """Map Doxygen comments to equal Java tokens, preserving every QA token."""
    _, existing_comments = lex_java(qa_original)
    clean = qa_original
    for comment in reversed(existing_comments):
        if comment.value.startswith("/**"):
            clean = clean[:comment.start] + " " + clean[comment.end:]
    assert_execution_unchanged(qa_original, clean, path)
    pm_tokens, comments = lex_java(pm_annotated)
    qa_tokens, _ = lex_java(clean)
    matcher = difflib.SequenceMatcher(a=[(t.kind, t.value) for t in pm_tokens], b=[(t.kind, t.value) for t in qa_tokens], autojunk=False)
    mapping = {}
    for block in matcher.get_matching_blocks():
        mapping.update((block.a + index, block.b + index) for index in range(block.size))
    starts, ends = [t.start for t in pm_tokens], [t.end for t in pm_tokens]
    insertions = []
    for comment in comments:
        if not comment.value.startswith("/**"):
            continue
        trailing = comment.value.startswith("/**<")
        index = bisect.bisect_right(ends, comment.start) - 1 if trailing else bisect.bisect_left(starts, comment.end)
        if index not in mapping:
            raise AssertionError("Cannot map a documentation comment to QA tokens: %s at character %d" % (path, comment.start))
        target = qa_tokens[mapping[index]]
        offset = target.end if trailing else target.start
        value = " " + comment.value if trailing else leading_insertion(clean, offset, comment.value)
        insertions.append((offset, len(insertions), value))
    result = clean
    for offset, _, value in sorted(insertions, reverse=True):
        result = result[:offset] + value + result[offset:]
    assert_execution_unchanged(qa_original, result, path)
    return result, len(insertions)


def documentation(brief: str, params=(), returns=None, throws=(), details=None) -> str:
    lines = ["/**", " * @brief " + brief]
    if details:
        lines.append(" * @details " + details)
    lines.extend(" * @param %s %s" % pair for pair in params)
    if returns:
        lines.append(" * @return " + returns)
    lines.extend(" * @throws %s %s" % pair for pair in throws)
    return "\n".join(lines + [" */"])


def document_anchor(source: str, anchor: str, comment: str) -> str:
    if source.count(anchor) != 1:
        raise AssertionError("Expected exactly one QA declaration: " + anchor)
    position = source.index(anchor)
    preceding = [item for item in lex_java(source)[1] if item.value.startswith("/**") and not item.value.startswith("/**<") and item.end <= position and not source[item.end:position].strip()]
    if preceding:
        prior = preceding[-1]
        prefix = source[source.rfind("\n", 0, prior.start) + 1:prior.start]
        indent = prefix if not prefix.strip() else "    "
        return source[:prior.start] + comment.replace("\n", "\n" + indent) + source[prior.end:]
    return source[:position] + leading_insertion(source, position, comment) + source[position:]


def prepare_qa_workform(pm_annotated: str, qa_original: str) -> str:
    """Keep every PM member comment and insert only QA's six added members.

    This small form deliberately avoids token-position matching: a trailing
    field comment must never move to one of the two inserted field endings.
    The declarations themselves are read verbatim from the pinned QA object.
    """
    def original_line(prefix: str) -> str:
        matches = [line.strip() for line in qa_original.splitlines() if line.strip().startswith(prefix)]
        if len(matches) != 1:
            raise AssertionError("Expected one QA WorkForm declaration: " + prefix)
        return matches[0]

    def member(comment: str, declaration: str) -> str:
        return "    " + comment.replace("\n", "\n    ") + "\n    " + declaration + "\n"

    fields = member(documentation("외부 작품 식별자를 보관합니다. 현재 서비스의 저장 Map에는 전달되지 않습니다."), original_line("private long externalId;"))
    fields += member(documentation("외부 작품 공급원 문자열을 보관합니다. 현재 서비스의 저장 Map에는 전달되지 않습니다."), original_line('private String externalSource = "";'))
    anchor = re.search(r"(?m)^\s*private boolean confirmDuplicate;[^\n]*\n", pm_annotated)
    if anchor is None:
        raise AssertionError("PM WorkForm confirmDuplicate declaration is missing")
    result = pm_annotated[:anchor.end()] + fields + pm_annotated[anchor.end():]

    methods = []
    for prefix in ("public long getExternalId()", "public String getExternalSource()"):
        # Each pinned QA line contains precisely its getter and setter.
        methods.extend(original_line(prefix).split("} public "))
        methods[-2] += "}"
        methods[-1] = "public " + methods[-1]
    comments = [
        documentation("보관된 외부 작품 식별자를 반환합니다.", returns="외부 작품 식별자"),
        documentation("외부 작품 식별자를 그대로 보관합니다.", [("v", "외부 작품 식별자")]),
        documentation("보관된 외부 작품 공급원을 반환합니다.", returns="외부 공급원 문자열"),
        documentation("외부 공급원을 보관하며 null은 빈 문자열로 바꿉니다.", [("v", "외부 공급원 문자열")]),
    ]
    if len(methods) != len(comments):
        raise AssertionError("Unexpected QA WorkForm accessor layout")
    position = result.rfind("\n}")
    if position < 0:
        raise AssertionError("WorkForm class closing brace is missing")
    result = result[:position] + "\n" + "".join(member(comment, declaration) for comment, declaration in zip(comments, methods)) + result[position:]
    assert_execution_unchanged(qa_original, result, "qa:WorkForm.java")
    return result


def annotate_qa_additions(path: str, source: str) -> str:
    entries = []
    if path.endswith("/WorkService.java"):
        entries = [
            ("private final TmdbClient tmdb;", documentation("외부 작품 검색·상세 정보를 조회하는 TMDB 클라이언트입니다.")),
            ("public WorkService(WorkMapper mapper, TmdbClient tmdb)", documentation("공용 작품 Mapper와 TMDB 클라이언트를 주입받습니다.", [("mapper", "공용 작품·장르 SQL Mapper"), ("tmdb", "TMDB 검색·상세 클라이언트")])),
            ("public List<Map<String,Object>> searchExternal(String query,String mediaType)", documentation("MOVIE는 movie로, 그 밖의 유형은 tv로 바꾸어 외부 검색을 요청합니다.", [("query", "검색할 제목 문자열"), ("mediaType", "애플리케이션 작품 유형 문자열")], "TMDB 클라이언트가 반환한 검색 결과 Map 목록", [("TmdbClient.TmdbException", "외부 검색 설정·입력·응답 처리에 실패한 경우")])),
            ("public Map<String,Object> getExternalDetails(long externalId,String mediaType)", documentation("외부 식별자와 전달받은 유형을 그대로 사용해 상세 정보를 요청합니다.", [("externalId", "TMDB 작품 식별자"), ("mediaType", "호출자가 전달한 작품 유형 문자열")], "TMDB 클라이언트가 구성한 상세 정보 Map", [("TmdbClient.TmdbException", "외부 상세 조회 또는 응답 변환에 실패한 경우")])),
            ("public boolean isExternalSearchEnabled()", documentation("TMDB 클라이언트에 비어 있지 않은 키가 설정됐는지 확인합니다.", returns="키가 설정돼 있으면 true이며 실제 인증·연결 성공 판정은 아님")),
        ]
    elif path.endswith("/WorkController.java"):
        entries = [
            ('@GetMapping("/works/search-external") public String searchExternal(', documentation("외부 검색 화면을 준비하고 검색어가 있으면 TMDB 결과 또는 오류 안내를 표시합니다.", [("query", "검색어이며 빈 문자열이면 검색하지 않음"), ("mediaType", "검색할 작품 유형이며 기본값은 MOVIE"), ("model", "결과·검색 조건·오류를 전달할 화면 모델"), ("response", "검색 예외에 따른 HTTP 상태를 설정할 응답")], "search-external 뷰 이름")),
            ('@PostMapping("/works/import-external") public String importExternal(', documentation("외부 상세 정보를 WorkForm에 옮겨 기존 공용 작품 등록 서비스를 호출합니다.", [("auth", "등록 요청 회원의 인증 정보"), ("externalId", "외부 작품 식별자"), ("mediaType", "호출할 작품 유형 문자열"), ("confirmDuplicate", "동일 제목·유형 수동 중복 후보 확인 여부"), ("model", "실패 안내를 전달할 화면 모델"), ("response", "외부 또는 입력 오류의 HTTP 상태를 설정할 응답"), ("flash", "등록 성공 안내를 전달할 리다이렉트 속성")], "성공하면 작품 상세 리다이렉트, 오류이면 error 뷰", details="폼의 외부 식별자·공급원은 현재 WorkService 저장 Map에 반영되지 않습니다. 현재 동작을 설명하며 가져오기 완료를 보증하지 않습니다.")),
        ]
    for anchor, comment in entries:
        source = document_anchor(source, anchor, comment)
    return source


def locate_tools(output: Path) -> dict:
    metadata_path = output / "toolchain.json"
    metadata = json.loads(metadata_path.read_text(encoding="utf-8-sig")) if metadata_path.is_file() else []
    found = {}
    for name, env_key, command in (("doxygen", "DOXYGEN_EXECUTABLE", "doxygen"), ("dot", "DOT_EXECUTABLE", "dot")):
        candidates = [os.environ.get(env_key)]
        candidates += [item.get("exe") for item in metadata if item.get("name", "").startswith("doxygen" if name == "doxygen" else "graphviz")]
        candidates += [shutil.which(command)]
        install = Path.home() / "dev/review-system/documentation-tools"
        if install.is_dir():
            candidates += [str(path) for path in install.glob("doxygen-*/doxygen.exe" if name == "doxygen" else "graphviz-*/**/bin/dot.exe")]
        executable = next((str(Path(item).resolve()) for item in candidates if item and Path(item).is_file()), None)
        if not executable:
            raise RuntimeError("%s를 찾지 못했습니다. Doxygen과 Graphviz 설치 단계를 먼저 완료하거나 %s에 실행 파일 절대경로를 지정하세요. 기존 설치 기록: %s" % (name, env_key, metadata_path))
        version = subprocess.run([executable, "--version" if name == "doxygen" else "-V"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
        found[name] = {"exe": executable, "version": (version.stdout + version.stderr).decode("utf-8", errors="replace").strip()}
    return found


def write_patch(output: Path, kind: str, items: list[tuple[str, str, str]]) -> str:
    patch = "".join("".join(difflib.unified_diff(original.replace("\r\n", "\n").splitlines(keepends=True), annotated.replace("\r\n", "\n").splitlines(keepends=True), fromfile="a/" + path, tofile="b/" + path)) for path, original, annotated in items)
    target = output / "patches" / (kind + "-annotations.patch")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(patch, encoding="utf-8", newline="\n")
    return report_path(target)


def prepare_sources(output: Path, baselines: dict, report: dict) -> None:
    pm_text = {}
    for kind in ("pm", "qa"):
        ref = baselines[kind]["commit"]
        paths = java_paths(ref)
        source_dir = output / (kind + "-source")
        expected = {str(Path(path[len(JAVA_ROOT):])) for path in paths}
        stale = [str(path.relative_to(source_dir)) for path in source_dir.rglob("*.java") if str(path.relative_to(source_dir)) not in expected] if source_dir.exists() else []
        if stale:
            raise RuntimeError("Generated source directory contains files outside the pinned baseline; use a new output directory: %s" % stale)
        patch_items, rows = [], []
        for path in paths:
            original_bytes = git("show", ref + ":" + path)
            original = original_bytes.decode("utf-8-sig")
            if kind == "pm":
                annotated = (REPO / path).read_text(encoding="utf-8-sig")
                mode, count = "current-checkout-annotation", None
                pm_text[path] = annotated
            elif path.endswith("/external/TmdbClient.java"):
                annotation_file = QA_ANNOTATION_SOURCE
                if not annotation_file.is_file():
                    raise RuntimeError("QA annotation source is missing: " + str(annotation_file))
                annotated = annotation_file.read_text(encoding="utf-8-sig")
                mode, count = "qa-specific-annotation", None
            elif path.endswith("/domain/WorkForm.java"):
                annotated = prepare_qa_workform(pm_text[path], original)
                mode, count = "pm-form-annotation-with-verbatim-qa-added-members", None
            elif path in pm_text and execution_tokens(original) == execution_tokens(pm_text[path]):
                annotated, mode, count = pm_text[path], "same-tokens-reuse-pm-annotation", None
            elif path in pm_text:
                annotated, count = transfer_comments(pm_text[path], original, path)
                annotated = annotate_qa_additions(path, annotated)
                mode = "qa-tokens-with-mapped-pm-and-qa-member-annotations"
            else:
                raise RuntimeError("No annotation source for a QA Java file: " + path)
            assert_execution_unchanged(original, annotated, kind + ":" + path)
            target = source_dir / path[len(JAVA_ROOT):]
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(annotated, encoding="utf-8", newline="\n")
            prepared_bytes = target.read_bytes()
            prepared = prepared_bytes.decode("utf-8")
            assert_execution_unchanged(original, prepared, kind + ":" + path)
            rows.append({"path": path, "baseline_commit": ref, "baseline_blob": resolve_ref(ref + ":" + path), "original_sha256": sha256(original_bytes), "prepared_path": report_path(target), "prepared_sha256": sha256(prepared_bytes), "original_token_sha256": token_sha256(original), "prepared_token_sha256": token_sha256(prepared), "executable_tokens_equal": True, "token_count": len(execution_tokens(prepared)), "annotation_mode": mode, "mapped_comment_count": count})
            patch_items.append((path, original, prepared))
        report["sources"][kind] = {"file_count": len(rows), "input": str(source_dir), "files": rows, "annotation_patch": write_patch(output, kind, patch_items)}
        print("Prepared %s: %d Java files; every executable token matches %s" % (kind, len(rows), ref), flush=True)


def run_doxygen(output: Path, kind: str, baseline: dict, tools: dict) -> dict:
    destination = output / kind
    destination.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    project_number = "%s @ %s | documentation annotation" % (baseline["branch"], baseline["commit"])
    # Relative paths avoid Windows native argv/environment code-page loss in a
    # repository whose absolute path contains Korean directory names.
    environment.update({"DOXYGEN_INPUT": Path(os.path.relpath(output / (kind + "-source"), REPO)).as_posix(), "DOXYGEN_OUTPUT": Path(os.path.relpath(destination, REPO)).as_posix(), "DOXYGEN_BASELINE": baseline["commit"], "DOXYGEN_DOT_PATH": Path(tools["dot"]["exe"]).parent.as_posix()})
    environment["PATH"] = str(Path(tools["dot"]["exe"]).parent) + os.pathsep + environment.get("PATH", "")
    # Native Windows getenv returns locale bytes for Korean branch names.
    # Put the label directly in a UTF-8 config instead of passing it through
    # DOXYGEN_BASELINE; keep the versioned template unchanged.
    configuration, replacements = re.subn(r"(?m)^PROJECT_NUMBER\s*=.*$", lambda _: "PROJECT_NUMBER = " + json.dumps(project_number, ensure_ascii=False), (REPO / "review-system/Doxyfile").read_text(encoding="utf-8-sig"))
    if replacements != 1:
        raise AssertionError("Doxyfile must contain exactly one PROJECT_NUMBER entry")
    generated_config = destination / "Doxyfile.generated"
    generated_config.write_text(configuration, encoding="utf-8", newline="\n")
    result = subprocess.run([tools["doxygen"]["exe"], Path(os.path.relpath(generated_config, REPO)).as_posix()], cwd=str(REPO), env=environment, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    (destination / "doxygen.stdout.log").write_bytes(result.stdout)
    (destination / "doxygen.stderr.log").write_bytes(result.stderr)
    if result.returncode:
        raise RuntimeError("Doxygen failed for %s; inspect %s (exit %d)" % (kind, destination, result.returncode))
    for relative in ("html/index.html", "xml/index.xml"):
        if not (destination / relative).is_file():
            raise AssertionError("Doxygen did not produce " + str(destination / relative))
    member_validation = None
    if kind == "qa":
        workform = ET.parse(destination / "xml/classorg_1_1mnu_1_1domain_1_1_work_form.xml").getroot()
        members = workform.findall(".//sectiondef/memberdef")
        missing = [item.findtext("name") for item in members if not "".join(item.find("briefdescription").itertext()).strip()]
        if len(members) != 24 or missing:
            raise AssertionError("QA WorkForm member brief validation failed: %d members; missing %s" % (len(members), missing))
        member_validation = {"type": "org.mnu.domain.WorkForm", "member_count": len(members), "missing_briefs": missing}
    print("Doxygen %s complete: HTML and XML ready at %s" % (kind, destination), flush=True)
    return {"exit_code": result.returncode, "html": str(destination / "html/index.html"), "xml": str(destination / "xml/index.xml"), "project_number": project_number, "configuration": report_path(generated_config), "qa_workform_members": member_validation}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-base", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--pm-ref")
    parser.add_argument("--qa-ref")
    parser.add_argument("--prepare-only", action="store_true")
    args = parser.parse_args()
    output = args.output_base.resolve()
    output.mkdir(parents=True, exist_ok=True)
    validate_lexer()
    baseline_file = output / "source-baselines.json"
    recorded = json.loads(baseline_file.read_text(encoding="utf-8-sig")) if baseline_file.is_file() else {}
    recorded_pm_branch = recorded.get("pm", {}).get("branch", DEFAULT_PM_BRANCH)
    # Accept metadata from before the branch rename without changing its
    # recorded commit or rewriting the original validation evidence.
    pm_branch = DEFAULT_PM_BRANCH if recorded_pm_branch == "codex/pm-rating-stats" else recorded_pm_branch
    baselines = {
        "pm": {"branch": pm_branch, "commit": resolve_ref(args.pm_ref or recorded.get("pm", {}).get("commit", DEFAULT_PM))},
        "qa": {"branch": recorded.get("qa", {}).get("branch", "QA_JO"), "commit": resolve_ref(args.qa_ref or recorded.get("qa", {}).get("commit", DEFAULT_QA))},
    }
    report = {"started_at_utc": datetime.now(timezone.utc).isoformat(), "repository": str(REPO), "baselines": baselines, "recorded_baselines": recorded, "before_baseline": ref_snapshot(), "sources": {}, "doxygen": {}, "status": "running", "merge_performed": False, "annotation_note": "Prepared files differ only in comments and layout; literal token contents are preserved. Patches use LF line endings."}
    try:
        tools = locate_tools(output) if not args.prepare_only else {}
        report["tools"] = tools
        report["qa_annotation_source"] = report_path(QA_ANNOTATION_SOURCE)
        # A fresh output folder must contain its own provenance. Never replace
        # existing install checksums or an earlier recorded baseline.
        if not baseline_file.exists():
            baseline_file.write_text(json.dumps({**baselines, "main": report["before_baseline"]["main"], "mergeAuthorized": False}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        toolchain_file = output / "toolchain.json"
        if tools and not toolchain_file.exists():
            toolchain_file.write_text(json.dumps([{"name": name, **tools[key]} for key, name in (("doxygen", "doxygen"), ("dot", "graphviz"))], ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        prepare_sources(output, baselines, report)
        if not args.prepare_only:
            for kind in ("pm", "qa"):
                report["doxygen"][kind] = run_doxygen(output, kind, baselines[kind], tools)
        report["status"] = "prepared" if args.prepare_only else "generated"
    except Exception as error:
        report["status"], report["error"] = "failed", str(error)
        raise
    finally:
        report["after_baseline"] = ref_snapshot()
        report["refs_unchanged"] = report["before_baseline"] == report["after_baseline"]
        report["finished_at_utc"] = datetime.now(timezone.utc).isoformat()
        (output / "source-validation.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        if not report["refs_unchanged"]:
            raise AssertionError("Git refs changed during documentation generation; see source-validation.json")
    print("Validation report: " + str(output / "source-validation.json"), flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, RuntimeError, ValueError, subprocess.CalledProcessError) as error:
        print("Doxygen preparation failed: " + str(error), file=sys.stderr)
        raise SystemExit(1)
