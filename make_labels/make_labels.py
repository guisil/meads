#!/usr/bin/env python3
"""Generate an A4 PDF of judging labels, one page per (entry number, entry code) pair.

Layout per page mirrors CHIP_judging_label_scheme.txt: a short top row holding the
entry number (2 cells), then three taller rows holding the entry code (2 cells each).
All cells are cut-out labels to tape on glasses.
"""
from pathlib import Path
from fpdf import FPDF

SRC = Path.home() / "Downloads" / "CHIP_judging_labels.txt"
OUT = Path.home() / "Downloads" / "CHIP_judging_labels.pdf"

# A4 portrait, mm. Outer margin then a 2-column grid.
MARGIN = 10.0
PAGE_W, PAGE_H = 210.0, 297.0
COLS = 2
NUM_ROW_H = 28.0          # entry number: shorter
CODE_ROWS = 3             # entry code: three equal, taller rows

NUM_FONT = 34
CODE_FONT = 56

usable_w = PAGE_W - 2 * MARGIN
usable_h = PAGE_H - 2 * MARGIN
col_w = usable_w / COLS
code_row_h = (usable_h - NUM_ROW_H) / CODE_ROWS


def read_pairs(path):
    lines = [ln.strip() for ln in path.read_text().splitlines() if ln.strip()]
    if len(lines) % 2 != 0:
        raise SystemExit(f"Expected an even number of non-empty lines, got {len(lines)}")
    return list(zip(lines[0::2], lines[1::2]))


def fit_font(pdf, text, max_w, start_size):
    """Shrink font until text fits within max_w (with a little padding)."""
    size = start_size
    pdf.set_font("Helvetica", "B", size)
    while pdf.get_string_width(text) > max_w - 6 and size > 8:
        size -= 1
        pdf.set_font("Helvetica", "B", size)
    return size


def cell_box(pdf, x, y, w, h, text, start_size):
    fit_font(pdf, text, w, start_size)
    pdf.set_xy(x, y)
    pdf.cell(w, h, text, border=1, align="C")


pairs = read_pairs(SRC)

pdf = FPDF(orientation="P", unit="mm", format="A4")
pdf.set_auto_page_break(False)

for number, code in pairs:
    pdf.add_page()
    # top row: entry number across both columns
    for c in range(COLS):
        cell_box(pdf, MARGIN + c * col_w, MARGIN, col_w, NUM_ROW_H, number, NUM_FONT)
    # code rows
    for r in range(CODE_ROWS):
        y = MARGIN + NUM_ROW_H + r * code_row_h
        for c in range(COLS):
            cell_box(pdf, MARGIN + c * col_w, y, col_w, code_row_h, code, CODE_FONT)

pdf.output(str(OUT))
print(f"Wrote {OUT} — {len(pairs)} pages")
