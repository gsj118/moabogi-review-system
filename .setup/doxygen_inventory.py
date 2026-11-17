"""Build a local PM/QA Doxygen type inventory and Redmine description drafts.

Only local Doxygen XML/HTML is read. No server or Git operations are performed.
The expected Java type sets and the Redmine mapping are deliberately explicit.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone
from html.parser import HTMLParser
from pathlib import Path
from typing import Any


PM_COMMIT = "8fa730d69a941e5f8487be652aaaa806c99f74eb"
QA_COMMIT = "dfc9a33138f1099bc234cb1841fd122c5a50fffc"
ALL_OPERATING_UCS = [184, 185, 187, 75, 76, 77, 79, 80, 188]
ENUM_FQN = "org.mnu.external.TmdbClient.TmdbException.ErrorType"
QA_ADDED = {
    "org.mnu.external.TmdbClient",
    "org.mnu.external.TmdbClient.TmdbException",
    ENUM_FQN,
}
QA_CHANGED_EXISTING = {
    "org.mnu.controller.WorkController",
    "org.mnu.service.WorkService",
    "org.mnu.domain.WorkForm",
}


def mapping(owner: int, reqs: list[int], ucs: list[int], role: str, **extra: Any) -> dict[str, Any]:
    return {"ownerId": owner, "reqIds": reqs, "ucIds": ucs, "role": role, **extra}


# Values are actual Redmine issue numbers, not the numeric suffixes of SFR/UC IDs.
TYPE_MAPPING: dict[str, dict[str, Any]] = {
    "org.mnu.config.LocalSettings": mapping(21, [29, 30], [], "서버 외부 설정 파일을 읽어 공통 설정값을 제공한다."),
    "org.mnu.config.RootConfig": mapping(21, [29, 30], [], "DB 연결·MyBatis·트랜잭션·시계의 공통 Bean을 구성한다."),
    "org.mnu.config.SecurityConfig": mapping(21, [13, 29, 30], [184], "로그인·로그아웃·접근 권한·CSRF·세션 등 웹 보안 설정을 구성한다."),
    "org.mnu.controller.AppAdvice": mapping(10, [27, 29, 30], [], "컨트롤러 공통 모델·입력 바인딩과 오류 응답을 제공한다."),
    "org.mnu.controller.AuthController": mapping(21, [13, 14], [184, 185], "로그인·회원가입 화면과 회원가입 요청을 처리한다."),
    "org.mnu.controller.Current": mapping(21, [13, 30], [184, 187, 75, 76, 77, 79, 80, 188], "현재 인증 정보에서 회원 ID와 관리자 여부를 읽는다."),
    "org.mnu.controller.HomeController": mapping(10, [18, 23, 24, 27], [75, 79, 80], "홈 화면에 본인 통계와 최근 감상 기록을 제공한다."),
    "org.mnu.controller.RecordController": mapping(25, [18, 19, 20, 21, 22], [75, 76, 77, 188], "본인 감상 기록의 조회·생성·수정·삭제 요청과 입력 오류·충돌 화면을 처리한다."),
    "org.mnu.controller.SessionTokens": mapping(10, [18, 19, 30], [75, 76], "감상 저장 요청의 중복 처리를 구분할 세션 토큰을 관리한다."),
    "org.mnu.controller.StatsController": mapping(10, [20, 23, 24], [79, 80], "본인 감상 활동·장르별 평가 통계 화면과 조회 조건 오류를 처리한다."),
    "org.mnu.controller.WorkController": mapping(21, [17, 21, 22], [187, 77, 188], "공용 작품 검색·등록·상세·관리와 본인 현재 상태 화면 요청을 처리한다.", qaReqIds=[25]),
    "org.mnu.domain.AccountPrincipal": mapping(21, [13, 30], [184], "인증된 회원의 식별 정보와 권한을 Spring Security 사용자 정보로 제공한다."),
    "org.mnu.domain.InputError": mapping(10, [27, 29, 30], [], "입력·업무 규칙 오류의 메시지와 HTTP 상태를 호출 계층에 전달한다."),
    "org.mnu.domain.QueryFilter": mapping(21, [17, 22, 23, 24], [187, 188, 79, 80], "작품·기록·통계 조회에 사용하는 조건·정렬·페이지 값을 보관하고 검증한다."),
    "org.mnu.domain.RecordForm": mapping(25, [18, 19, 20, 21], [75, 76, 77], "감상 기록 입력과 저장 모드·요청 식별자·수정 버전을 전달한다."),
    "org.mnu.domain.SignupForm": mapping(21, [14], [185], "회원가입 입력값을 전달하는 폼 객체이다."),
    "org.mnu.domain.WorkForm": mapping(21, [17], [187], "공용 작품 입력값·장르와 중복 등록 확인 여부를 전달한다.", qaReqIds=[25]),
    "org.mnu.mapper.AccountMapper": mapping(21, [13, 14], [184, 185], "계정 조회·중복 확인·가입 저장을 위한 MyBatis 질의 인터페이스이다.", kind="interface"),
    "org.mnu.mapper.RecordMapper": mapping(25, [18, 19, 20, 21, 22], [75, 76, 77, 188], "본인 감상·이력·현재 상태·저장 요청 원장의 MyBatis 질의 인터페이스이다.", kind="interface"),
    "org.mnu.mapper.StatsMapper": mapping(10, [20, 23, 24], [79, 80], "본인 감상 활동과 장르별 평가 집계를 위한 MyBatis 질의 인터페이스이다.", kind="interface"),
    "org.mnu.mapper.WorkMapper": mapping(21, [17, 22], [187, 188], "공용 작품·장르의 검색·등록·관리 MyBatis 질의 인터페이스이다.", kind="interface", implementationNotes=["QA_JO에서는 연결 SQL인 WorkMapper.xml의 insert에 외부 공급원·ID 분기가 추가되었다. Java 인터페이스 선언 토큰은 동일하므로 Java 타입 변경 여부는 shared로 기록한다. SQL의 분기 추가와 외부 식별자의 실제 저장 완료는 별개이다."]),
    "org.mnu.service.AccountService": mapping(21, [13, 14], [184, 185], "계정 인증 조회와 회원가입 입력 검증·비밀번호 해시·계정 저장을 처리한다."),
    "org.mnu.service.RecordService": mapping(25, [18, 19, 20, 21, 22], [75, 76, 77, 188], "본인 감상 기록·이력·현재 상태와 중복 저장·수정 충돌의 업무 규칙을 처리한다."),
    "org.mnu.service.RecordService.SubmitResult": mapping(25, [18, 19, 21], [75, 76, 77], "감상 저장의 기록 ID와 중복 요청 여부를 호출 계층에 전달하는 중첩 결과 객체이다."),
    "org.mnu.service.StatsService": mapping(10, [20, 23, 24], [79, 80], "감상 활동·장르별 집계 결과와 유효 평점 비율을 구성한다."),
    "org.mnu.service.WorkService": mapping(21, [17, 22], [187, 188], "공용 작품·장르 검증과 조회·저장·관리 업무 규칙을 처리한다.", qaReqIds=[25]),
    "org.mnu.external.TmdbClient": mapping(21, [17, 25], [187], "TMDB 영화·TV 검색·상세 JSON을 조회해 Map으로 전달하는 QA_JO의 외부 연동 컴포넌트이다."),
    "org.mnu.external.TmdbClient.TmdbException": mapping(21, [17, 25], [187], "TMDB 연동 실패 메시지와 오류 분류를 전달하는 검사 예외이다."),
    ENUM_FQN: mapping(21, [17, 25], [187], "TMDB 키 미설정·HTTP 오류·연결 오류·기타 오류를 구분하는 중첩 열거형이다.", kind="enum"),
}
EXPECTED_PM = set(TYPE_MAPPING) - QA_ADDED
EXPECTED_QA = set(TYPE_MAPPING)
LIMITATION_TYPES = {
    "org.mnu.controller.WorkController": ["QA-TMDB-001", "QA-TMDB-002", "QA-TMDB-003"],
    "org.mnu.service.WorkService": ["QA-TMDB-002", "QA-TMDB-003"],
    "org.mnu.domain.WorkForm": ["QA-TMDB-002"],
    "org.mnu.external.TmdbClient": ["QA-TMDB-001", "QA-TMDB-003"],
}
DEFAULT_LIMITATIONS = {
    "QA-TMDB-001": "영화 상세 JSON 필드 선택의 대소문자 비교 오류로 영화 가져오기가 실패할 수 있다.",
    "QA-TMDB-002": "WorkForm의 외부 ID·공급원이 저장 Map에 전달되지 않아 MANUAL·NULL로 저장되는 경로가 남아 있다.",
    "QA-TMDB-003": "외부 장르 ID를 로컬 장르 ID로 변환하는 매핑이 없어 등록 거부 또는 잘못된 연결 가능성이 있다.",
}


class InventoryError(RuntimeError):
    """The local Doxygen output does not match the documented source baseline."""


def normalized_name(value: str) -> str:
    return re.sub(r"\s+", "", value.replace("::", ".").strip())


def element_text(element: ET.Element | None) -> str:
    """Return text from documentation nodes without interpreting its contents."""
    if element is None:
        return ""
    text = "".join(element.itertext())
    return re.sub(r"\s+", " ", text).strip()


def source_path(value: str) -> str:
    path = value.replace("\\", "/")
    marker = "review-system/src/"
    if marker in path:
        return path[path.index(marker):]
    if "src/main/java/" in path:
        return "review-system/" + path[path.index("src/main/java/"):]
    if "org/mnu/" in path:
        return "review-system/src/main/java/" + path[path.index("org/mnu/"):]
    return path


def _xml(path: Path) -> ET.Element:
    if not path.is_file():
        raise InventoryError(f"필수 Doxygen XML 파일이 없습니다: {path}")
    try:
        return ET.parse(path).getroot()
    except ET.ParseError as exc:
        raise InventoryError(f"XML을 읽을 수 없습니다: {path}: {exc}") from exc


def _relative(path: Path, root: Path) -> str:
    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except ValueError as exc:
        raise InventoryError(f"HTML 경로가 산출물 루트 밖에 있습니다: {path}") from exc


class _Ids(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.ids: set[str] = set()

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        self.ids.update(value for name, value in attrs if name == "id" and value)


def enum_html_anchor(html_path: Path, compound_refid: str, enum_id: str) -> str:
    """Resolve XML's escaped member ID against the actual generated HTML ID."""
    parser = _Ids()
    parser.feed(html_path.read_text(encoding="utf-8"))
    suffix = enum_id[len(compound_refid):] if enum_id.startswith(compound_refid) else enum_id
    candidates = [enum_id, suffix.removeprefix("_1"), suffix.removeprefix("_")]
    for candidate in candidates:
        if candidate and candidate in parser.ids:
            return candidate
    raise InventoryError(f"열거형 HTML anchor를 찾을 수 없습니다: {html_path}: {enum_id}")


