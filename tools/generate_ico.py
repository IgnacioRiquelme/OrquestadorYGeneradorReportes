#!/usr/bin/env python3
import os
import shutil
from PIL import Image

SRC_PNG = r"C:\Users\IARC\Desktop\bci seguros.png"
DST_ICO = r"C:\Users\IARC\Desktop\OrquestadorYGeneradorReportes\assets\bci_seguros.ico"
BACKUP = DST_ICO + ".bak"

def main():
    if not os.path.exists(SRC_PNG):
        print('ERROR: PNG fuente no encontrada:', SRC_PNG)
        raise SystemExit(2)

    if os.path.exists(DST_ICO):
        try:
            shutil.copy2(DST_ICO, BACKUP)
            print('Backup creado:', BACKUP)
        except Exception as e:
            print('Warning: no se pudo crear backup:', e)

    img = Image.open(SRC_PNG).convert('RGBA')
    sizes = [(16,16),(32,32),(48,48),(64,64),(128,128),(256,256)]
    try:
        img.save(DST_ICO, format='ICO', sizes=sizes)
        print('ICO guardado en:', DST_ICO)
        st = os.stat(DST_ICO)
        print('Tamaño (bytes):', st.st_size)
    except Exception as e:
        print('ERROR al guardar ICO:', e)
        raise

if __name__ == '__main__':
    main()
