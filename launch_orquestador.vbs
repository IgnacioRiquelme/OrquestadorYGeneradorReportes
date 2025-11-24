Set WshShell = CreateObject("WScript.Shell")
WshShell.CurrentDirectory = "C:\Users\IARC\Desktop\OrquestadorYGeneradorReportes"
WshShell.Run "cmd /c mvn javafx:run", 0, False
Set WshShell = Nothing
