$filesToRestore = @(
    "controller\ApkLinkBotController.java",
    "dto\ApkLinkBotConfigDTO.java",
    "dto\ApkLinkBotConfigRequest.java",
    "dto\ApkLinkInviteDTO.java",
    "dto\ApkLinkInviteRequest.java",
    "dto\ApkLinkKeywordDTO.java",
    "dto\ApkLinkKeywordRequest.java",
    "dto\ApkLinkPlatformDTO.java",
    "dto\ApkLinkPlatformRequest.java",
    "dto\MainApkChannelRequest.java",
    "model\ApkLinkBotConfig.java",
    "model\ApkLinkGroupCooldown.java",
    "model\ApkLinkInvite.java",
    "model\ApkLinkKeyword.java",
    "model\ApkLinkPlatform.java",
    "model\ApkLinkGroupPlatformCooldown.java",
    "model\ApkLinkUserCooldown.java",
    "model\ApkLinkUserPreference.java",
    "repository\ApkLinkBotConfigRepository.java",
    "repository\ApkLinkGroupCooldownRepository.java",
    "repository\ApkLinkGroupPlatformCooldownRepository.java",
    "repository\ApkLinkInviteRepository.java",
    "repository\ApkLinkKeywordRepository.java",
    "repository\ApkLinkPlatformRepository.java",
    "repository\ApkLinkUserCooldownRepository.java",
    "repository\ApkLinkUserPreferenceRepository.java",
    "service\ApkLinkBotConfigService.java",
    "service\ApkLinkPlatformService.java",
    "service\ApkLinkInviteService.java"
)

$srcBase = "C:\Users\Sardor\Desktop\AppLink\src\main\java\com\example\applink"
$dstBase = "C:\Users\Sardor\Desktop\Shade\src\main\java\com\example\shade"

foreach ($f in $filesToRestore) {
    $srcPath = Join-Path $srcBase $f
    $dstPath = Join-Path $dstBase $f
    
    if (Test-Path $srcPath) {
        $content = [System.IO.File]::ReadAllText($srcPath, [System.Text.Encoding]::UTF8)
        $content = $content -replace "com\.example\.applink", "com.example.shade"
        
        # Strip BOM if it exists
        $content = $content.TrimStart([char]0xFEFF)
        
        $dir = Split-Path $dstPath
        if (-not (Test-Path $dir)) {
            New-Item -ItemType Directory -Force -Path $dir | Out-Null
        }
        
        # Write without BOM
        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($dstPath, $content, $utf8NoBom)
        
        Write-Host "Restored: $f"
    }
}

Write-Host "`nAll required files restored to Shade!"
