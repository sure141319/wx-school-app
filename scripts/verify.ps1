[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $repositoryRoot 'v1'
$miniProgramRoot = Join-Path $repositoryRoot 'wxui_v2'

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,

        [Parameter(Mandatory = $true)]
        [string[]]$ArgumentList,

        [Parameter(Mandatory = $true)]
        [string]$WorkingDirectory
    )

    Push-Location -LiteralPath $WorkingDirectory
    try {
        & $FilePath @ArgumentList
        $commandExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }

    if ($commandExitCode -ne 0) {
        throw "$FilePath failed with exit code $commandExitCode"
    }
}

$mavenExecutable = (Get-Command mvn -ErrorAction Stop).Source
$npmExecutable = (Get-Command npm -ErrorAction Stop).Source

Write-Host 'Running backend tests...'
$backendVerification = @{
    FilePath         = $mavenExecutable
    ArgumentList     = @('test', '--no-transfer-progress')
    WorkingDirectory = $backendRoot
}
Invoke-CheckedCommand @backendVerification

Write-Host 'Running mini program quality gate...'
$miniProgramVerification = @{
    FilePath         = $npmExecutable
    ArgumentList     = @('run', 'verify')
    WorkingDirectory = $miniProgramRoot
}
Invoke-CheckedCommand @miniProgramVerification

Write-Host 'Repository quality gate passed.'
