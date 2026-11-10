# Alkemy regression tests - run inside the app, no real mouse or pen is used.
# Builds first, then runs every test and prints a summary. Screenshots go to tests\shots.
#   powershell -ExecutionPolicy Bypass -File test.ps1            # all tests
#   powershell -ExecutionPolicy Bypass -File test.ps1 LayerTest  # some tests
# Note: the tests share the app's saved preferences and put them back when done.

param([string[]]$Only)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
& (Join-Path $root 'build.ps1')
$dist = Join-Path $root 'dist\Alchemy'
$classes = Join-Path $root 'build\test-classes'
$shots = Join-Path $root 'tests\shots'
New-Item -ItemType Directory -Force $classes, $shots | Out-Null
$sources = Get-ChildItem (Join-Path $root 'tests') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac -nowarn -encoding UTF-8 -d $classes -cp "$dist\Alchemy.jar" $sources
if ($LASTEXITCODE -ne 0) { throw 'Compiling the tests failed' }

$tests = Get-ChildItem (Join-Path $root 'tests\org\alchemy\core') -Filter *Test.java | ForEach-Object { $_.BaseName }
if ($Only) {
    $names = $Only | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() }
    $tests = $tests | Where-Object { $names -contains $_ }
    if (-not $tests) { throw "No test called $($names -join ', ')" }
}
$failed = 0
Push-Location $dist
try {
    foreach ($t in $tests) {
        $out = & cmd /c "java -Djava.library.path=lib --enable-native-access=ALL-UNNAMED -cp `"Alchemy.jar;$classes`" org.alchemy.core.$t `"$shots`" 2>&1" | Out-String
        if ($out -match 'ALL PASSED') {
            Write-Host "PASS  $t"
        } elseif ($out -match 'FAIL') {
            $failed++
            Write-Host "FAIL  $t" -ForegroundColor Red
            $out -split "`n" | Where-Object { $_ -match '^FAIL' } | ForEach-Object { Write-Host "      $_" }
        } else {
            Write-Host "done  $t (screenshot only)"
        }
    }
} finally {
    Pop-Location
}
if ($failed) { Write-Host "$failed test(s) failed" -ForegroundColor Red; exit 1 }
Write-Host 'All tests passed'
