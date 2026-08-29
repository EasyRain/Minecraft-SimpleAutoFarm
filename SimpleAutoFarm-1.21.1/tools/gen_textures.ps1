# =============================================================
# gen_textures.ps1
# Generates all textures for the Simple Auto Farm mod (NeoForge 1.21.1).
# GUI textures are 256x256 (standard size; content lives in the top-left).
# Slots use the vanilla bevel geometry: inner 16x16 at (x,y), with a
# dark top-left edge and a light bottom-right edge extending OUTWARD.
# Run:  powershell -ExecutionPolicy Bypass -File tools/gen_textures.ps1
# =============================================================
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$base    = Split-Path -Parent $PSScriptRoot
$texRoot = Join-Path $base 'src/main/resources/assets/simpleautofarm/textures'
$blockDir = Join-Path $texRoot 'block'
$guiDir   = Join-Path $texRoot 'gui'
$itemDir  = Join-Path $texRoot 'item'
New-Item -ItemType Directory -Force -Path $blockDir, $guiDir, $itemDir | Out-Null

function New-Color([int]$r, [int]$g, [int]$b) {
    [System.Drawing.Color]::FromArgb(255, $r, $g, $b)
}
function New-Brush($c) { [System.Drawing.SolidBrush]::new($c) }

$script:rngSeed = 20240817
function Get-Rnd([int]$max) {
    $script:rngSeed = ($script:rngSeed * 1103515245 + 12345) % 2147483648
    return $script:rngSeed % $max
}

