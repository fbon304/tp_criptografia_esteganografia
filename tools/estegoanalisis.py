#!/usr/bin/env python3
"""Ayuda para el estegoanálisis (punto 6). No forma parte de stegobmp ni hace falta para compilarlo.

Uso: python3 tools/estegoanalisis.py [-o carpeta] [--pass PASSWORD] [--steg LSB1|LSB4|LSBI]
                                     [--stegobmp RUTA] archivo.bmp [...]

Para cada BMP:
  1. Revisa el encabezado y avisa si el archivo tiene bytes de más después de los píxeles o un
     tamaño distinto al declarado (posible ocultamiento que no es LSB1, LSB4 ni LSBI). Esos bytes
     se guardan aparte para inspeccionarlos con un editor hex.
  2. Prueba LSB1, LSB4 y LSBI ejecutando `stegobmp -extract`: la extracción la hace siempre
     stegobmp; este script no decodifica nada por su cuenta.
     - Sin --pass: si stegobmp extrae, el archivo estaba oculto sin cifrar. Si falla porque el
       tamaño oculto excede la capacidad, ese método no se usó; si falla por otro motivo, el
       tamaño es coherente pero no hay extensión válida: probablemente esté cifrado.
     - Con --pass: prueba los 16 pares algoritmo/modo con esa contraseña hasta que uno funcione.

Por defecto usa el ejecutable que queda al descomprimir la distribución en
stegobmp/target/stegobmp/stegobmp (ver README); con --stegobmp se indica otra ruta.
"""
import argparse
import os
import struct
import subprocess
import sys

METHODS = ('LSB1', 'LSB4', 'LSBI')
CIPHERS = ('aes128', 'aes192', 'aes256', '3des')
MODES = ('cbc', 'ecb', 'cfb', 'ofb')
DEFAULT_STEGOBMP = os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir,
                                'stegobmp', 'target', 'stegobmp', 'stegobmp')
# Mensaje de StegoService cuando el tamaño leído no entra en el portador con ese método.
CAPACITY_ERROR = 'excede la capacidad'


def check_layout(path, data, outdir):
    """Análisis del encabezado: el único que hace el script sin stegobmp."""
    offset, = struct.unpack('<I', data[10:14])
    declared, = struct.unpack('<I', data[2:6])
    width, height = struct.unpack('<ii', data[18:26])
    bpp, = struct.unpack('<H', data[28:30])
    compression, = struct.unpack('<I', data[30:34])
    row = (width * 3 + 3) // 4 * 4
    expected = offset + row * abs(height)
    print(f'== {path}: {len(data)} bytes, {width}x{height}, {bpp} bpp, compresión {compression}, offset {offset}')
    if declared != len(data):
        print(f'   ! tamaño declarado en el encabezado ({declared}) distinto del real ({len(data)})')
    if len(data) != expected:
        print(f'   ! el archivo debería medir {expected} bytes: sobran {len(data) - expected} al final')
        if len(data) > expected:
            extra = os.path.join(outdir, os.path.basename(path) + '.extra.bin')
            with open(extra, 'wb') as file:
                file.write(data[expected:])
            print(f'     guardados en {extra}; primeros bytes: {data[expected:expected + 32]!r}')


def run_extract(stegobmp, path, out_base, method, cipher=None, mode=None, password=None):
    """Ejecuta stegobmp -extract y devuelve (archivo generado o None, mensaje de error)."""
    command = [stegobmp, '-extract', '-p', path, '-out', out_base, '-steg', method]
    if password is not None:
        command += ['-a', cipher, '-m', mode, '-pass', password]
    directory = os.path.dirname(out_base) or '.'
    before = set(os.listdir(directory))
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        return None, result.stderr.strip().splitlines()[0] if result.stderr.strip() else 'error'
    prefix = os.path.basename(out_base) + '.'
    created = [name for name in set(os.listdir(directory)) - before if name.startswith(prefix)]
    # Si el archivo ya existía de una corrida anterior, stegobmp lo sobrescribe: se busca por prefijo.
    if not created:
        created = [name for name in os.listdir(directory) if name.startswith(prefix)]
    return (os.path.join(directory, created[0]) if created else out_base), None


def try_methods(stegobmp, path, outdir, methods, password):
    for method in methods:
        out_base = os.path.join(outdir, f'{os.path.basename(path)}.{method}')
        if password is None:
            output, error = run_extract(stegobmp, path, out_base, method)
            if output:
                print(f'   {method}: ENCONTRADO sin cifrar -> {output}')
            elif CAPACITY_ERROR in error:
                print(f'   {method}: tamaño incoherente, no se usó este método')
            else:
                print(f'   {method}: tamaño coherente pero sin extensión válida; posible cifrado ({error})')
            continue

        for cipher in CIPHERS:
            for mode in MODES:
                output, _ = run_extract(stegobmp, path, out_base, method, cipher, mode, password)
                if output:
                    print(f'   {method}: ENCONTRADO con -a {cipher} -m {mode} -pass {password} -> {output}')
                    break
            else:
                continue
            break
        else:
            print(f'   {method}: ningún algoritmo/modo funcionó con esa contraseña')


def main(argv):
    parser = argparse.ArgumentParser(description='Estegoanálisis de BMP usando stegobmp para extraer.')
    parser.add_argument('files', nargs='+', metavar='archivo.bmp')
    parser.add_argument('-o', dest='outdir', default='.', help='carpeta de salida (por defecto: actual)')
    parser.add_argument('--pass', dest='password', help='probar los 16 algoritmos/modos con esta contraseña')
    parser.add_argument('--steg', choices=METHODS, help='probar solo este método')
    parser.add_argument('--stegobmp', default=DEFAULT_STEGOBMP, help='ruta al ejecutable stegobmp')
    args = parser.parse_args(argv)

    stegobmp = os.path.abspath(args.stegobmp)
    if not os.access(stegobmp, os.X_OK):
        sys.exit(f'No se encontró stegobmp en {stegobmp}.\n'
                 'Compilar con ./mvnw clean package y descomprimir target/stegobmp.tar.gz (ver README), '
                 'o indicar la ruta con --stegobmp.')
    os.makedirs(args.outdir, exist_ok=True)
    methods = (args.steg,) if args.steg else METHODS
    for path in args.files:
        with open(path, 'rb') as file:
            data = file.read()
        check_layout(path, data, args.outdir)
        try_methods(stegobmp, path, args.outdir, methods, args.password)


if __name__ == '__main__':
    main(sys.argv[1:])
