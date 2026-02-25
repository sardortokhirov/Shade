$base = "C:\Users\Sardor\Desktop\Shade\src\main\resources\i18n"
$files = @("messages_uz.properties", "messages_ru.properties")

foreach ($f in $files) {
    $path = Join-Path $base $f
    if (Test-Path $path) {
        $lines = Get-Content $path -Encoding UTF8
        $newLines = $lines | Where-Object { $_ -notmatch '^apk_link\.' -and $_ -notmatch '^contact\.' }
        [System.IO.File]::WriteAllLines($path, $newLines, [System.Text.Encoding]::UTF8)
        Write-Host "Cleaned: $f"
    }
}
