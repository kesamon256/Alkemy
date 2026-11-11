# Alkemy build script.
# Requires only a JDK 22+ (for the Windows Ink pen support).
#
#   powershell -ExecutionPolicy Bypass -File build.ps1            # build into dist\Alchemy
#   powershell -ExecutionPolicy Bypass -File build.ps1 -Run       # build and launch
#   powershell -ExecutionPolicy Bypass -File build.ps1 -Package   # also make release\Alkemy-<version>-windows-x64.zip
#   powershell -ExecutionPolicy Bypass -File build.ps1 -Dist dist\Alchemy-test   # build into another folder
#                                                                 # (Alkemy.exe with its own Java - nothing to install)

param([switch]$Run, [switch]$Package, [string]$Version = '1.0.0', [string]$Dist = 'dist\Alchemy')

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$build = Join-Path $root 'build'
$classes = Join-Path $build 'classes'
# -Dist builds somewhere else - e.g. while the app is open from dist\Alchemy
$dist = Join-Path $root $Dist

Add-Type -AssemblyName System.IO.Compression.FileSystem

if (Test-Path $build) { Remove-Item -Recurse -Force $build }
if (Test-Path $dist) { Remove-Item -Recurse -Force $dist }
New-Item -ItemType Directory -Force $classes, "$dist\lib", "$dist\modules" | Out-Null

# Libraries used at compile and run time
$libs = Get-ChildItem (Join-Path $root 'lib') -Recurse -Filter *.jar |
    Where-Object { $_.FullName -notmatch '\\(ant|jpen-tiger|quaqua)\\' }
$cp = ($libs | ForEach-Object { $_.FullName }) -join ';'

# Compile
Write-Host 'Compiling...'
$sources = Get-ChildItem (Join-Path $root 'src') -Recurse -Filter *.java | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
$argFile = Join-Path $build 'sources.txt'
[IO.File]::WriteAllLines($argFile, $sources)
& javac -nowarn -Xlint:-removal -encoding UTF-8 --release 22 -d $classes -cp $cp "@$argFile"
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }

# Copy resources (images, language bundles)
Get-ChildItem (Join-Path $root 'src') -Recurse -File | Where-Object Extension -ne '.java' | ForEach-Object {
    $rel = $_.FullName.Substring((Join-Path $root 'src').Length + 1)
    $target = Join-Path $classes $rel
    New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
    Copy-Item $_.FullName $target
}

# jar writes portable (forward slash) zip entries, unlike .NET Framework's ZipFile
function New-Zip($dir, $zipPath) {
    & jar --create --no-manifest --file $zipPath -C $dir .
    if ($LASTEXITCODE -ne 0) { throw "zip failed: $zipPath" }
}

function New-ModuleZip($moduleDir, $classDir, $prefix, $zipPath) {
    $staging = Join-Path $build ("stage-" + (Split-Path $moduleDir -Leaf))
    New-Item -ItemType Directory -Force $staging | Out-Null
    Copy-Item (Join-Path $moduleDir '*') $staging -Recurse
    if ($classDir) {
        $pkg = Join-Path $staging $prefix.Package
        New-Item -ItemType Directory -Force $pkg | Out-Null
        Get-ChildItem $classDir -Filter *.class | Where-Object { $_.Name -like ($prefix.Name + '*') } |
            Where-Object { $_.BaseName -ieq $prefix.Name -or $_.BaseName -like ($prefix.Name + '$*') } |
            ForEach-Object { Copy-Item $_.FullName $pkg }
    }
    New-Zip $staging $zipPath
}

# The core plugin declares the Create/Affect extension points
Write-Host 'Packaging modules...'
$coreStage = Join-Path $build 'stage-core'
New-Item -ItemType Directory -Force "$coreStage\org\alchemy\core" | Out-Null
Copy-Item (Join-Path $root 'modules\core\plugin.xml') $coreStage
Copy-Item (Join-Path $classes 'org\alchemy\core\AlcCore.class') "$coreStage\org\alchemy\core"
New-Zip $coreStage "$dist\modules\org.alchemy.core-1.0.5.zip"

foreach ($kind in 'create', 'affect') {
    $classDir = Join-Path $classes "org\alchemy\$kind"
    Get-ChildItem (Join-Path $root 'modules') -Directory | Where-Object Name -ne 'core' | ForEach-Object {
        $xml = Get-Content (Join-Path $_.FullName 'plugin.xml') -Raw
        if ($xml -match "org\.alchemy\.$kind\.(\w+)""") {
            $id = ([regex]::Match($xml, 'plugin id="([^"]+)" version="([^"]+)"')).Groups
            $zip = Join-Path "$dist\modules" ($id[1].Value + '-' + $id[2].Value + '.zip')
            New-ModuleZip $_.FullName $classDir @{ Package = "org\alchemy\$kind"; Name = $Matches[1] } $zip
        }
    }
    Remove-Item -Recurse -Force $classDir
}

