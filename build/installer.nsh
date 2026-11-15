; The app runs on Electron, which needs Windows 10 or 11 on a 64-bit PC (#107). On anything older the
; installer used to finish, and only the app said "This app can't run on your PC". Now the installer says
; why and stops. Windows on ARM runs the 64-bit app through emulation, so it may go ahead.
!include WinVer.nsh
!include x64.nsh

!macro customInit
  ${IfNot} ${AtLeastWin10}
    MessageBox MB_OK|MB_ICONSTOP "CleanMyTV needs Windows 10 or 11. This PC has an older Windows." /SD IDOK
    Quit
  ${EndIf}
  ${IfNot} ${RunningX64}
  ${AndIfNot} ${IsNativeARM64}
    MessageBox MB_OK|MB_ICONSTOP "CleanMyTV needs 64-bit Windows. This PC runs 32-bit Windows." /SD IDOK
    Quit
  ${EndIf}
!macroend
