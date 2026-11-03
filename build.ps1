$ErrorActionPreference = "Stop"

$ProjectRoot = "D:\Valerio\PROGETTI\AAMotoMic\AAMotoMicPro"
$BuildToolsDir = "D:\Valerio\PROGETTI\AAMotoMic\android-sdk\build-tools\35.0.0"
$PlatformJar = "D:\Valerio\PROGETTI\AAMotoMic\android-sdk\platforms\android-35\android.jar"
$JdkBin = "C:\Program Files\Android\Android Studio\jbr\bin"

$env:PATH = "$JdkBin;$BuildToolsDir;$env:PATH"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

$SrcDir = "$ProjectRoot\app\src\main\java"
$ResDir = "$ProjectRoot\app\src\main\res"
$Manifest = "$ProjectRoot\app\src\main\AndroidManifest.xml"
$BuildDir = "$ProjectRoot\build"

Write-Host "=== 1. Cleaning build directory ==="
if (Test-Path "$BuildDir\classes") { Remove-Item -Recurse -Force "$BuildDir\classes" }
if (Test-Path "$BuildDir\compiled_res") { Remove-Item -Recurse -Force "$BuildDir\compiled_res" }
if (Test-Path "$BuildDir\gen") { Remove-Item -Recurse -Force "$BuildDir\gen" }
if (Test-Path "$BuildDir\dex") { Remove-Item -Recurse -Force "$BuildDir\dex" }
New-Item -ItemType Directory -Force -Path "$BuildDir\classes" | Out-Null
New-Item -ItemType Directory -Force -Path "$BuildDir\compiled_res" | Out-Null
New-Item -ItemType Directory -Force -Path "$BuildDir\gen" | Out-Null
New-Item -ItemType Directory -Force -Path "$BuildDir\dex" | Out-Null

Write-Host "=== 2. Compiling resources with aapt2 ==="
& "$BuildToolsDir\aapt2.exe" compile --dir "$ResDir" -o "$BuildDir\compiled_res\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "=== 3. Linking resources and generating R.java ==="
& "$BuildToolsDir\aapt2.exe" link -o "$BuildDir\unsigned.apk" `
    -I "$PlatformJar" `
    --manifest "$Manifest" `
    --java "$BuildDir\gen" `
    --auto-add-overlay `
    "$BuildDir\compiled_res\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "=== 4. Compiling Java sources with javac ==="
$JavaFiles = Get-ChildItem -Path "$SrcDir", "$BuildDir\gen" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
& "$JdkBin\javac.exe" -encoding UTF-8 -cp "$PlatformJar" -d "$BuildDir\classes" $JavaFiles
if ($LASTEXITCODE -ne 0) { throw "javac compilation failed" }

Write-Host "=== 5. Converting bytecode to DEX with d8 ==="
$ClassFiles = Get-ChildItem -Path "$BuildDir\classes" -Recurse -Filter "*.class" | ForEach-Object { $_.FullName }
& "$BuildToolsDir\d8.bat" --lib "$PlatformJar" --output "$BuildDir\dex" --min-api 24 $ClassFiles
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "=== 6. Injecting classes.dex into APK ==="
Push-Location "$BuildDir\dex"
& "$JdkBin\jar.exe" -uf "$BuildDir\unsigned.apk" "classes.dex"
Pop-Location
if ($LASTEXITCODE -ne 0) { throw "jar -uf failed" }

Write-Host "=== 7. Aligning APK with zipalign ==="
if (Test-Path "$BuildDir\aligned.apk") { Remove-Item -Force "$BuildDir\aligned.apk" }
& "$BuildToolsDir\zipalign.exe" -p -f -v 4 "$BuildDir\unsigned.apk" "$BuildDir\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "=== 8. Signing APK with apksigner ==="
$FinalApk = "D:\Valerio\PROGETTI\AAMotoMic\AAMotoMic-Gem.apk"
$Keystore = "$BuildDir\release.keystore"
if (-not (Test-Path $Keystore)) {
    & "$JdkBin\keytool.exe" -genkey -v -keystore $Keystore -storepass "android" -alias "aamotomic" -keypass "android" -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Valerio, OU=AAMotoMic, O=Personal, C=IT"
}
& "$BuildToolsDir\apksigner.bat" sign --ks "$Keystore" --ks-pass pass:android --ks-key-alias aamotomicpro --key-pass pass:android --out "$FinalApk" "$BuildDir\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

# Canonical release APK
Copy-Item "$FinalApk" "$ProjectRoot\AAMotoMic-Gem.apk" -Force
Copy-Item "$FinalApk" "D:\Valerio\PROGETTI\AAMotoMic\AAMotoMic-Pro-v11.0.apk" -Force

Write-Host "=== 9. Verifying signed APK ==="
& "$BuildToolsDir\apksigner.bat" verify --verbose "$FinalApk"

Write-Host "SUCCESS! APK built and signed at: $FinalApk"
Get-Item "$FinalApk" | Select-Object Name, Length, LastWriteTime