# Main jar
Write-Host 'Creating Alchemy.jar...'
$libNames = $libs | ForEach-Object { Copy-Item $_.FullName "$dist\lib"; 'lib/' + $_.Name }
Copy-Item (Join-Path $root 'lib\jpen\*.dll') "$dist\lib"
$manifest = Join-Path $build 'manifest.mf'
$classPathLine = 'Class-Path: ' + ($libNames -join "`r`n  ")
[IO.File]::WriteAllText($manifest, "Main-Class: org.alchemy.core.Alchemy`r`n$classPathLine`r`n")
& jar --create --file "$dist\Alchemy.jar" --manifest $manifest -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }

Copy-Item (Join-Path $root 'shapes') $dist -Recurse
$launcher = "@echo off`r`ncd /d `"%~dp0`"`r`nstart `"`" javaw -Djava.library.path=lib --enable-native-access=ALL-UNNAMED -jar Alchemy.jar`r`n"
[IO.File]::WriteAllText("$dist\Alkemy.bat", $launcher)
# A shortcut with the Alkemy logo - a .bat file cannot have an icon of its own
Copy-Item (Join-Path $root 'src\org\alchemy\data\alkemy.ico') "$dist\alkemy.ico" -Force
try {
    $shell = New-Object -ComObject WScript.Shell
    $link = $shell.CreateShortcut("$dist\Alkemy.lnk")
    $link.TargetPath = 'javaw.exe'
    $link.Arguments = '-Djava.library.path=lib --enable-native-access=ALL-UNNAMED -jar Alchemy.jar'
    $link.WorkingDirectory = $dist
    $link.IconLocation = "$dist\alkemy.ico,0"
    $link.Description = 'Alkemy'
    $link.Save()
} catch {
    Write-Host "Could not create the Alkemy shortcut: $_"
}

Write-Host "Done: $dist"

if ($Package) {
    # The jpackage of the JDK that runs java - it bundles that Java with the app
    $javaHome = ((& cmd /c 'java -XshowSettings:properties -version 2>&1' | Select-String 'java.home') -split '=')[1].Trim()
    $jpackage = Join-Path $javaHome 'bin\jpackage.exe'
    if (-not (Test-Path $jpackage)) { throw "jpackage not found in $javaHome" }
    $out = Join-Path $root 'release'
    $image = Join-Path $out 'Alkemy'
    if (Test-Path $image) { Remove-Item -Recurse -Force $image }
    New-Item -ItemType Directory -Force $out | Out-Null
    $pkgInput = Join-Path $build 'package-input'
    if (Test-Path $pkgInput) { Remove-Item -Recurse -Force $pkgInput }
    New-Item -ItemType Directory -Force $pkgInput | Out-Null
    # The app without the launchers made for a plain Java install
    Get-ChildItem $dist | Where-Object { $_.Name -notin 'Alkemy.bat', 'Alkemy.lnk' } | Copy-Item -Destination $pkgInput -Recurse
    Write-Host 'Packaging Alkemy.exe with its own Java...'
    & $jpackage --type app-image --name Alkemy --app-version $Version --vendor 'Alkemy' `
        --description 'Alkemy - a sketching app built on Alchemy' `
        --input $pkgInput --main-jar Alchemy.jar --main-class org.alchemy.core.Alchemy `
        --icon (Join-Path $root 'src\org\alchemy\data\alkemy.ico') `
        --java-options '-Djava.library.path=$APPDIR\lib' --java-options '--enable-native-access=ALL-UNNAMED' `
        --dest $out
    if ($LASTEXITCODE -ne 0) { throw 'jpackage failed' }
    Copy-Item (Join-Path $root 'LICENSE.md') $image
    $zip = Join-Path $out "Alkemy-$Version-windows-x64.zip"
    if (Test-Path $zip) { Remove-Item $zip }
    [IO.Compression.ZipFile]::CreateFromDirectory($image, $zip, [IO.Compression.CompressionLevel]::Optimal, $true)
    Write-Host "Release: $zip"
}
if ($Run) {
    Push-Location $dist
    try { & java "-Djava.library.path=lib" --enable-native-access=ALL-UNNAMED -jar Alchemy.jar } finally { Pop-Location }
}

