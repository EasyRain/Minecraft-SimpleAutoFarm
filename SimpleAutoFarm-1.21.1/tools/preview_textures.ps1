# =============================================================
# preview_textures.ps1
# Verifies the generated textures: ASCII preview of block textures
# and structural checks for the two 256x256 GUI textures.
# Slot bevel: inner 16x16 at (x,y); left/top edge dark (55) at (x-1,y)/(x,y-1);
# right/bottom edge light (255) at (x+16,y)/(x,y+16).
# =============================================================
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$texRoot = 'D:\Temp\MCMOD\SimpleAutoFarm\src\main\resources\assets\simpleautofarm\textures'
function Get-Bmp([string]$path) { return [System.Drawing.Bitmap]::new($path) }

foreach ($name in @('auto_farm_top', 'auto_farm_side', 'auto_farm_bottom', 'generator_top', 'generator_side', 'generator_bottom')) {
    $script:legend = @{}
    $bmp = Get-Bmp (Join-Path $texRoot "block/$name.png")
    Write-Host "==== $name.png ($($bmp.Width) x $($bmp.Height)) ===="
    for ($y = 0; $y -lt $bmp.Height; $y++) {
        $line = ''
        for ($x = 0; $x -lt $bmp.Width; $x++) {
            $c = $bmp.GetPixel($x, $y)
            $key = "$($c.R),$($c.G),$($c.B)"
            if (-not $script:legend.ContainsKey($key)) { $script:legend[$key] = [char](97 + $script:legend.Count) }
            $line += $script:legend[$key]
        }
        Write-Host $line
    }
    $legendStr = ($script:legend.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join '  '
    Write-Host "legend: $legendStr"
    $bmp.Dispose()
}

function Check-Gui($relPath, [scriptblock]$checks) {
    $bmp = Get-Bmp (Join-Path $texRoot $relPath)
    Write-Host "==== $relPath ($($bmp.Width) x $($bmp.Height)) ===="
    $script:ok = $true
    function Check-Pixel([int]$x, [int]$y, [int]$r, [int]$g, [int]$b, [string]$what) {
        $c = $bmp.GetPixel($x, $y)
        if ($c.R -ne $r -or $c.G -ne $g -or $c.B -ne $b) {
            $script:ok = $false
            Write-Host "MISMATCH $what at ($x,$y): got $($c.R),$($c.G),$($c.B) want $r,$g,$b"
        }
    }
    function Check-Slot([int]$x, [int]$y, [string]$what) {
        Check-Pixel $x $y 139 139 139 "$what inner"
        Check-Pixel ($x - 1) $y 55 55 55 "$what left"
        Check-Pixel $x ($y - 1) 55 55 55 "$what top"
        Check-Pixel ($x + 16) $y 255 255 255 "$what right"
        Check-Pixel $x ($y + 16) 255 255 255 "$what bottom"
    }
    Check-Pixel 4 4 198 198 198 'background'
    Check-Pixel 2 0 0 0 0 'frame black outline'
    Check-Pixel 2 1 255 255 255 'frame white bevel'
    Check-Pixel 1 1 0 0 0 'frame black diagonal'
    $c0 = $bmp.GetPixel(0, 0)
    if ($c0.A -ne 0) { $script:ok = $false; Write-Host "MISMATCH frame transparent at (0,0): alpha=$($c0.A) want 0" }
    & $checks
    if ($script:ok) { Write-Host 'ALL STRUCTURAL CHECKS PASSED' } else { Write-Host 'HAS MISMATCHES' }
    $bmp.Dispose()
}

# Farm GUI
Check-Gui 'gui/auto_farm.png' {
    for ($r = 0; $r -lt 4; $r++) { for ($c = 0; $c -lt 9; $c++) { Check-Slot (8 + $c * 18) (18 + $r * 18) "slot ($c,$r)" } }
    for ($r = 0; $r -lt 3; $r++) { for ($c = 0; $c -lt 9; $c++) { Check-Slot (8 + $c * 18) (120 + $r * 18) "player slot ($c,$r)" } }
    for ($c = 0; $c -lt 9; $c++) { Check-Slot (8 + $c * 18) 174 "hotbar ($c)" }
    Check-Pixel 7 96 55 55 55 'progress bar border'
    Check-Pixel 8 97 16 16 16 'progress bar bg'
    Check-Pixel 88 96 55 55 55 'energy bar border'
    Check-Pixel 89 97 16 16 16 'energy bar bg'
}

# Generator GUI
Check-Gui 'gui/generator.png' {
    Check-Slot 56 53 'fuel slot'
    for ($r = 0; $r -lt 3; $r++) { for ($c = 0; $c -lt 9; $c++) { Check-Slot (8 + $c * 18) (84 + $r * 18) "player slot ($c,$r)" } }
    for ($c = 0; $c -lt 9; $c++) { Check-Slot (8 + $c * 18) 142 "hotbar ($c)" }
    Check-Pixel 8 18 55 55 55 'energy bar border'
    Check-Pixel 9 19 16 16 16 'energy bar bg'
}
