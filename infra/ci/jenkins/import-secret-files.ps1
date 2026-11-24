param(
    [Parameter(Mandatory)][string]$JenkinsUrl,
    [Parameter(Mandatory)][string]$CredentialEnvFile,
    [Parameter(Mandatory)][string]$SecretDirectory,
    [Parameter(Mandatory)][ValidateSet('dev','stage','prod')][string]$Environment
)
$ErrorActionPreference = 'Stop'
$ciValues = @{}
Get-Content -LiteralPath $CredentialEnvFile | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object {
    $parts = $_ -split '=', 2
    $ciValues[$parts[0]] = $parts[1]
}
if (!$ciValues.CI_JENKINS_USER -or !$ciValues.CI_JENKINS_PASSWORD) { throw 'Faltan las credenciales de Jenkins.' }
if ($JenkinsUrl -notmatch '^https://') { throw 'La importacion requiere HTTPS.' }
$headers = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(
    $ciValues.CI_JENKINS_USER + ':' + $ciValues.CI_JENKINS_PASSWORD)) }
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$base = $JenkinsUrl.TrimEnd('/')
$crumb = Invoke-RestMethod "$base/crumbIssuer/api/json" -Headers $headers -WebSession $session
$headers[$crumb.crumbRequestField] = $crumb.crumb
$specs = @(
    @{id="kubeconfig-$Environment"; filename="$Environment.kubeconfig"},
    @{id="circleguard-env-$Environment"; filename="circleguard-env-$Environment"}
)
foreach ($spec in $specs) {
    $spec.content = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $SecretDirectory $spec.filename)))
}
$encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $specs -Compress)))
$script = @'
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.domains.Domain
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl
import com.cloudbees.plugins.credentials.SecretBytes
import groovy.json.JsonSlurper
def specs = new JsonSlurper().parseText(new String('__SPECS__'.decodeBase64(), 'UTF-8'))
def store = SystemCredentialsProvider.getInstance().getStore()
specs.each { item ->
    def previous = store.getCredentials(Domain.global()).find { it.id == item.id }
    if (previous) { store.removeCredentials(Domain.global(), previous) }
    if (!store.addCredentials(Domain.global(), new FileCredentialsImpl(CredentialsScope.GLOBAL,
        item.id, 'CircleGuard AWS', item.filename, SecretBytes.fromBytes(item.content.decodeBase64())))) {
        throw new IllegalStateException('No se pudo registrar la credencial')
    }
    println('Configurada: ' + item.id)
}
// scriptText muestra el valor de retorno: nunca devolver specs ni secretos.
return null
'@
$request = @{
    Uri = "$base/scriptText"
    Method = 'Post'
    Headers = $headers
    WebSession = $session
    Body = @{script=$script.Replace('__SPECS__', $encoded)}
}
$reply = Invoke-RestMethod @request
foreach ($spec in $specs) {
    if ($reply -notmatch [regex]::Escape('Configurada: ' + $spec.id)) { throw 'Jenkins no confirmo la importacion.' }
    Write-Output ('Configurada: ' + $spec.id)
}
