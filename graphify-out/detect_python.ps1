$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path graphify-out | Out-Null
$GRAPHIFY_PYTHON = $null

function Find-GraphifyPython {
    if (Get-Command uv -ErrorAction SilentlyContinue) {
        $uvDir = (uv tool dir 2>$null)
        if ($uvDir) {
            $uvDir = $uvDir.Trim()
            $py = Join-Path $uvDir "graphifyy\Scripts\python.exe"
            if (Test-Path $py) {
                & $py -c "import graphify" 2>$null
                if ($LASTEXITCODE -eq 0) { return $py }
            }
        }
    }
    if (Get-Command pipx -ErrorAction SilentlyContinue) {
        $venvs = (pipx environment --value PIPX_LOCAL_VENVS 2>$null)
        if ($venvs) {
            $venvs = $venvs.Trim()
            $py = Join-Path $venvs "graphifyy\Scripts\python.exe"
            if (Test-Path $py) {
                & $py -c "import graphify" 2>$null
                if ($LASTEXITCODE -eq 0) { return $py }
            }
        }
    }
    $pyCmd = Get-Command python -ErrorAction SilentlyContinue
    if ($pyCmd) {
        & $pyCmd.Source -c "import graphify" 2>$null
        if ($LASTEXITCODE -eq 0) {
            return (& $pyCmd.Source -c "import sys; print(sys.executable)").Trim()
        }
    }
    return $null
}

$GRAPHIFY_PYTHON = Find-GraphifyPython

if (-not $GRAPHIFY_PYTHON) {
    Write-Host "Graphify not found in standard tool locations, trying to install via pip..."
    pip install graphifyy -q
    $GRAPHIFY_PYTHON = Find-GraphifyPython
}

if ($GRAPHIFY_PYTHON) {
    $GRAPHIFY_PYTHON | Out-File -FilePath graphify-out\.graphify_python -Encoding utf8 -NoNewline
    (Resolve-Path .).Path | Out-File -FilePath graphify-out\.graphify_root -Encoding utf8 -NoNewline
    Write-Host "Found Python with graphify at: $GRAPHIFY_PYTHON"
} else {
    Write-Error "Could not find or install graphifyy."
}
