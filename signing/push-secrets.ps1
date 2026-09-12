<#
.SYNOPSIS
    Envoie le keystore et ses mots de passe dans les secrets GitHub du dépôt.

.DESCRIPTION
    Lit keystore.properties (produit par new-keystore.ps1) et pousse, via gh CLI :
      KEYSTORE_B64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.
    Le workflow .github/workflows/build-android.yml les utilise pour signer
    l'APK de release. Rien n'est affiché ; rien n'est écrit dans le dépôt.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File signing\push-secrets.ps1
#>
[CmdletBinding()]
param([string]$Repo = 'Hitman47/ProwlarrExplorer')

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$propsPath = Join-Path $projectRoot 'keystore.properties'
if (-not (Test-Path $propsPath)) { throw "keystore.properties introuvable : lance d'abord new-keystore.ps1" }

$props = @{}
Get-Content $propsPath | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    $props[$k.Trim()] = $v.Trim()
}
$storeFile = $props['storeFile']
if (-not (Test-Path $storeFile)) { throw "keystore introuvable : $storeFile" }

$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($storeFile))
$b64 | gh secret set KEYSTORE_B64 --repo $Repo
$props['storePassword'] | gh secret set KEYSTORE_PASSWORD --repo $Repo
$props['keyAlias'] | gh secret set KEY_ALIAS --repo $Repo
$props['keyPassword'] | gh secret set KEY_PASSWORD --repo $Repo
Write-Host "4 secrets poussés sur $Repo."