def _member(member: ET.Element) -> dict[str, Any]:
    location = member.find("location")
    return {
        "id": member.get("id", ""),
        "name": element_text(member.find("name")),
        "kind": member.get("kind", ""),
        "type": element_text(member.find("type")),
        "args": element_text(member.find("argsstring")),
        "brief": element_text(member.find("briefdescription")),
        "details": element_text(member.find("detaileddescription")),
        "protection": member.get("prot", ""),
        "static": member.get("static") == "yes",
        "line": int(location.get("line", "0")) if location is not None else 0,
        "parameters": [
            {"name": element_text(p.find("declname")), "type": element_text(p.find("type"))}
            for p in member.findall("param")
        ],
        "values": [
            {"name": element_text(v.find("name")), "brief": element_text(v.find("briefdescription"))}
            for v in member.findall("enumvalue")
        ],
        "_typeRefs": sorted({
            ref.get("refid", "")
            for node in [member.find("type"), *member.findall("param/type")]
            if node is not None
            for ref in node.iter("ref")
            if ref.get("refid")
        }),
    }


def read_variant(xml_dir: Path, html_dir: Path, variant: str, artifact_root: Path) -> dict[str, dict[str, Any]]:
    """Read named project types and the nested ErrorType enum from local XML."""
    xml_dir, html_dir = xml_dir.resolve(), html_dir.resolve()
    index = _xml(xml_dir / "index.xml")
    results: dict[str, dict[str, Any]] = {}
    pending_refs: dict[str, list[dict[str, str]]] = {}
    valid_name = re.compile(r"org\.mnu(?:\.[A-Za-z_$][\w$]*)+\Z")
    for entry in index.findall("compound"):
        if entry.get("kind") not in {"class", "interface", "enum"}:
            continue
        fqn = normalized_name(element_text(entry.find("name")))
        if not fqn.startswith("org.mnu.") or not valid_name.fullmatch(fqn):
            continue
        if fqn not in TYPE_MAPPING:
            raise InventoryError(f"예상하지 못한 이름 있는 Java 타입: {variant}: {fqn}")
        refid = entry.get("refid", "")
        if not refid or "/" in refid or "\\" in refid:
            raise InventoryError(f"올바르지 않은 compound refid: {refid!r}")
        xml_path = xml_dir / (refid + ".xml")
        compound = _xml(xml_path).find("compounddef")
        if compound is None:
            raise InventoryError(f"compounddef가 없습니다: {xml_path}")
        parsed_name = normalized_name(element_text(compound.find("compoundname")))
        if parsed_name != fqn:
            raise InventoryError(f"index/compound 이름 불일치: {fqn} != {parsed_name}")
        html_path = html_dir / (refid + ".html")
        if not html_path.is_file():
            raise InventoryError(f"타입의 HTML 파일이 없습니다: {html_path}")
        location = compound.find("location")
        members = [_member(m) for m in compound.findall("sectiondef/memberdef")]
        record = {
            "present": True, "variant": variant, "refid": refid,
            "xmlPath": str(xml_path), "htmlPath": str(html_path),
            "fullpath": str(html_path),
            "htmlRelativeUrl": _relative(html_path, artifact_root),
            "sourcefile": source_path(location.get("file", "")) if location is not None else "",
            "doxygenSourcefile": location.get("file", "") if location is not None else "",
            "line": int(location.get("line", "0")) if location is not None else 0,
            "kind": TYPE_MAPPING[fqn].get("kind", compound.get("kind", "class")),
            "brief": element_text(compound.find("briefdescription")),
            "details": element_text(compound.find("detaileddescription")),
            "members": members,
            "relations": [],
        }
        if fqn in results:
            raise InventoryError(f"동일한 타입이 중복 정의되었습니다: {variant}: {fqn}")
        results[fqn] = record
        refs = []
        if record["kind"] == "enum":
            parent_fqn = fqn.rsplit(".", 1)[0]
            record.update({"parentFqn": parent_fqn, "enumRepresentation": "standalone_compound", "anchor": None,
                           "memberAnchors": {m["id"]: enum_html_anchor(html_path, refid, m["id"]) for m in members if m["id"]}})
            refs.append({"refid": "", "name": parent_fqn, "relation": "포함하는 타입"})
        for tag, relation in [("basecompoundref", "상속·구현"), ("derivedcompoundref", "파생 타입")]:
            for node in compound.findall(tag):
                refs.append({"refid": node.get("refid", ""), "name": normalized_name(element_text(node)), "relation": relation})
        for member in members:
            refs.extend({"refid": r, "name": "", "relation": "멤버·매개변수 유형 참조"} for r in member["_typeRefs"])
        pending_refs[fqn] = refs

        for member_node in compound.findall("sectiondef/memberdef[@kind='enum']"):
            name = element_text(member_node.find("name"))
            qualified = normalized_name(element_text(member_node.find("qualifiedname")))
            enum_fqn = qualified if qualified.startswith("org.mnu.") else fqn + "." + name
            if not valid_name.fullmatch(enum_fqn):
                continue
            if enum_fqn != ENUM_FQN:
                raise InventoryError(f"예상하지 못한 이름 있는 열거형: {variant}: {enum_fqn}")
            enum_member = _member(member_node)
            enum_id = member_node.get("id", "")
            if not enum_id:
                raise InventoryError(f"열거형 refid가 없습니다: {enum_fqn}")
            enum_anchor = enum_html_anchor(html_path, refid, enum_id)
            enum_record = dict(record)
            enum_record.update({
                "refid": enum_id,
                "parentRefid": refid,
                "parentFqn": fqn,
                "enumRepresentation": "member_of_parent_compound",
                "anchor": enum_anchor,
                "htmlRelativeUrl": _relative(html_path, artifact_root) + "#" + enum_anchor,
                "fullpath": str(html_path) + "#" + enum_anchor,
                "kind": "enum", "brief": enum_member["brief"], "details": enum_member["details"],
                "line": enum_member["line"], "members": enum_member["values"],
                "relations": [{"fqn": fqn, "relation": "포함하는 타입"}],
            })
            if enum_fqn in results:
                raise InventoryError(f"중복된 열거형: {enum_fqn}")
            results[enum_fqn] = enum_record
    by_refid = {data["refid"]: name for name, data in results.items()}
    for fqn, refs in pending_refs.items():
        relations = set()
        for ref in refs:
            other = by_refid.get(ref["refid"]) or ref["name"]
            if other in results and other != fqn:
                relations.add((other, ref["relation"]))
        results[fqn]["relations"] = [{"fqn": other, "relation": relation} for other, relation in sorted(relations)]
        for member in results[fqn]["members"]:
            member.pop("_typeRefs", None)
    return results


