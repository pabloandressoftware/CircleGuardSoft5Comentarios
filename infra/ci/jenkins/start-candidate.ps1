param(
    [Parameter(Mandatory)][string]$JenkinsUrl,
    [Parameter(Mandatory)][string]$CredentialEnvFile,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{40}$')][string]$GitCommit
)
$ErrorActionPreference = 'Stop'
if ($JenkinsUrl -notmatch '^https://') { throw 'Jenkins requiere HTTPS.' }
$values = @{}
Get-Content -LiteralPath $CredentialEnvFile | ForEach-Object {
    if ($_ -match '^([A-Z_]+)=(.*)$') { $values[$matches[1]] = $matches[2] }
}
if (!$values.CI_JENKINS_USER -or !$values.CI_JENKINS_PASSWORD) { throw 'Faltan las credenciales de Jenkins.' }
$base = $JenkinsUrl.TrimEnd('/')
$headers = @{Authorization='Basic '+[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(
    $values.CI_JENKINS_USER+':'+$values.CI_JENKINS_PASSWORD))}
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$crumb = Invoke-RestMethod "$base/crumbIssuer/api/json" -Headers $headers -WebSession $session
$headers[$crumb.crumbRequestField] = $crumb.crumb
$job = 'circleguard-handover'
$exists = $false
try {
    $state = Invoke-RestMethod "$base/job/$job/api/json?tree=lastBuild[building]" -Headers $headers -WebSession $session
    if ($state.lastBuild.building) { throw 'El job ya esta ejecutandose; consulta su resultado.' }
    $exists = $true
} catch {
    if (!$_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 404) { throw }
}
$xml = @"
<flow-definition plugin="workflow-job">
  <description>Snapshot verificado del commit $GitCommit. No despliega ambientes.</description>
  <definition class="org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition" plugin="workflow-cps">
    <scm class="hudson.plugins.git.GitSCM" plugin="git">
      <configVersion>2</configVersion>
      <userRemoteConfigs><hudson.plugins.git.UserRemoteConfig>
        <url>https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios.git</url>
      </hudson.plugins.git.UserRemoteConfig></userRemoteConfigs>
      <branches><hudson.plugins.git.BranchSpec><name>$GitCommit</name></hudson.plugins.git.BranchSpec></branches>
      <doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>
      <extensions/>
    </scm>
    <scriptPath>infra/ci/candidate.Jenkinsfile</scriptPath>
    <lightweight>false</lightweight>
  </definition>
  <disabled>false</disabled>
</flow-definition>
"@
$uri = if ($exists) { "$base/job/$job/config.xml" } else { "$base/createItem?name=$job" }
$null = Invoke-WebRequest -UseBasicParsing -Uri $uri -Method Post -Headers $headers -WebSession $session `
    -ContentType 'application/xml; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($xml))
$null = Invoke-WebRequest -UseBasicParsing "$base/job/$job/build" -Method Post -Headers $headers -WebSession $session
Write-Output "Job iniciado: $base/job/$job/ (commit $GitCommit)"
