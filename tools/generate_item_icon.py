"""Generate Sealant's original 16x16 cartridge icon using only the standard library.

Copyright (c) 2026 Sealant contributors. MIT licensed.
No upstream bitmap is read or used as a template.
"""
from pathlib import Path
import struct
import zlib

SIZE = 16
PALETTE = {
    '.': (0, 0, 0, 0),
    'o': (23, 47, 57, 255),
    'b': (40, 115, 143, 255),
    'c': (65, 176, 201, 255),
    'h': (151, 232, 228, 255),
    'w': (233, 248, 230, 255),
    'g': (128, 149, 153, 255),
}
# A vertical teal sealing cartridge with pale collar and tapered metal nozzle.
PIXELS = [
    '................',
    '.......oo.......',
    '.......og.......',
    '......oggo......',
    '......ogwo......',
    '.....oooooo.....',
    '.....owwwwo.....',
    '.....oooooo.....',
    '.....obhcco.....',
    '.....obhcco.....',
    '.....obwwco.....',
    '.....obwwco.....',
    '.....obhcco.....',
    '.....obbbbo.....',
    '....oooooooo....',
    '................',
]

def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))


def main():
    assert len(PIXELS) == SIZE and all(len(row) == SIZE for row in PIXELS)
    raw = b''.join(b'\0' + bytes(component for pixel in row for component in PALETTE[pixel]) for row in PIXELS)
    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', SIZE, SIZE, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    target = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/sealant/textures/item/sealant.png'
    target.write_bytes(png)

if __name__ == '__main__':
    main()
