#!/usr/bin/env python3
"""Overlay the real HUD (rendered by the Robolectric HudOverlayTest) on rendered 3D frames.

usage: compose.py <frames_dir> <overlay_dir> <names.txt> <out_dir> [name,name,...]
Writes one composited PNG per requested frame name (default: all)."""
import os, sys
from PIL import Image

frames, overlays, names_file, out = sys.argv[1:5]
wanted = sys.argv[5].split(',') if len(sys.argv) > 5 else None
names = [l.strip() for l in open(names_file) if l.strip()]
os.makedirs(out, exist_ok=True)
for i, name in enumerate(names):
    if wanted and name not in wanted:
        continue
    scene = Image.open(os.path.join(frames, f'frame_{i:04d}.png')).convert('RGBA')
    hud = Image.open(os.path.join(overlays, f'overlay_{i:04d}.png')).convert('RGBA')
    if hud.size != scene.size:
        hud = hud.resize(scene.size, Image.LANCZOS)
    scene.alpha_composite(hud)
    scene.convert('RGB').save(os.path.join(out, f'{name}.png'))
    print('wrote', name)