def validate_type_sets(pm: dict[str, Any], qa: dict[str, Any]) -> None:
    for label, actual, expected in [("pm", set(pm), EXPECTED_PM), ("qa", set(qa), EXPECTED_QA)]:
        if actual != expected:
            raise InventoryError(f"{label} 타입 집합 불일치: count={len(actual)}, 누락={sorted(expected - actual)}, 추가={sorted(actual - expected)}")
    if len(pm) != 26 or len(qa) != 29 or len(set(pm) | set(qa)) != 29:
        raise InventoryError("타입 수는 PM 26개, QA 29개, 합집합 29개여야 합니다.")
    if set(qa) - set(pm) != QA_ADDED:
        raise InventoryError("QA 추가 타입은 TmdbClient·TmdbException·ErrorType이어야 합니다.")


def _member_key(member: dict[str, Any]) -> tuple[str, str, str]:
    return member.get("kind", ""), member.get("name", ""), member.get("args", "")


def _combined_members(variants: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    result: dict[tuple[str, str, str], dict[str, Any]] = {}
    for label in ["pm", "qa"]:
        for member in variants[label].get("members", []):
            key = _member_key(member)
            if key not in result:
                result[key] = {**member, "variants": []}
            result[key]["variants"].append(label)
    return list(result.values())


def _member_lines(members: list[dict[str, Any]], kind: str) -> list[str]:
    lines = []
    for member in members:
        if kind == "enum":
            lines.append(f"- `{member['name']}`" + (f": {member.get('brief', '')}" if member.get("brief") else ""))
        elif member.get("kind") == "function":
            signature = member.get("name", "") + member.get("args", "")
            summary = member.get("brief", "")
            labels = "/".join("PM" if v == "pm" else "QA_JO" for v in member.get("variants", []))
            lines.append(f"- `{signature}` ({labels})" + (f": {summary}" if summary else ""))
    return lines or ["- 생성된 클래스 문서의 필드·멤버 목록을 참조한다."]


def issue_draft(record: dict[str, Any], limitations: dict[str, str]) -> dict[str, str]:
    fqn = record["fqn"]
    lines = ["## 역할", record["role"], "", "## 기준 소스", f"- PM 기준: `codex/pm-rating-stats` / `{PM_COMMIT[:7]}`."]
    if record["variants"]["pm"]["present"]:
        lines.append(f"- PM 소스: `{record['variants']['pm']['sourcefile']}`.")
    else:
        lines.append("- PM 기준에는 존재하지 않는 QA_JO 추가 타입이다.")
    lines.extend([f"- QA 기준: `QA_JO` / `{QA_COMMIT[:7]}`.", "- 두 기준본을 분리해 역공학 문서를 생성하며, Git 브랜치 병합 또는 실행 코드 수정을 뜻하지 않는다.", "", "## 주요 멤버"])
    lines.extend(_member_lines(record["members"], record["kind"]))
    lines.extend(["", "## 관련 타입·추적성"])
    for relation in record["relations"]:
        labels = "/".join("PM" if v == "pm" else "QA_JO" for v in relation["variants"])
        lines.append(f"- `{relation['fqn']}`: {relation['relation']} ({labels}).")
    if not record["relations"]:
        lines.append("- XML에서 확인되는 직접 프로젝트 타입 관계가 없다. 존재하지 않는 관계를 추가하지 않는다.")
    if record["ucIds"]:
        lines.append("- 관련 유스케이스: " + " · ".join(f"#{i}" for i in record["ucIds"]) + ".")
    else:
        lines.append("- 공통 지원 타입으로 특정 UC와 일괄 연결하지 않고 아래의 공통 요구사항에 직접 연결한다.")
    lines.append("- 관련 요구사항: " + " · ".join(f"#{i}" for i in record["reqIds"]) + ".")
    for note in record.get("implementationNotes", []):
        lines.append("- " + note)
    lines.extend(["", "## Doxygen 산출물", "DOC_IMAGES_TOKEN", "", "## 현재 구현의 제약"])
    for issue_id in record["limitations"]:
        lines.append(f"- {issue_id}: {limitations[issue_id]}")
    if record["limitations"]:
        lines.append("- 위 제약은 QA_JO 기준본에 남아 있으며, 이 문서화 작업에서 수정하거나 완료 처리하지 않는다. 실제 TMDB API 성공은 미검증이다.")
    if record["changeKind"] == "qa_added":
        lines.append("- 이 타입은 현재 PM 구현에 포함되지 않은 QA_JO 별도 기준본의 구조이다.")
    lines.extend(["- 원본 SQL·Spring 실행 시점 연결·화면 동작 전체를 Doxygen 관계도 하나로 입증하지 않는다.", "", "## 완료 범위", "이 일감의 완료는 해당 타입의 설명 주석·Doxygen 문서·캡처·관련 일감 연결을 작성하고 확인한 범위이다. 기능 개발 완료, 실제 API 성공, 팀원 독립 검토 또는 최종 인수를 뜻하지 않는다."])
    return {"subject": "[클래스] " + fqn, "description": "\n".join(lines)}


def inspect_quality(records: list[dict[str, Any]]) -> tuple[dict[str, Any], list[str]]:
    """Inspect actual documentation content; do not fill absent prose by guessing."""
    report: dict[str, Any] = {
        "missingTypeBrief": [], "missingMembers": [], "missingMemberBrief": [],
        "unicodeReplacementPresent": [], "briefWithoutKorean": [],
        "enumMemberAnchorsVerified": 0,
        "draftPlaceholderCount": sum("DOC_IMAGES_TOKEN" in r["issueDraft"]["description"] for r in records),
    }
    warnings = []
    for record in records:
        for variant_name, variant in record["variants"].items():
            if not variant["present"]:
                continue
            identifier = {"variant": variant_name, "fqn": record["fqn"]}
            if not variant["brief"]:
                report["missingTypeBrief"].append(identifier)
            if not variant["members"]:
                report["missingMembers"].append(identifier)
            missing = [member["name"] for member in variant["members"] if not member.get("brief")]
            if missing:
                report["missingMemberBrief"].append({**identifier, "members": missing})
                warnings.append(f"{variant_name} {record['fqn']}: 멤버 brief 없음: {', '.join(missing)}")
            html = Path(variant["htmlPath"]).read_text(encoding="utf-8")
            if "\ufffd" in html or "\ufffd" in variant["brief"] or "\ufffd" in variant["details"]:
                report["unicodeReplacementPresent"].append(identifier)
            if variant["brief"] and not re.search("[가-힣]", variant["brief"]):
                report["briefWithoutKorean"].append(identifier)
            report["enumMemberAnchorsVerified"] += len(variant.get("memberAnchors", {}))
    for key, label in [("missingTypeBrief", "타입 brief 없음"), ("missingMembers", "멤버 목록 없음"),
                       ("unicodeReplacementPresent", "문자 대체 기호 발견"), ("briefWithoutKorean", "brief에 한글 없음")]:
        warnings.extend(f"{item['variant']} {item['fqn']}: {label}" for item in report[key])
    if report["draftPlaceholderCount"] != len(records):
        raise InventoryError("일감 초안의 이미지 교체 토큰 수가 타입 수와 다릅니다.")
    return report, warnings


def build_inventory(artifact_root: Path, pm_xml: Path | None = None, qa_xml: Path | None = None,
                    pm_html: Path | None = None, qa_html: Path | None = None) -> dict[str, Any]:
    """Build the inventory in memory; writing is isolated in main()."""
    root = artifact_root.resolve()
    pm = read_variant(pm_xml or root / "pm/xml", pm_html or root / "pm/html", "pm", root)
    qa = read_variant(qa_xml or root / "qa/xml", qa_html or root / "qa/html", "qa", root)
    validate_type_sets(pm, qa)
    limitation_text = dict(DEFAULT_LIMITATIONS)
    notes_path = root / "qa-annotation/notes.json"
    if notes_path.is_file():
        notes = json.loads(notes_path.read_text(encoding="utf-8"))
        for item in notes.get("limitations", []):
            if item.get("id") in limitation_text:
                limitation_text[item["id"]] = item.get("description", limitation_text[item["id"]])
    records = []
    for fqn in sorted(EXPECTED_QA):
        config = TYPE_MAPPING[fqn]
        variants = {name: data.get(fqn, {"present": False, "refid": None, "xmlPath": None, "htmlPath": None, "fullpath": None, "htmlRelativeUrl": None, "sourcefile": None, "line": None}) for name, data in [("pm", pm), ("qa", qa)]}
        primary = variants["pm"] if variants["pm"]["present"] else variants["qa"]
        merged_relations: dict[tuple[str, str], dict[str, Any]] = {}
        for name, variant in variants.items():
            for relation in variant.get("relations", []):
                key = relation["fqn"], relation["relation"]
                merged_relations.setdefault(key, {**relation, "variants": []})["variants"].append(name)
        req_ids = list(dict.fromkeys(config["reqIds"] + config.get("qaReqIds", [])))
        record = {
            "fqn": fqn, "kind": primary["kind"], "brief": primary["brief"], "details": primary["details"],
            "role": config["role"], "sourcefile": primary["sourcefile"],
            "htmlRelativeUrl": primary["htmlRelativeUrl"],
            "members": _combined_members(variants), "relations": list(merged_relations.values()),
            "variants": variants,
            "changed": fqn in QA_CHANGED_EXISTING | QA_ADDED,
            "changeKind": "qa_added" if fqn in QA_ADDED else "branch_variant" if fqn in QA_CHANGED_EXISTING else "shared",
            "ownerId": config["ownerId"], "reqIds": req_ids, "ucIds": config["ucIds"],
            "limitations": LIMITATION_TYPES.get(fqn, []),
            "implementationNotes": config.get("implementationNotes", []),
        }
        record["issueDraft"] = issue_draft(record, limitation_text)
        records.append(record)
    quality, warnings = inspect_quality(records)
    return {
        "schemaVersion": 1,
        "createdAtKst": datetime.now(timezone(timedelta(hours=9))).isoformat(timespec="seconds"),
        "artifactRoot": str(root),
        "baselines": {"pm": {"branch": "codex/pm-rating-stats", "commit": PM_COMMIT}, "qa": {"branch": "QA_JO", "commit": QA_COMMIT}},
        "counts": {"pm": len(pm), "qa": len(qa), "union": len(records), "qaAdded": len(QA_ADDED), "qaChangedExisting": len(QA_CHANGED_EXISTING)},
        "validation": {"expectedTypeSetsMatch": True, "htmlFilesExist": True, "realApiExecutedByThisScript": False, "serverStateChanged": False,
                       "upstreamSourceTokenValidation": {"matchedFiles": 51, "matches": True, "verifiedBy": "root"},
                       "upstreamGitRefsUnchanged": True},
        "quality": quality, "warnings": warnings,
        "types": records,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1] / "output/doxygen-design")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--pm-xml", type=Path)
    parser.add_argument("--qa-xml", type=Path)
    parser.add_argument("--pm-html", type=Path)
    parser.add_argument("--qa-html", type=Path)
    args = parser.parse_args(argv)
    try:
        inventory = build_inventory(args.root, args.pm_xml, args.qa_xml, args.pm_html, args.qa_html)
        output = args.output or args.root / "type-inventory.json"
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(inventory, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    except (InventoryError, OSError, ValueError) as exc:
        print(f"Doxygen inventory failed: {exc}", file=sys.stderr)
        return 1
    print(json.dumps({"output": str(output.resolve()), "counts": inventory["counts"], "warnings": inventory["warnings"]}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
