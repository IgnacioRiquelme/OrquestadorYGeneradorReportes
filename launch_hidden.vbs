Set WshShell = CreateObject("WScript.Shell")
script = "C:\\Users\\IARC\\Desktop\\OrquestadorYGeneradorReportes\\ejecutar.bat"
' Ejecutar el BAT de forma oculta (0 = oculto, True = esperar a que termine)
WshShell.Run """" & script & """", 0, False
