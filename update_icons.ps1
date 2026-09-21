
$sourceImage = "C:/Users/prem7/.gemini/antigravity/brain/ba90ab4e-36e4-418d-882e-a95310faf08e/app_logo_concept_1768570241185.png"
$resDir = "app/src/main/res"

$sizes = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

Add-Type -AssemblyName System.Drawing

$img = [System.Drawing.Image]::FromFile($sourceImage)

foreach ($key in $sizes.Keys) {
    $size = $sizes[$key]
    $dir = "$resDir/$key"
    
    if (!(Test-Path $dir)) {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
    }

    $resized = new-object System.Drawing.Bitmap($size, $size)
    $graph = [System.Drawing.Graphics]::FromImage($resized)
    $graph.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graph.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graph.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    
    $graph.DrawImage($img, 0, 0, $size, $size)
    
    $resized.Save("$dir/ic_launcher.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $resized.Save("$dir/ic_launcher_round.png", [System.Drawing.Imaging.ImageFormat]::Png)
    
    $graph.Dispose()
    $resized.Dispose()
    
    Write-Host "Updated $dir"
}

$img.Dispose()
