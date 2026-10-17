# Fails if any banned dependency appears in the tree (see check-no-horologist.sh).
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$patterns = @(
    "androidx\.wear\.horologist",
    "com\.google\.android\.horologist",
    "horologist-",
    "androidx\.compose\.material3",
    "com\.materialkolor",
    "material-kolor",
    "materialkolor",
    "material-color-utilities",
    "com\.google\.android\.material\.color\.utilities"
)

$hits = @()
Get-ChildItem -Path $root -Recurse -File -Include *.kt,*.kts,*.toml,*.gradle,*.xml |
    Where-Object { $_.FullName -notmatch "\\\.gradle\\|\\build\\|\\\.git\\" } |
    ForEach-Object {
        $text = Get-Content -LiteralPath $_.FullName -Raw -ErrorAction SilentlyContinue
        if (-not $text) { return }
        foreach ($p in $patterns) {
            if ($text -match $p) {
                $hits += "$($_.FullName) ~ $p"
            }
        }
    }

if ($hits.Count -gt 0) {
    Write-Host "Dependency ban violated:"
    $hits | ForEach-Object { Write-Host " - $_" }
    exit 1
}

Write-Host "OK: no Horologist / phone M3 / material-kolor / MCU artifacts found."
