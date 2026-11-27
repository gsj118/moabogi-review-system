"""Prepare reviewable Redmine branch-status updates from saved UI snapshots.

This module writes a local JSON artifact only. It makes no network requests,
changes no Git refs, and does not edit an issue or Wiki page on the server.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path


DATE = "2026-11-17"
HEADING = f"## {DATE} 브랜치 구현 확인"
ISSUE_IDS = (13, 14, 17, 22, 25, 69, 81, 84, 87, 88)
WIKI_TITLES = ("개발_최종계획_20261104", "시스템_설계서", "시험계획_결과")
REPO = "https://github.com/gsj118/moabogi-review-system"


def read_json(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(value, dict):
        raise ValueError(f"Expected a JSON object: {path}")
    return value


def field(snapshot: dict, element_id: str) -> dict:
    matches = [item for item in snapshot["fields"] if item.get("id") == element_id]
    if len(matches) != 1:
        raise ValueError(f"Expected one saved field {element_id}; found {len(matches)}")
    return matches[0]


def status_label(snapshot: dict) -> str:
    status = field(snapshot, "issue_status_id")
    matches = [item["label"] for item in status["options"] if item["value"] == status["value"]]
    if len(matches) != 1:
        raise ValueError("Cannot resolve the saved issue status label")
    return matches[0]


def digest(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def prepare(base_dir: Path) -> dict:
    issue_snapshots = read_json(base_dir / "redmine-before" / "issues-ui.json")
    wiki_snapshots = read_json(base_dir / "redmine-before" / "wiki-ui.json")
    baselines = read_json(base_dir / "source-baselines.json")
    summary_path = base_dir / "branch-status.md"
    summary = summary_path.read_text(encoding="utf-8-sig")

    if baselines.get("mergeAuthorized") is not False:
        raise ValueError("This preparation requires the saved no-merge baseline")
    pm_sha = baselines["pm"]["commit"]
    qa_sha = baselines["qa"]["commit"]
    main_sha = baselines["main"]
    if not all(re.fullmatch(r"[0-9a-f]{40}", value) for value in (pm_sha, qa_sha, main_sha)):
        raise ValueError("All saved baseline refs must be full commit SHAs")
    if not (pm_sha.startswith("8fa730d") and qa_sha.startswith("dfc9a33") and main_sha.startswith("db056d5")):
        raise ValueError("Unexpected source baselines; review the prepared statements first")
    for fragment in ("QA_JO", "dfc9a33", "8fa730d", "단위시험 2개", "미실행"):
        if fragment not in summary:
            raise ValueError(f"Branch summary is missing the reviewed fact: {fragment}")

    qa_commit = f"[QA_JO {qa_sha[:7]}]({REPO}/commit/{qa_sha})"
    pm_commit = f"[PM {pm_sha[:7]}]({REPO}/commit/{pm_sha})"
    main_commit = f"[main {main_sha[:7]}]({REPO}/commit/{main_sha})"
    movie_source = f"{REPO}/blob/{qa_sha}/review-system/src/main/java/org/mnu/external/TmdbClient.java#L85-L86"
    save_source = f"{REPO}/blob/{qa_sha}/review-system/src/main/java/org/mnu/service/WorkService.java#L33-L38"
    genre_source = f"{REPO}/blob/{qa_sha}/review-system/src/main/java/org/mnu/controller/WorkController.java#L64"
    genre_validation = f"{REPO}/blob/{qa_sha}/review-system/src/main/java/org/mnu/service/WorkService.java#L32"
    defects = (
        f"[영화 상세 필드 선택]({movie_source}), "
        f"[저장 Map의 외부 ID·공급원 전달 누락]({save_source}), "
        f"[외부 장르 ID 전달]({genre_source})과 [로컬 장르 검증]({genre_validation}) 사이의 명시적 매핑 누락"
    )
    upload = (
        f"조우성(duftjd03)의 {qa_commit} 개발본 업로드를 확인했다. "
        "커밋 시각은 2026-11-10 05:19:30 KST이며 메시지는 `1,2,5,10,13번 기능구현`이다."
    )
    dates = (
        "SFR-001·002·005는 11/09, SFR-010은 11/10 구현해 11/10 게시했다는 내용은 본인 보고다. "
        "단일 독립 루트 커밋이므로 항목별 실제 개발일과 게시 시각을 커밋 기록만으로 분리 검증할 수 없다."
    )
    build = (
        "2026-11-17 Codex가 QA 커밋의 별도 임시 사본에서 `build.cmd -B -ntp`를 실행해 "
        "기존 StatsServiceTest 단위시험 2개 통과와 WAR 생성 성공을 독립 확인했다. "
        "이는 해당 일감의 기능별 통합시험이나 실제 TMDB 호출을 통과했다는 뜻이 아니다."
    )
    outstanding = (
        "실제 TMDB 호출, QA 변경의 DB 통합시험과 후속 38개 시험은 미실행이다. "
        "팀원 독립 기능 검토·인수도 완료로 기록하지 않는다. "
        f"정적 검토 보완 항목은 {defects}이다. "
        "[[시험계획_결과|실행 범위·증거·보완 항목]]에서 상세 결과를 관리한다."
    )
    scope = (
        f"현재 PM 작업본은 `구서준_작업`의 {pm_commit} 기준이며 {main_commit}과 QA 개발본을 구분한다. "
        "아래 기존 본문의 현재 구현·TMDB 구현이 남았다는 표현은 PM 작업본 기준으로 읽는다. "
        "QA_JO에는 외부 검색·가져오기 코드가 올라와 있으므로 QA 개발본의 수정·검증이 필요한 상태로 정정한다."
    )
    collaboration = (
        "사용자 확정 협업 규칙: main 관리·반영은 PM 전용이며 각 담당자는 자기 브랜치에서 작업한다. "
        "사용자의 명시적인 병합 지시 전에는 QA_JO를 PM 작업본이나 main에 병합하지 않는다. "
        "접근 권한·브랜치 보호·GitHub 설정을 변경한 것으로 기록하지 않는다."
    )

    specifics = {
        13: "SFR-001의 인증·세션·로그아웃 핵심 코드는 11/03 main과 동일하다. 구현을 포함한 업로드는 확인했으며 신규 개발 기여와 로그인·세션·보안 시험 및 인수 완료는 별도로 확인한다. 기존 진행 상태를 유지한다.",
        14: "SFR-002의 가입 검증·비밀번호 해시·기본 USER 계정 저장 핵심 코드는 11/03 main과 동일하다. 구현을 포함한 업로드와 실제 가입·권한·오류·개인정보 정책 검토 완료를 구분하고 기존 진행 상태를 유지한다.",
        17: "SFR-005·013 영역에서 QA_JO에 TMDB 검색·가져오기와 외부 식별자 입력 경로가 추가되어 있다. 개발본 존재를 반영하되 외부 상세·저장·장르 매핑 결함과 공용 정보 권한·반복 저장 계약을 보완해야 하므로 기존 진행 상태를 유지한다.",
        22: "SFR-010의 QueryFilter·회원 범위·동적 필터·안정 정렬·페이지 보정 핵심 코드와 기존 시험은 11/03 main과 동일하다. 11/10 게시 보고를 새 구현·기능별 시험 완료로 소급하지 않고 기존 진행 상태를 유지한다.",
        25: "PM 작업본에는 TMDB Client가 없지만 QA_JO에는 TmdbClient·검색 화면·가져오기 경로가 존재한다. 업로드된 개발 작업을 반영해 신규에서 진행으로 변경하되 결함 보완·실호출·DB 통합·계약 인수는 남겨 두며 완료 처리하지 않는다.",
        69: collaboration + " QA_JO의 실제 업로드를 협업 산출물로 반영한다. 조우성의 업로드를 확인했으므로 추가 작업이 없었다는 단정은 철회한다. 저장소 내부 SCM 동기화·다른 팀원의 clone/실행·전체 협업 검토 완료까지 확인한 것은 아니므로 기존 진행 상태를 유지한다.",
        81: "설계 기준은 PM Java 25파일·26개 이름 있는 타입과 QA Java 26파일·29개 타입을 분리한다. QA 전용 TmdbClient, TmdbClient.TmdbException, TmdbClient.TmdbException.ErrorType 3개 타입을 PM 통합 구조처럼 표시하지 않는다. PM 소스 설명 주석을 보강했으며 Doxygen 생성물·캡처 게시와 PM/SE 실제 검토 결과는 이후 확인해 추가한다. 지금은 설계 산출물 게시·교차 검토 완료를 주장하지 않고 기존 상태를 유지한다.",
        84: "QA_JO에 외부 검색·가져오기 구현본이 올라왔으므로 보완 작업은 신규에서 진행으로 변경한다. 정적으로 확인한 영화 상세·외부 식별자 전달·장르 매핑 3개 결함을 담당 브랜치에서 보완하고 기존 로컬 기능·권한·반복/동시 저장·롤백을 검증한다. 사용자 지시 전 병합과 완료 처리는 하지 않는다.",
        87: "이번 2개 단위시험은 기존 StatsServiceTest로, T01-01~T01-04 회원가입·인증·세션의 독립 기능시험 수행 결과가 아니다. 기존 신규 상태와 미실행 표시를 유지하고 실제 입력·기대/결과·환경·증거·실행자/검토자·일자를 남길 후속 작업으로 관리한다.",
        88: "QA 외부 검색 추가와 기존 단위시험·WAR 성공은 확인했지만 T02-01~T02-09의 로컬 4개·외부 연동 5개 독립 기능시험은 이번에 실행하지 않았다. 3개 결함 보완 후 합성 응답과 실제 API·DB 검증을 구분해 기록하며 기존 신규 상태를 유지한다.",
    }

    issues = []
    for issue_id in ISSUE_IDS:
        snapshot = issue_snapshots[str(issue_id)]
        original = field(snapshot, "issue_description")["value"]
        if not isinstance(original, str) or not original:
            raise ValueError(f"Missing original description for issue {issue_id}")
        if HEADING in original:
            raise ValueError(f"Issue {issue_id} already contains this dated update")
        current_status = status_label(snapshot)
        desired_status = "진행" if issue_id in (25, 84) else current_status
        allowed_statuses = {item["label"] for item in field(snapshot, "issue_status_id")["options"]}
        if desired_status not in allowed_statuses:
            raise ValueError(f"Unavailable status {desired_status} for issue {issue_id}")
        if issue_id in (25, 84) and current_status not in ("신규", "진행"):
            raise ValueError(f"Review unexpected current status of issue {issue_id}")
        if desired_status == "완료":
            raise ValueError(f"This preparation must not close issue {issue_id}")

        block = "\n\n".join((HEADING, upload, specifics[issue_id], dates, build, outstanding, scope))
        description = block + "\n\n---\n\n" + original
        notes = "\n\n".join((
            f"{DATE} 브랜치 구현 확인 결과를 추가한다. 기존 본문과 과거 수행 이력은 보존한다.",
            specifics[issue_id], upload, dates, build, outstanding,
            "담당자·기존 일정·진척률은 변경하지 않는다. 업로드·소스 대조·제한된 빌드 성공과 기능 시험·독립 검토·인수를 구분한다.",
        ))
        assert description.endswith(original)
        issues.append({
            "id": issue_id,
            "originalDescription": original,
            "description": description,
            "notes": notes,
            "statusLabel": desired_status,
        })

    page_specifics = {
        "개발_최종계획_20261104": collaboration + " Redmine 기록만으로 팀원의 추가 미작업을 단정한 판단을 철회한다. QA 업로드와 본인 보고 날짜를 구분하고 현재 분담·기존 검토 조건은 유지한다.",
        "시스템_설계서": "이 페이지의 기존 구조·ERD·미구현/예정 설명은 PM 작업본 기준이다. PM의 25파일·26타입과 QA의 26파일·29타입을 구분하며 QA 전용 TmdbClient·TmdbException·ErrorType 3타입을 통합 운영 구조로 표시하지 않는다. Doxygen 클래스 산출물과 캡처의 실제 게시·검증 완료는 별도 결과로 추가한다.",
        "시험계획_결과": "11/17 확인한 QA 빌드의 기존 StatsServiceTest 2개 통과와 WAR 생성은 후속 시험 38개 또는 T01/T02의 기능별 실행 실적이 아니다. 원래 사례별 미실행 표시와 독립 검토·인수 조건을 유지하고, 정적 검토의 결함과 실제 API/DB에서 재현한 결과를 구분한다.",
    }
    additions = []
    original_wiki_hashes = {}
    for title in WIKI_TITLES:
        original_text = wiki_snapshots[title]["text"]
        if not isinstance(original_text, str) or not original_text:
            raise ValueError(f"Missing original Wiki text: {title}")
        if HEADING in original_text:
            raise ValueError(f"Wiki already contains this dated update: {title}")
        original_wiki_hashes[title] = digest(original_text)
        append_text = "\n\n" + "\n\n".join((HEADING, page_specifics[title], upload, dates, build, outstanding)) + "\n"
        assert (original_text + append_text).startswith(original_text)
        additions.append({"title": title, "appendText": append_text})

    return {
        "issues": issues,
        "wikiAdditions": additions,
        "state": {
            "date": DATE,
            "mergeAuthorized": False,
            "collaborationRule": collaboration,
            "sourceBaselines": baselines,
            "branchSummarySha256": digest(summary),
            "originalWikiTextSha256": original_wiki_hashes,
            "notes": [
                "로컬 게시 준비용 JSON이며 서버에 업데이트하는 처리는 포함하지 않는다.",
                "25·84만 진행으로 변경을 권장하고 나머지 대상은 저장된 기존 상태를 유지한다.",
                "담당자·기존 일정·진척률·Wiki 부모 계층은 이 JSON에서 변경하지 않는다.",
                "게시 전 실페이지의 최신 본문과 스냅샷을 대조하고 다른 사람의 편집을 덮어쓰지 않는다.",
            ],
        },
    }


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-dir", type=Path, default=Path(__file__).resolve().parents[1] / "output" / "doxygen-design")
    args = parser.parse_args()
    result = prepare(args.base_dir)
    output = args.base_dir / "branch-updates.json"
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    reloaded = read_json(output)
    if len(reloaded["issues"]) != len(ISSUE_IDS) or len(reloaded["wikiAdditions"]) != len(WIKI_TITLES):
        raise ValueError("Unexpected output record counts")
    print(json.dumps({"output": str(output), "issues": len(ISSUE_IDS), "wikiAdditions": len(WIKI_TITLES), "serverUpdated": False}, ensure_ascii=False))


if __name__ == "__main__":
    main()
