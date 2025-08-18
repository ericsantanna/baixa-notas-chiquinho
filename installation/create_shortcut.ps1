$WshShell = New-Object -comObject WScript.Shell
$Shortcut = $WshShell.CreateShortcut("$([Environment]::GetFolderPath('Desktop'))\Baixa Notas Chiquinho.lnk")
$Shortcut.TargetPath = "$PSScriptRoot\executar.bat"
$Shortcut.WorkingDirectory = "$PSScriptRoot"
$Shortcut.IconLocation = "$PSScriptRoot\app.ico"  # Optional: add an icon
$Shortcut.Description = "Baixa Notas Chiquinho Application"
$Shortcut.WindowStyle = 7  # Minimized window
$Shortcut.Save()