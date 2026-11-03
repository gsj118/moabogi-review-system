param([switch]$Verify)
$ErrorActionPreference = 'Stop'
$taskEnv = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'environment.json') -Raw | ConvertFrom-Json
$taskPlugins = Join-Path $taskEnv.sts 'plugins'
$taskClasses = Join-Path $PSScriptRoot 'bootstrap\classes'
New-Item -ItemType Directory -Path $taskClasses -Force | Out-Null
$taskPatterns = @('org.eclipse.equinox.app_*.jar','org.eclipse.core.runtime_*.jar','org.eclipse.core.resources_*.jar','org.eclipse.core.jobs_*.jar','org.eclipse.equinox.common_*.jar','org.eclipse.equinox.preferences_*.jar','org.eclipse.equinox.registry_*.jar','org.eclipse.osgi_*.jar','org.eclipse.jdt.core_*.jar','org.eclipse.jdt.launching_*.jar','org.eclipse.wst.server.core_*.jar','org.eclipse.jst.server.core_*.jar','org.eclipse.jst.server.tomcat.core_*.jar','org.eclipse.m2e.core_*.jar','org.springsource.ide.eclipse.commons.content.core_*.jar')
$taskJars = foreach ($taskPattern in $taskPatterns) { (Get-ChildItem -LiteralPath $taskPlugins -Filter $taskPattern).FullName }
$taskClasspath = $taskJars -join ';'
& (Join-Path $taskEnv.jdk 'bin\javac.exe') -encoding UTF-8 -cp $taskClasspath -d $taskClasses (Join-Path $PSScriptRoot 'bootstrap\src\team\e\setup\ConfigureWorkspace.java')
if ($LASTEXITCODE -ne 0) { throw 'Workspace setup compilation failed' }
$taskBundle = Join-Path $taskEnv.sts 'dropins\team.e.setup_1.0.0.jar'
& (Join-Path $taskEnv.jdk 'bin\jar.exe') cfm $taskBundle (Join-Path $PSScriptRoot 'bootstrap\META-INF\MANIFEST.MF') -C $taskClasses . -C (Join-Path $PSScriptRoot 'bootstrap') plugin.xml
if ($LASTEXITCODE -ne 0) { throw 'Workspace setup bundle creation failed' }
$taskMode = if ($Verify) { 'verify' } else { 'configure' }
& (Join-Path $taskEnv.sts 'eclipsec.exe') -nosplash -clean -consoleLog -vm (Join-Path $taskEnv.jdk 'bin\java.exe') -data $taskEnv.workspace -application team.e.setup.configure $taskEnv.tools $taskEnv.project $taskEnv.tomcat $taskMode
if ($LASTEXITCODE -ne 0) { throw 'Workspace configuration failed' }
