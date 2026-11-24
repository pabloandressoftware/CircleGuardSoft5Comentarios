# Refina las acciones de IAM Policy Autopilot; no analiza Terraform por su cuenta.
param(
    [Parameter(Mandatory)][string[]]$AutopilotFiles,
    [Parameter(Mandatory)][string]$OutputFile
)
$ErrorActionPreference = 'Stop'
$account = '001784022331'
$region = 'us-east-2'
$boundary = "arn:aws:iam::${account}:policy/CircleGuardNodeBoundary"
$actions = @($AutopilotFiles | ForEach-Object {
    $generated = Get-Content -LiteralPath $_ -Raw | ConvertFrom-Json
    $generated.Policies.Policy.Statement.Action
} | Sort-Object -Unique)
if (!$actions -or !($actions -contains 'ec2:RunInstances')) { throw 'Falta el plan de creacion de Autopilot.' }
function New-Statement($sid, $statementActions, $resources, $condition = $null) {
    $statement = [ordered]@{Sid=$sid; Effect='Allow'; Action=@($statementActions); Resource=@($resources)}
    if ($condition) { $statement.Condition = $condition }
    return $statement
}
$regional = @{StringEquals=@{'aws:RequestedRegion'=$region}}
$nodeRoles = @('dev','stage','prod') | ForEach-Object { "arn:aws:iam::${account}:role/circleguard-$_-*" }
$nodeProfiles = @('dev','stage','prod') | ForEach-Object { "arn:aws:iam::${account}:instance-profile/circleguard-$_-*" }
$statements = @(
    (New-Statement 'Ec2Read' ($actions | Where-Object { $_ -match '^ec2:(Describe|Get)' }) '*' $regional),
    (New-Statement 'Ec2Provision' ($actions | Where-Object { $_ -match '^ec2:' -and $_ -notmatch '^ec2:(Describe|Get)' }) '*' $regional),
    (New-Statement 'NodeRoles' ($actions | Where-Object {
        $_ -match '^iam:.*(Role|InstanceProfile)' -and $_ -notin @('iam:CreateRole','iam:AttachRolePolicy','iam:PassRole')
    }) ($nodeRoles + $nodeProfiles)),
    (New-Statement 'CreateBoundedNodeRoles' 'iam:CreateRole' $nodeRoles @{StringEquals=@{'iam:PermissionsBoundary'=$boundary}}),
    (New-Statement 'AttachSsmToNodes' 'iam:AttachRolePolicy' $nodeRoles @{ArnEquals=@{'iam:PolicyARN'='arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore'}}),
    (New-Statement 'PassOnlyNodeRoles' 'iam:PassRole' $nodeRoles @{StringEquals=@{'iam:PassedToService'='ec2.amazonaws.com'}}),
    (New-Statement 'ReadLogGroups' 'logs:DescribeLogGroups' '*' $regional),
    (New-Statement 'EnvironmentLogGroups' ($actions | Where-Object { $_ -match '^logs:' -and $_ -ne 'logs:DescribeLogGroups' }) "arn:aws:logs:${region}:${account}:log-group:/circleguard/circleguard-*" $regional),
    # El backend no aparece en resource_changes. Permisos de la documentacion
    # del backend S3 de HashiCorp, contrastados con Service Authorization Reference.
    (New-Statement 'ListStateBucket' 's3:ListBucket' "arn:aws:s3:::circleguard-tfstate-${account}-${region}"),
    (New-Statement 'EnvironmentState' @('s3:GetObject','s3:PutObject') @('dev','stage','prod').ForEach({"arn:aws:s3:::circleguard-tfstate-${account}-${region}/circleguard/$_/terraform.tfstate"})),
    (New-Statement 'TerraformLocks' @('dynamodb:DescribeTable','dynamodb:GetItem','dynamodb:PutItem','dynamodb:DeleteItem') "arn:aws:dynamodb:${region}:${account}:table/circleguard-tf-locks" $regional)
)
$statements += [ordered]@{
    Sid='ProtectCiAndUnrelatedInstances'; Effect='Deny'; Action=@('ec2:StopInstances','ec2:TerminateInstances')
    Resource="arn:aws:ec2:${region}:${account}:instance/*"
    Condition=@{StringNotEquals=@{'ec2:ResourceTag/Environment'=@('dev','stage','prod')}}
}
$policy = [ordered]@{Version='2012-10-17'; Statement=$statements}
$compact = ConvertTo-Json -InputObject $policy -Depth 15 -Compress
if ($compact.Length -gt 10240) { throw 'La politica supera el limite inline por rol.' }
$null = New-Item -ItemType Directory -Force (Split-Path -Parent $OutputFile)
[IO.File]::WriteAllText($OutputFile, (ConvertTo-Json -InputObject $policy -Depth 15), (New-Object Text.UTF8Encoding($false)))
Write-Output ('Politica generada y acotada: ' + $compact.Length + ' caracteres.')
