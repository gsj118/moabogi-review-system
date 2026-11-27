<#
.SYNOPSIS
고정된 PM·QA 원본과 실행 토큰이 같은 주석본을 준비하고 Doxygen을 생성합니다.
.DESCRIPTION
병합·체크아웃·커밋을 수행하지 않습니다. Python 3.10 이상, Doxygen, Graphviz가
필요하며 설치된 도구는 output/doxygen-design/toolchain.json 또는 실행 경로로 찾습니다.
-PrepareOnly는 Doxygen·Graphviz 없이 Python과 Git으로 소스·주석 보존을 검증합니다.
#>
[CmdletBinding()]
param(
    [string]$OutputBase,
    [string]$PmRef,
    [string]$QaRef,
    [switch]$PrepareOnly
)

$ErrorActionPreference = 'Stop'
$candidates = [System.Collections.Generic.List[object]]::new()
if ($env:DOXYGEN_PYTHON) {
    $candidates.Add(@{ Command = $env:DOXYGEN_PYTHON; Prefix = @() })
}
foreach ($name in @('python', 'python3', 'py')) {
    $command = Get-Command $name -ErrorAction SilentlyContinue
    if ($command) {
        $prefix = if ($name -eq 'py') { @('-3') } else { @() }
        $candidates.Add(@{ Command = $command.Source; Prefix = $prefix })
    }
}

# Codex 번들 런타임도 탐색합니다. 설치 버전과 사용자 이름은 고정하지 않습니다.
if (Get-Command Get-AppxPackage -ErrorAction SilentlyContinue) {
    foreach ($package in @(Get-AppxPackage -Name 'OpenAI.CodexPrimaryRuntime*' -ErrorAction SilentlyContinue)) {
        $candidate = Join-Path $package.InstallLocation 'dependencies\python\python.exe'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            $candidates.Add(@{ Command = $candidate; Prefix = @() })
        }
    }
}

$selectedPython = $null
foreach ($candidate in $candidates) {
    try {
        $probeArgs = @($candidate.Prefix) + @('-c', 'import sys; raise SystemExit(0 if sys.version_info >= (3, 10) else 1)')
        & $candidate.Command @probeArgs 2>$null
        if ($LASTEXITCODE -eq 0) { $selectedPython = $candidate; break }
    } catch {
        # 다음 설치된 인터프리터 후보를 확인합니다.
    }
}
if (-not $selectedPython) {
    throw 'Python 3.10 이상을 찾지 못했습니다. Python을 설치하거나 DOXYGEN_PYTHON 환경변수에 python.exe의 절대경로를 지정하세요.'
}

$arguments = @($selectedPython.Prefix) + @('-X', 'utf8', (Join-Path $PSScriptRoot '.setup\doxygen_design.py'))
if ($OutputBase) { $arguments += @('--output-base', $OutputBase) }
if ($PmRef) { $arguments += @('--pm-ref', $PmRef) }
if ($QaRef) { $arguments += @('--qa-ref', $QaRef) }
if ($PrepareOnly) { $arguments += '--prepare-only' }
& $selectedPython.Command @arguments
exit $LASTEXITCODE
