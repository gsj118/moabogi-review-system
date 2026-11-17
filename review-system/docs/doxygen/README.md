# Doxygen 재생성 입력

이 디렉터리는 문서 생성에 필요한 QA 전용 설명 주석 원본을 보관한다. `qa-annotation/TmdbClient.java`는 `QA_JO`의 `dfc9a33138f1099bc234cb1841fd122c5a50fffc` 소스에 설명 주석만 추가한 문서화 입력이다. Maven의 운영 소스 경로 밖에 있으며 PM 애플리케이션에 컴파일되거나 QA 기능을 병합하지 않는다. 실제 API 키·개인 설정은 포함하지 않는다.

PM 입력은 저장소의 `review-system/src/main/java/`에서 읽고, QA의 기존 타입은 원본 Git 객체에 PM 설명 주석과 QA 추가 멤버 설명을 대응시킨다. 생성기는 문자열·문자 리터럴을 포함한 모든 실행 토큰이 각 고정 커밋과 같은지 확인한 후 결과를 쓴다.

## 새 PC에서 준비

작업 브랜치를 받은 저장소와 Git, Python 3.10 이상이 필요하다. 원본 커밋 객체가 있어야 하며 기본값은 PM `8fa730d69a941e5f8487be652aaaa806c99f74eb`, QA `dfc9a33138f1099bc234cb1841fd122c5a50fffc`다. shallow clone이라 PM의 이전 커밋 객체가 없다면 `git fetch --unshallow origin`으로 이력을 확보한다. 단일 브랜치 clone에서 QA 객체가 없다면 다음과 같이 해당 브랜치를 가져온다. 이 명령들은 브랜치를 병합하지 않는다.

```powershell
git fetch origin QA_JO
```

저장소 최상위에서 소스 준비와 실행 토큰 검증만 하려면 다음 명령을 사용한다. 이 단계에는 Doxygen·Graphviz가 필요하지 않다.

```powershell
.\generate-doxygen.ps1 -PrepareOnly
```

HTML·XML까지 생성하려면 Doxygen과 Graphviz를 설치하고 실행 파일이 PATH에 있도록 하거나 `DOXYGEN_EXECUTABLE`·`DOT_EXECUTABLE` 환경변수에 각각의 절대경로를 지정한다. 이번 산출물은 Doxygen 1.18.0·Graphviz 16.1.1을 사용했다. Python을 자동으로 찾지 못하면 `DOXYGEN_PYTHON`에 `python.exe` 경로를 지정한다.

```powershell
.\generate-doxygen.ps1
# 다른 출력 디렉터리에도 같은 버전 관리 입력을 사용한다.
.\generate-doxygen.ps1 -OutputBase 'C:\work\moabogi-doxygen-output'
```

기본 결과는 `output/doxygen-design/`에 생긴다. 생성 HTML/XML·주석 패치·검증 메타데이터는 출력물이고, 이 디렉터리의 QA 주석 원본은 입력이다. 기존 `output/doxygen-design/qa-annotation/`을 새 PC에 별도로 복사할 필요는 없다. `source-validation.json`에서 PM 25파일·QA 26파일의 토큰 일치와 Git ref 유지, 두 HTML/XML 생성 결과를 확인한다. 기능 수정 뒤 원본 커밋과 토큰이 달라지면 생성기는 실패하며, 문서 기준 커밋과 주석을 함께 갱신해야 한다.
