# PowerShell script to update Android launcher icons

# Source directory containing the icons
$iconsDir = "$PSScriptRoot\icons"

# Destination directories for Android mipmap resources
$mipmapHdpi = "$PSScriptRoot\android\app\src\main\res\mipmap-hdpi"
$mipmapMdpi = "$PSScriptRoot\android\app\src\main\res\mipmap-mdpi"
$mipmapXhdpi = "$PSScriptRoot\android\app\src\main\res\mipmap-xhdpi"
$mipmapXxhdpi = "$PSScriptRoot\android\app\src\main\res\mipmap-xxhdpi"
$mipmapXxxhdpi = "$PSScriptRoot\android\app\src\main\res\mipmap-xxxhdpi"

# Create destination directories if they don't exist
$null = New-Item -ItemType Directory -Force -Path $mipmapHdpi
$null = New-Item -ItemType Directory -Force -Path $mipmapMdpi
$null = New-Item -ItemType Directory -Force -Path $mipmapXhdpi
$null = New-Item -ItemType Directory -Force -Path $mipmapXxhdpi
$null = New-Item -ItemType Directory -Force -Path $mipmapXxxhdpi

# Copy and rename the icon files
# Using the 48x48 icon for mdpi (1x)
Copy-Item -Path "$iconsDir\android-icon-48x48.png" -Destination "$mipmapMdpi\ic_launcher.png" -Force

# Using the 72x72 icon for hdpi (1.5x)
Copy-Item -Path "$iconsDir\android-icon-72x72.png" -Destination "$mipmapHdpi\ic_launcher.png" -Force

# Using the 96x96 icon for xhdpi (2x)
Copy-Item -Path "$iconsDir\android-icon-96x96.png" -Destination "$mipmapXhdpi\ic_launcher.png" -Force

# Using the 144x144 icon for xxhdpi (3x)
Copy-Item -Path "$iconsDir\android-icon-144x144.png" -Destination "$mipmapXxhdpi\ic_launcher.png" -Force

# Using the 192x192 icon for xxxhdpi (4x)
Copy-Item -Path "$iconsDir\android-icon-192x192.png" -Destination "$mipmapXxxhdpi\ic_launcher.png" -Force

Write-Host "Icons have been updated successfully!" -ForegroundColor Green
