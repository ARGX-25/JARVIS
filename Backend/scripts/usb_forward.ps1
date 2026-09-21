# Phone on USB (USB debugging on): make the phone's http://127.0.0.1:8765 reach Cuddy on this laptop.
# No firewall change needed. Re-run after unplugging/replugging the phone or restarting adb.
$adb = "P:\Coding\App Development\platform-tools\adb.exe"
& $adb devices
& $adb reverse tcp:8765 tcp:8765
& $adb reverse --list
