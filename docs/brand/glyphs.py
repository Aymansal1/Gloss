"""Outline text from the bundled fonts as SVG path data (used by build.py).

Run from the repo root: python3 docs/brand/glyphs.py "gloss"
"""
import sys
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen

FONT = "app/src/main/res/font/instrument_serif_italic.ttf"


def outline(text, size, x=0.0, baseline=0.0, font=FONT, tracking=0.0):
    """Return (path_d, advance_width, bounds) for text set at `size` px with its baseline at y=baseline."""
    f = TTFont(font)
    gs = f.getGlyphSet()
    cmap = f.getBestCmap()
    upm = f["head"].unitsPerEm
    s = size / upm
    pen = SVGPathPen(gs)
    bounds = BoundsPen(gs)
    cursor = x
    hmtx = f["hmtx"]
    for ch in text:
        name = cmap[ord(ch)]
        # Flip y: font units go up, SVG goes down.
        t = (s, 0, 0, -s, cursor, baseline)
        gs[name].draw(TransformPen(pen, t))
        gs[name].draw(TransformPen(bounds, t))
        cursor += hmtx[name][0] * s + tracking
    return pen.getCommands(), cursor - x, bounds.bounds


if __name__ == "__main__":
    d, adv, b = outline(sys.argv[1], 100)
    print(adv, b)
    print(d[:200])
