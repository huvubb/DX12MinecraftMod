param(
    [Parameter(Mandatory)][string]$ShaderPack,
    [ValidateRange(4,8)][int]$MaxPrivateGB = 8,
    [switch]$NoSodium
)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$game = Join-Path $project 'build/world-smoke'
$report = Join-Path $project 'build/iris-compat'
if ([IO.Path]::GetFileName($ShaderPack) -ne $ShaderPack) { throw 'Use a shader pack filename, not a path.' }
if (-not (Test-Path -LiteralPath (Join-Path $game "shaderpacks/$ShaderPack"))) { throw 'Shader pack missing.' }
New-Item -ItemType Directory (Join-Path $game 'config'), $report -Force | Out-Null
$limitFile = Join-Path $report 'memory-limit.txt'
$memoryFile = Join-Path $report 'memory.csv'
Remove-Item -LiteralPath $limitFile -ErrorAction SilentlyContinue
[IO.File]::WriteAllText((Join-Path $game 'config/iris.properties'), "enableShaders=true`nshaderPack=$ShaderPack`n", [Text.UTF8Encoding]::new($false))
"time,privateGB,workingGB,freeGB" | Set-Content -LiteralPath $memoryFile
$watcher = Start-Job -ArgumentList $project, $limitFile, $memoryFile, $MaxPrivateGB -ScriptBlock {
    param($project, $limitFile, $memoryFile, $MaxPrivateGB)
    for ($sample = 0; $sample -lt 420; $sample++) {
        $tests = Get-CimInstance Win32_Process -Filter "name='java.exe'" | Where-Object {
            $_.CommandLine -and $_.CommandLine.Contains($project) -and $_.CommandLine.Contains('-Ddx12.irisSmoke=true') -and
            $_.CommandLine.Contains('net.fabricmc.devlaunchinjector.Main')
        }
        foreach ($test in $tests) {
            $process = Get-Process -Id $test.ProcessId -ErrorAction SilentlyContinue
            if (-not $process) { continue }
            $free = (Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory * 1KB
            "$(Get-Date -Format o),$($process.PrivateMemorySize64 / 1GB),$($process.WorkingSet64 / 1GB),$($free / 1GB)" | Add-Content -LiteralPath $memoryFile
            # This watchdog only terminates the isolated shader test, never a player's game.
            if ($process.PrivateMemorySize64 -gt ($MaxPrivateGB * 1GB) -or $free -lt 8GB) {
                "Isolated Iris test stopped: private memory exceeded $MaxPrivateGB GiB or system free memory fell below 8 GiB." | Set-Content -LiteralPath $limitFile
                Stop-Process -Id $test.ProcessId -Force -ErrorAction SilentlyContinue
                return
            }
        }
        Start-Sleep -Milliseconds 250
    }
}
Push-Location $project
try {
    $arguments = @('runWorldSmoke', '-PirisCompat', '-x', 'downloadAssets', '--console=plain')
    if ($NoSodium) { $arguments += '-PirisNoSodium' }
    & ./gradlew.bat @arguments *> (Join-Path $report 'shader-run.log')
    $result = $LASTEXITCODE
} finally {
    Pop-Location
    Stop-Job $watcher
    Receive-Job $watcher
    Remove-Job $watcher
}
if (Test-Path -LiteralPath $limitFile) { throw (Get-Content -LiteralPath $limitFile) }
if ($result -ne 0 -or -not (Test-Path -LiteralPath (Join-Path $game 'dx12-smoke.ok'))) { throw 'Iris check failed; see build/iris-compat/shader-run.log.' }
if (-not $NoSodium -and -not (Select-String -LiteralPath (Join-Path $report 'shader-run.log') -SimpleMatch 'Sodium draws submitted individually')) {
    throw 'Sodium compatibility workaround was not exercised.'
}
Copy-Item -LiteralPath (Join-Path $game 'dx12-world.png') -Destination (Join-Path $report "$ShaderPack.png")
Write-Output "Passed: $ShaderPack (DX12, Iris, toggle/reload, world and saving)."
