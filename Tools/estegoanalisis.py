#!/usr/bin/env python3
"""Ayuda para el estegoanálisis (punto 6): prueba LSB1, LSB4 y LSBI sobre cada BMP.

Uso: python3 tools/estegoanalisis.py archivo.bmp [...]   (opcional: -o carpeta_salida)

Para cada método lee el tamaño (4 bytes big endian) y, si es coherente con la capacidad,
busca la extensión ".xxx\\0" después de los datos. Si aparece, el archivo estaba oculto sin
cifrar y lo extrae. Si el tamaño es coherente pero no hay extensión, probablemente esté
cifrado (queda probar algoritmo, modo y password con stegobmp). También avisa si el archivo
tiene bytes de más después de los píxeles o un tamaño distinto al declarado en el encabezado.
"""
import os
import struct
import sys


def bits_to_bytes(bits):
    out = bytearray()
    for i in range(0, len(bits) - 7, 8):
        value = 0
        for bit in bits[i:i + 8]:
            value = (value << 1) | bit
        out.append(value)
    return bytes(out)


def lsb1(body, count):
    return bits_to_bytes([b & 1 for b in body[:count * 8]]), len(body) // 8


def lsb4(body, count):
    chunk = body[:count * 2]
    return bytes(((chunk[i] & 15) << 4) | (chunk[i + 1] & 15) for i in range(0, len(chunk) - 1, 2)), len(body) // 2


def lsbi(body, count):
    inverted = [b & 1 for b in body[:4]]
    capacity = (len(body) - len(body) // 3 - 3) // 8
    bits = []
    index = 4
    while len(bits) < count * 8 and index < len(body):
        if index % 3 != 2:  # se saltea el rojo
            value = body[index]
            bits.append((value & 1) ^ inverted[(value >> 1) & 3])
        index += 1
    return bits_to_bytes(bits), capacity


def analyze(path, outdir):
    data = open(path, 'rb').read()
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
            open(extra, 'wb').write(data[expected:])
            print(f'     guardados en {extra}; primeros bytes: {data[expected:expected + 32]!r}')
    body = data[offset:]
    for name, method in (('LSB1', lsb1), ('LSB4', lsb4), ('LSBI', lsbi)):
        head, capacity = method(body, 4)
        size, = struct.unpack('>I', head)
        if size == 0 or size + 4 > capacity:
            print(f'   {name}: tamaño {size} incoherente (capacidad {capacity})')
            continue
        hidden, _ = method(body, min(capacity, 4 + size + 16))
        tail = hidden[4 + size:]
        end = tail.find(b'\0')
        if tail[:1] == b'.' and end > 1 and all(32 < c < 127 for c in tail[:end]):
            ext = tail[:end].decode('ascii')
            out = os.path.join(outdir, f'{os.path.basename(path)}.{name}{ext}')
            open(out, 'wb').write(hidden[4:4 + size])
            print(f'   {name}: ENCONTRADO sin cifrar, {size} bytes, extensión {ext} -> {out}')
        else:
            blocks = [b for b in (8, 16) if size % b == 0]
            hint = f'múltiplo de {blocks}: ¿ecb/cbc?' if blocks else 'no es múltiplo de bloque: ¿cfb/ofb?'
            print(f'   {name}: tamaño {size} coherente pero sin extensión; posible cifrado ({hint})')


def main(argv):
    outdir = '.'
    if '-o' in argv:
        position = argv.index('-o')
        outdir = argv[position + 1]
        del argv[position:position + 2]
        os.makedirs(outdir, exist_ok=True)
    if not argv:
        sys.exit(__doc__)
    for path in argv:
        analyze(path, outdir)


if __name__ == '__main__':
    main(sys.argv[1:])