# =============================================================
# Farm block: TOP  - farm soil with green sprouts
# =============================================================
$bmp = [System.Drawing.Bitmap]::new(16, 16)
$soil       = New-Color 91 74 51
$soilDark   = New-Color 74 60 40
$green      = New-Color 62 142 62
$greenLight = New-Color 111 191 79
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) { $bmp.SetPixel($x, $y, $soil) } }
for ($i = 0; $i -lt 42; $i++) { $bmp.SetPixel((Get-Rnd 16), (Get-Rnd 16), $soilDark) }
$clusters = @(@(2, 3), @(6, 9), @(10, 4), @(13, 11), @(4, 13))
foreach ($c in $clusters) {
    $cx = $c[0]; $cy = $c[1]
    $bmp.SetPixel($cx,     $cy,     $green)
    $bmp.SetPixel($cx + 1, $cy,     $greenLight)
    $bmp.SetPixel($cx,     $cy + 1, $green)
    $bmp.SetPixel($cx + 1, $cy + 1, $green)
}
$bmp.Save((Join-Path $blockDir 'auto_farm_top.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

# =============================================================
# Farm block: SIDE - gray machine metal, green band, screen + LED
# =============================================================
$bmp = [System.Drawing.Bitmap]::new(16, 16)
$metal      = New-Color 126 126 126
$metalLight = New-Color 143 143 143
$metalDark  = New-Color 90 90 90
$band       = New-Color 78 140 78
$panelEdge  = New-Color 106 106 106
$screen     = New-Color 47 47 47
$led        = New-Color 62 255 62
$rivet      = New-Color 74 74 74
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) { $bmp.SetPixel($x, $y, $metal) } }
for ($x = 0; $x -lt 16; $x++) { $bmp.SetPixel($x, 0, $band); $bmp.SetPixel($x, 1, $band) }
for ($x = 0; $x -lt 16; $x++) { $bmp.SetPixel($x, 3, $metalDark) }
for ($y = 5; $y -le 13; $y++) {
    for ($x = 3; $x -le 12; $x++) {
        if ($x -eq 3 -or $x -eq 12 -or $y -eq 5 -or $y -eq 13) { $bmp.SetPixel($x, $y, $panelEdge) }
        else { $bmp.SetPixel($x, $y, $metalLight) }
    }
}
for ($y = 7; $y -le 9; $y++) { for ($x = 6; $x -le 9; $x++) { $bmp.SetPixel($x, $y, $screen) } }
$bmp.SetPixel(11, 8, $led); $bmp.SetPixel(11, 9, $led)
$bmp.SetPixel(1, 5, $rivet);  $bmp.SetPixel(14, 5, $rivet)
$bmp.SetPixel(1, 14, $rivet); $bmp.SetPixel(14, 14, $rivet)
$bmp.Save((Join-Path $blockDir 'auto_farm_side.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

# =============================================================
# Farm block: BOTTOM - plain dark metal
# =============================================================
$bmp = [System.Drawing.Bitmap]::new(16, 16)
$bottomBase = New-Color 105 105 105
$bottomEdge = New-Color 80 80 80
$bottomDark = New-Color 92 92 92
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) { $bmp.SetPixel($x, $y, $bottomBase) } }
for ($x = 0; $x -lt 16; $x++) {
    $bmp.SetPixel($x, 0, $bottomEdge); $bmp.SetPixel($x, 15, $bottomEdge)
    $bmp.SetPixel(0, $x, $bottomEdge); $bmp.SetPixel(15, $x, $bottomEdge)
}
for ($i = 0; $i -lt 30; $i++) { $bmp.SetPixel((Get-Rnd 16), (Get-Rnd 16), $bottomDark) }
$bmp.Save((Join-Path $blockDir 'auto_farm_bottom.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

# =============================================================
# Generator block: TOP - gray metal with a central dark grate
# =============================================================
$bmp = [System.Drawing.Bitmap]::new(16, 16)
$gMetal   = New-Color 118 118 118
$gEdge    = New-Color 88 88 88
$gGrate   = New-Color 45 45 45
$gGrateL  = New-Color 66 66 66
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) { $bmp.SetPixel($x, $y, $gMetal) } }
for ($x = 0; $x -lt 16; $x++) { $bmp.SetPixel($x, 0, $gEdge); $bmp.SetPixel($x, 15, $gEdge); $bmp.SetPixel(0, $x, $gEdge); $bmp.SetPixel(15, $x, $gEdge) }
for ($y = 5; $y -le 10; $y++) {
    for ($x = 5; $x -le 10; $x++) {
        if ($x -eq 5 -or $x -eq 10 -or $y -eq 5 -or $y -eq 10) { $bmp.SetPixel($x, $y, $gGrateL) }
        else { $bmp.SetPixel($x, $y, $gGrate) }
    }
}
$bmp.Save((Join-Path $blockDir 'generator_top.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

# =============================================================
# Generator block: SIDE - metal with an orange band + fuel hatch
# =============================================================
$bmp = [System.Drawing.Bitmap]::new(16, 16)
$gMetal2  = New-Color 118 118 118
$gEdge2   = New-Color 88 88 88
$orange   = New-Color 224 123 42
$orangeL  = New-Color 240 150 60
$hatch    = New-Color 64 64 64
$hatchL   = New-Color 84 84 84
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) { $bmp.SetPixel($x, $y, $gMetal2) } }
for ($x = 0; $x -lt 16; $x++) { $bmp.SetPixel($x, 0, $orange); $bmp.SetPixel($x, 1, $orange) }
for ($x = 0; $x -lt 16; $x++) { $bmp.SetPixel($x, 2, $orangeL); $bmp.SetPixel($x, 3, $gEdge2) }
for ($y = 10; $y -le 13; $y++) {
    for ($x = 5; $x -le 10; $x++) {
        if ($x -eq 5 -or $x -eq 10 -or $y -eq 10 -or $y -eq 13) { $bmp.SetPixel($x, $y, $hatchL) }
        else { $bmp.SetPixel($x, $y, $hatch) }
    }
}
$bmp.Save((Join-Path $blockDir 'generator_side.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

# =============================================================
# Generator block: BOTTOM - plain dark metal
# =============================================================
$bmp = [System.Drawing.Bitmap]::new(16, 16)
$gbBase = New-Color 100 100 100
$gbEdge = New-Color 78 78 78
$gbDark = New-Color 90 90 90
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) { $bmp.SetPixel($x, $y, $gbBase) } }
for ($x = 0; $x -lt 16; $x++) {
    $bmp.SetPixel($x, 0, $gbEdge); $bmp.SetPixel($x, 15, $gbEdge)
    $bmp.SetPixel(0, $x, $gbEdge); $bmp.SetPixel(15, $x, $gbEdge)
}
for ($i = 0; $i -lt 28; $i++) { $bmp.SetPixel((Get-Rnd 16), (Get-Rnd 16), $gbDark) }
$bmp.Save((Join-Path $blockDir 'generator_bottom.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

# =============================================================
# GUI helpers
# =============================================================
$slotDark  = New-Color 55 55 55      # 0x37 - top/left bevel edge
$slotLight = New-Color 255 255 255   # 0xFF - bottom/right bevel edge
$slotInner = New-Color 139 139 139   # 0x8B - slot inner
$barBorder = New-Color 55 55 55
$barInner  = New-Color 16 16 16
$bgColor     = New-Color 198 198 198  # 0xC6
$frameWhite  = New-Color 255 255 255  # 0xFF - top/left bevel
$frameGrey   = New-Color 85 85 85     # 0x55 - bottom/right bevel

# Vanilla slot bevel: 18x18 total spanning (x-1, y-1)..(x+16, y+16),
# inner 16x16 exactly at (x, y) so it lines up with the hover highlight.
function Draw-Slot([System.Drawing.Graphics]$g, [System.Drawing.SolidBrush]$bDark, [System.Drawing.SolidBrush]$bLight, [System.Drawing.SolidBrush]$bInner, [int]$x, [int]$y) {
    $g.FillRectangle($bInner, ($x - 1), ($y - 1), 18, 18)
    $g.FillRectangle($bDark, ($x - 1), ($y - 1), 17, 1)   # top edge
    $g.FillRectangle($bDark, ($x - 1), $y, 1, 16)         # left edge
    $g.FillRectangle($bLight, ($x + 16), $y, 1, 16)       # right edge
    $g.FillRectangle($bLight, $x, ($y + 16), 17, 1)       # bottom edge
}
function Draw-BarBg([System.Drawing.Graphics]$g, [System.Drawing.SolidBrush]$bBorder, [System.Drawing.SolidBrush]$bInner, [int]$x, [int]$y, [int]$w, [int]$h) {
    $g.FillRectangle($bBorder, $x, $y, $w, $h)
    $g.FillRectangle($bInner, ($x + 1), ($y + 1), ($w - 2), ($h - 2))
}
# Vanilla container window frame (per-pixel): 1px black outline + 2px bevel
# (white top-left, grey bottom-right), with diagonal stair corners and rounded
# (transparent) outer corners.
function Get-BorderColor([int]$x, [int]$y, [int]$W, [int]$H) {
    if (($x + $y) -le 1) { return 'T' }
    if ((($W - 1 - $x) + $y) -le 2) { return 'T' }
    if (($x + ($H - 1 - $y)) -le 2) { return 'T' }
    if ((($W - 1 - $x) + ($H - 1 - $y)) -le 1) { return 'T' }
    if ($y -eq 0 -and $x -ge 2 -and $x -le ($W - 4)) { return 'B' }
    if ($y -eq ($H - 1) -and $x -ge 3 -and $x -le ($W - 3)) { return 'B' }
    if ($x -eq 0 -and $y -ge 2 -and $y -le ($H - 4)) { return 'B' }
    if ($x -eq ($W - 1) -and $y -ge 3 -and $y -le ($H - 3)) { return 'B' }
    if ($x -eq 1 -and $y -eq 1) { return 'B' }
    if ($x -eq ($W - 3) -and $y -eq 1) { return 'B' }
    if ($x -eq ($W - 2) -and $y -eq 1) { return 'B' }
    if ($x -eq ($W - 2) -and $y -eq 2) { return 'B' }
    if ($x -eq 1 -and $y -eq ($H - 3)) { return 'B' }
    if ($x -eq 1 -and $y -eq ($H - 2)) { return 'B' }
    if ($x -eq 2 -and $y -eq ($H - 2)) { return 'B' }
    if ($x -eq ($W - 2) -and $y -eq ($H - 2)) { return 'B' }
    if ($y -eq 1 -and $x -ge 2 -and $x -le ($W - 4)) { return 'W' }
    if ($y -eq 2 -and $x -ge 1 -and $x -le ($W - 4)) { return 'W' }
    if ($x -eq 1 -and $y -ge 2 -and $y -le ($H - 4)) { return 'W' }
    if ($x -eq 2 -and $y -ge 1 -and $y -le ($H - 4)) { return 'W' }
    if ($x -eq 3 -and $y -eq 3) { return 'W' }
    if ($y -eq ($H - 2) -and $x -ge 3 -and $x -le ($W - 3)) { return 'G' }
    if ($y -eq ($H - 3) -and $x -ge 3 -and $x -le ($W - 3)) { return 'G' }
    if ($x -eq ($W - 3) -and $y -ge 3 -and $y -le ($H - 2)) { return 'G' }
    if ($x -eq ($W - 2) -and $y -ge 3 -and $y -le ($H - 3)) { return 'G' }
    if ($x -eq ($W - 4) -and $y -eq ($H - 4)) { return 'G' }
    return 'C'
}
function Draw-Border([System.Drawing.Bitmap]$bmp, [int]$W, [int]$H) {
    $cBlack = [System.Drawing.Color]::FromArgb(255, 0, 0, 0)
    $cWhite = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)
    $cGrey  = [System.Drawing.Color]::FromArgb(255, 85, 85, 85)
    $cTrans = [System.Drawing.Color]::Transparent
    for ($y = 0; $y -lt $H; $y++) {
        for ($x = 0; $x -lt $W; $x++) {
            if ($x -ge 4 -and $x -le ($W - 5) -and $y -ge 4 -and $y -le ($H - 5)) { continue }
            switch (Get-BorderColor $x $y $W $H) {
                'T' { $bmp.SetPixel($x, $y, $cTrans) }
                'B' { $bmp.SetPixel($x, $y, $cBlack) }
                'W' { $bmp.SetPixel($x, $y, $cWhite) }
                'G' { $bmp.SetPixel($x, $y, $cGrey) }
            }
        }
    }
}

$brushSlotDark  = New-Brush $slotDark
$brushSlotLight = New-Brush $slotLight
$brushInner     = New-Brush $slotInner
$brushBarBorder = New-Brush $barBorder
$brushBarIn     = New-Brush $barInner
$brushBg         = New-Brush $bgColor
$brushFrameWhite = New-Brush $frameWhite
$brushFrameGrey  = New-Brush $frameGrey

# =============================================================
# Farm GUI: 256x256 canvas, content 176x200 (standard 9-slot container width)
#   input row y=18, output rows y=36/54/72
#   progress bar (left) + energy bar (right) at y=96, player rows y=120/138/156, hotbar y=174
# =============================================================
$bmp = [System.Drawing.Bitmap]::new(256, 256)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.FillRectangle($brushBg, 0, 0, 176, 200)
Draw-Border $bmp 176 200
for ($r = 0; $r -lt 4; $r++) { for ($c = 0; $c -lt 9; $c++) { Draw-Slot $g $brushSlotDark $brushSlotLight $brushInner (8 + $c * 18) (18 + $r * 18) } }
# grey hint balls in the input row (row 0)
$hintBall = New-Brush (New-Color 110 110 110)
for ($c = 0; $c -lt 9; $c++) { $g.FillEllipse($hintBall, (12 + $c * 18), 22, 8, 8) }
$hintBall.Dispose()
for ($r = 0; $r -lt 3; $r++) { for ($c = 0; $c -lt 9; $c++) { Draw-Slot $g $brushSlotDark $brushSlotLight $brushInner (8 + $c * 18) (120 + $r * 18) } }
for ($c = 0; $c -lt 9; $c++) { Draw-Slot $g $brushSlotDark $brushSlotLight $brushInner (8 + $c * 18) 174 }
Draw-BarBg $g $brushBarBorder $brushBarIn 7 96 80 8       # progress bar (left half)
Draw-BarBg $g $brushBarBorder $brushBarIn 88 96 81 8      # energy bar (right half)
$bmp.Save((Join-Path $guiDir 'auto_farm.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

# =============================================================
# Generator GUI: 256x256 canvas, content 176x166
#   fuel slot (56,53), energy bar (8,18) 12x52
#   the burn flame is drawn by code (vanilla sprite) above the fuel slot
#   player rows y=84/102/120, hotbar y=142
# =============================================================
$bmp = [System.Drawing.Bitmap]::new(256, 256)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.FillRectangle($brushBg, 0, 0, 176, 166)
Draw-Border $bmp 176 166
Draw-Slot $g $brushSlotDark $brushSlotLight $brushInner 56 53
for ($r = 0; $r -lt 3; $r++) { for ($c = 0; $c -lt 9; $c++) { Draw-Slot $g $brushSlotDark $brushSlotLight $brushInner (8 + $c * 18) (84 + $r * 18) } }
for ($c = 0; $c -lt 9; $c++) { Draw-Slot $g $brushSlotDark $brushSlotLight $brushInner (8 + $c * 18) 142 }
Draw-BarBg $g $brushBarBorder $brushBarIn 8 18 12 52    # energy bar
$bmp.Save((Join-Path $guiDir 'generator.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

# =============================================================
# Upgrade slots texture (256x256): 3 vertical 18x18 segments
#   top segment at (0,0), middle at (0,18), bottom at (0,36)
# Farm uses top+middle+bottom (3 slots); generator uses top+bottom (2 slots).
# =============================================================
$bmp = [System.Drawing.Bitmap]::new(256, 256)
$g = [System.Drawing.Graphics]::FromImage($bmp)
Draw-Slot $g $brushSlotDark $brushSlotLight $brushInner 1 1
Draw-Slot $g $brushSlotDark $brushSlotLight $brushInner 1 19
Draw-Slot $g $brushSlotDark $brushSlotLight $brushInner 1 37
$bmp.Save((Join-Path $guiDir 'upgrade_slots.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

# =============================================================
# Upgrade column panel (256x256): 24x77 bordered panel for slots + button
# =============================================================
$bmp = [System.Drawing.Bitmap]::new(256, 256)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.FillRectangle($brushBg, 0, 0, 24, 77)
Draw-Border $bmp 24 77
$bmp.Save((Join-Path $guiDir 'upgrade_panel.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

# =============================================================
# Placeholder item textures (16x16) - replaced later
# =============================================================
function Save-ItemTex([string]$name, [int]$cr, [int]$cg, [int]$cb) {
    $ib = [System.Drawing.Bitmap]::new(16, 16)
    $col = New-Color $cr $cg $cb
    $edg = New-Color ([Math]::Max(0, $cr - 45)) ([Math]::Max(0, $cg - 45)) ([Math]::Max(0, $cb - 45))
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) { $ib.SetPixel($x, $y, $col) } }
    for ($i = 0; $i -lt 16; $i++) {
        $ib.SetPixel($i, 0, $edg); $ib.SetPixel($i, 15, $edg)
        $ib.SetPixel(0, $i, $edg); $ib.SetPixel(15, $i, $edg)
    }
    $ib.Save((Join-Path $itemDir $name), [System.Drawing.Imaging.ImageFormat]::Png)
    $ib.Dispose()
}
Save-ItemTex 'upgrade_base.png' 150 150 150
Save-ItemTex 'speed_upgrade.png' 90 180 90
Save-ItemTex 'efficiency_upgrade.png' 200 90 90
Save-ItemTex 'yield_upgrade.png' 90 120 200

# =============================================================
# Auto-eject toggle button (256x256): two 18x18 states, off at (0,0), on at (18,0)
# =============================================================
function Draw-ButtonState([System.Drawing.Graphics]$g, [int]$x, [int]$y, [int]$cr, [int]$cg, [int]$cb) {
    $bb = New-Brush (New-Color 55 55 55)
    $cc = New-Brush (New-Color $cr $cg $cb)
    $dd = New-Brush (New-Color ([Math]::Min(255, $cr + 60)) ([Math]::Min(255, $cg + 60)) ([Math]::Min(255, $cb + 60)))
    $g.FillRectangle($bb, $x, $y, 16, 16)
    $g.FillRectangle($cc, ($x + 1), ($y + 1), 14, 14)
    $g.FillRectangle($dd, ($x + 5), ($y + 5), 6, 6)
    $bb.Dispose(); $cc.Dispose(); $dd.Dispose()
}
$bmp = [System.Drawing.Bitmap]::new(256, 256)
$g = [System.Drawing.Graphics]::FromImage($bmp)
Draw-ButtonState $g 0 0 120 120 120
Draw-ButtonState $g 16 0 60 180 60
$bmp.Save((Join-Path $guiDir 'auto_eject_button.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

$brushSlotDark.Dispose(); $brushSlotLight.Dispose(); $brushInner.Dispose(); $brushBarBorder.Dispose(); $brushBarIn.Dispose(); $brushBg.Dispose(); $brushFrameWhite.Dispose(); $brushFrameGrey.Dispose()

Write-Host 'Textures generated:'
Get-ChildItem -Recurse $texRoot -Filter *.png | ForEach-Object { Write-Host ('  ' + $_.FullName + '  (' + $_.Length + ' bytes)') }
