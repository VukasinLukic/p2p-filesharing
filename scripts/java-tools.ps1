# Resolves java/javac to a JDK 17+ instead of trusting whatever PATH happens to hit first.
#
# Why this exists: Windows machines routinely have an old Oracle "java8path" shim early in PATH
# (installed by the public Java 8 JRE), so `javac` can be JDK 21 while `java` is 1.8 - which
# compiles the project fine and then fails at startup with UnsupportedClassVersionError.
# JAVA_HOME wins when it is usable, PATH is the fallback.
#
# Dot-source this file, then call Get-JavaExe / Get-JavacExe.

$script:JavaToolCache = @{}
$script:MinimumJavaMajor = 17

function Get-JavaMajorVersion([string]$ExePath) {
    # Redirect inside cmd: `-version` writes to stderr, and redirecting a native command's stderr
    # in PowerShell 5.1 turns every line into an ErrorRecord (fatal under $ErrorActionPreference).
    $text = cmd /c "`"$ExePath`" -version 2>&1" | Out-String
    $match = [regex]::Match($text, 'version "(\d+)(?:\.(\d+))?')
    if (-not $match.Success) { return 0 }

    $first = [int]$match.Groups[1].Value
    if ($first -eq 1 -and $match.Groups[2].Success) {
        return [int]$match.Groups[2].Value   # legacy "1.8.0_471" -> 8
    }
    return $first
}

function Resolve-JdkTool([string]$Tool) {
    if ($script:JavaToolCache.ContainsKey($Tool)) { return $script:JavaToolCache[$Tool] }

    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME "bin\$Tool.exe") }
    $onPath = Get-Command $Tool -ErrorAction SilentlyContinue
    if ($onPath) { $candidates += $onPath.Source }

    $rejected = @()
    foreach ($candidate in $candidates) {
        if (-not (Test-Path $candidate)) { continue }
        # javac and java in the same bin\ always share a version, so probing java is enough.
        $probe = Join-Path (Split-Path -Parent $candidate) "java.exe"
        if (-not (Test-Path $probe)) { $probe = $candidate }
        $major = Get-JavaMajorVersion $probe
        if ($major -ge $script:MinimumJavaMajor) {
            $script:JavaToolCache[$Tool] = $candidate
            return $candidate
        }
        $rejected += "$candidate (Java $major)"
    }

    $message = "Nije pronadjen JDK $($script:MinimumJavaMajor)+ za '$Tool'."
    if ($rejected.Count -gt 0) {
        $message += " Pronadjeno, ali previse staro: " + ($rejected -join ", ") + "."
    }
    $message += " Instaliraj JDK 17+ i postavi JAVA_HOME na njegov koreni folder."
    throw $message
}

function Get-JavaExe { Resolve-JdkTool "java" }
function Get-JavacExe { Resolve-JdkTool "javac" }
