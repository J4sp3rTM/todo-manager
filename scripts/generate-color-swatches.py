"""
Generates small SVG color swatch files for the Marketplace configuration page.
Output: .github/assets/swatches/<name>.svg
Run: python scripts/generate-color-swatches.py
"""

import os

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", ".github", "assets", "swatches")

SWATCHES = {
    # keywords
    "todo":            "#42A5F5",
    "fixme":           "#E53935",
    "hack":            "#FF9800",
    "note":            "#00ACC1",
    "xxx":             "#AB47BC",
    "other-keywords":  "#9E9E9E",
    # priorities
    "critical":        "#D32F2F",
    "high":            "#E53935",
    "medium":          "#FFB300",
    "low":             "#4CAF50",
    # comment text
    "description":     "#808080",
    "delimiter":       "#6A737D",
}

SVG = (
    '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14">'
    '<rect width="14" height="14" rx="3" fill="{color}"/>'
    "</svg>\n"
)

os.makedirs(OUT_DIR, exist_ok=True)

for name, color in SWATCHES.items():
    path = os.path.join(OUT_DIR, f"{name}.svg")
    with open(path, "w") as f:
        f.write(SVG.format(color=color))
    print(f"  {name}.svg  {color}")

print(f"\nWrote {len(SWATCHES)} swatches to {os.path.relpath(OUT_DIR)}")
