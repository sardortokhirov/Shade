$filesToDelete = @(
    "src\main\java\com\example\shade\bot\ApkLinkDistributionBot.java",
    "src\main\java\com\example\shade\controller\ApkLinkBotController.java",
    "src\main\java\com\example\shade\dto\ApkLinkBotConfigDTO.java",
    "src\main\java\com\example\shade\dto\ApkLinkBotConfigRequest.java",
    "src\main\java\com\example\shade\dto\ApkLinkInviteDTO.java",
    "src\main\java\com\example\shade\dto\ApkLinkInviteRequest.java",
    "src\main\java\com\example\shade\dto\ApkLinkKeywordDTO.java",
    "src\main\java\com\example\shade\dto\ApkLinkKeywordRequest.java",
    "src\main\java\com\example\shade\dto\ApkLinkPlatformDTO.java",
    "src\main\java\com\example\shade\dto\ApkLinkPlatformRequest.java",
    "src\main\java\com\example\shade\dto\MainApkChannelRequest.java",
    "src\main\java\com\example\shade\model\ApkLinkBotConfig.java",
    "src\main\java\com\example\shade\model\ApkLinkGroupCooldown.java",
    "src\main\java\com\example\shade\model\ApkLinkInvite.java",
    "src\main\java\com\example\shade\model\ApkLinkKeyword.java",
    "src\main\java\com\example\shade\model\ApkLinkPlatform.java",
    "src\main\java\com\example\shade\model\ApkLinkUserCooldown.java",
    "src\main\java\com\example\shade\model\ApkLinkUserPreference.java",
    "src\main\java\com\example\shade\repository\ApkLinkBotConfigRepository.java",
    "src\main\java\com\example\shade\repository\ApkLinkGroupCooldownRepository.java",
    "src\main\java\com\example\shade\repository\ApkLinkInviteRepository.java",
    "src\main\java\com\example\shade\repository\ApkLinkKeywordRepository.java",
    "src\main\java\com\example\shade\repository\ApkLinkPlatformRepository.java",
    "src\main\java\com\example\shade\repository\ApkLinkUserCooldownRepository.java",
    "src\main\java\com\example\shade\repository\ApkLinkUserPreferenceRepository.java",
    "src\main\java\com\example\shade\service\ApkLinkBotConfigService.java",
    "src\main\java\com\example\shade\service\ApkLinkPlatformService.java",
    "src\main\java\com\example\shade\service\ApkLinkCooldownService.java",
    "src\main\java\com\example\shade\service\ApkLinkLanguageService.java",
    "src\main\java\com\example\shade\service\ApkLinkInviteService.java",
    "src\main\java\com\example\shade\service\ApkDownloadService.java"
)

$base = "C:\Users\Sardor\Desktop\Shade"
foreach ($f in $filesToDelete) {
    $path = Join-Path $base $f
    if (Test-Path $path) {
        Remove-Item $path -Force
        Write-Host "Deleted: $f"
    }
}
