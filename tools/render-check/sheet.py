#!/usr/bin/env python3
"""Tile rendered frames into one contact sheet: sheet.py <frames_dir> <out.png> [cols] [crop=x0,y0,x1,y1]"""
import glob, sys
from PIL import Image

frames_dir, out = sys.argv[1], sys.argv[2]
cols = int(sys.argv[3]) if len(sys.argv) > 3 else 3
crop = tuple(int(v) for v in sys.argv[4].split(',')) if len(sys.argv) > 4 else None
imgs = [Image.open(f).convert('RGB') for f in sorted(glob.glob(frames_dir + '/frame_*.png'))]
if crop:
    imgs = [im.crop(crop) for im in imgs]
w, h = imgs[0].size
rows = (len(imgs) + cols - 1) // cols
sheet = Image.new('RGB', (cols * w, rows * h))
for i, im in enumerate(imgs):
    sheet.paste(im, ((i % cols) * w, (i // cols) * h))
sheet.save(out)
print(sheet.size)
