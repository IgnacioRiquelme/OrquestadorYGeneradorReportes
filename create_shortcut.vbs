Set WshShell = WScript.CreateObject("WScript.Shell")
desk = WshShell.SpecialFolders("Desktop")

' Ruta al VBS que ejecuta el BAT en oculto
scriptVbs = WshShell.CurrentDirectory & "\\launch_hidden.vbs"

' Ruta al ejecutable que arranca VBS sin consola
systemRoot = WshShell.ExpandEnvironmentStrings("%SystemRoot%")
wscriptPath = systemRoot & "\\System32\\wscript.exe"

Set lnk = WshShell.CreateShortcut(desk & "\\OrquestadorYGeneradorReportes.lnk")
lnk.TargetPath = wscriptPath
lnk.Arguments = Chr(34) & scriptVbs & Chr(34)
lnk.WorkingDirectory = WshShell.CurrentDirectory
lnk.WindowStyle = 1
lnk.Description = "Acceso directo para ejecutar OrquestadorYGeneradorReportes (sin consola)"
' Opcional: icono del proyecto si existe
iconPath = WshShell.CurrentDirectory & "\\assets\\bci_seguros.ico.bak"
If CreateObject("Scripting.FileSystemObject").FileExists(iconPath) Then
	lnk.IconLocation = iconPath
End If
lnk.Save
