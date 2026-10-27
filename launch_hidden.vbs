Set WshShell = CreateObject("WScript.Shell")
strPath = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName)
WshShell.CurrentDirectory = strPath
script = strPath & "\ejecutar.bat"
' Ejecutar el BAT de forma oculta (0 = oculto). Si hay un error, ejecutar.bat
' abrira automaticamente una ventana visible mostrando el error.
WshShell.Run """" & script & """", 0, False
