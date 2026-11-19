# 把 Gradle wrapper 临时指向本地 distribution zip（用于离线开发机）。
# 用法（在仓库根）：
#   powershell -ExecutionPolicy Bypass -File scripts/use-local-gradle.ps1 -Zip ..\gradle-9.7.1-bin.zip
# 注意：这会修改受版本控制的 gradle/wrapper/gradle-wrapper.properties，不要提交该改动。
param(
    [Parameter(Mandatory = $true)]
    [string]$Zip
)

$root = Split-Path -Parent $PSScriptRoot
$props = Join-Path $root "gradle/wrapper/gradle-wrapper.properties"
$zipPath = (Resolve-Path $Zip).Path -replace '\\', '/'

if (-not (Test-Path $props)) {
    throw "找不到 $props"
}

$lines = Get-Content $props | ForEach-Object {
    if ($_ -match '^distributionUrl=') {
        "distributionUrl=file:///$zipPath"
    } else {
        $_
    }
}
$lines | Set-Content $props -Encoding ASCII
Write-Host "已将 wrapper 指向 $zipPath"
Write-Host "恢复官方 URL：git checkout -- gradle/wrapper/gradle-wrapper.properties"
