@echo off
echo Creating desktop shortcut...
powershell -ExecutionPolicy Bypass -File "create_shortcut.ps1"
echo Desktop shortcut created successfully!
echo You can now run the application from your desktop.
pause