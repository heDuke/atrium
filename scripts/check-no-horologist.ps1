# Fails if any Horologist coordinate or import appears in the tree.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$patterns = @(
    "androidx\.wear\.horologist",
    "com\.google\.android\.horologist",
    "horologist-"
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
    Write-Host "Horologist ban violated:"
    $hits | ForEach-Object { Write-Host " - $_" }
    exit 1
}

Write-Host "OK: no Horologist artifacts found."
