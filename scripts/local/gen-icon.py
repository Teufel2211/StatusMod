import argparse

from PIL import Image, ImageDraw, ImageFont

BG = (14, 21, 32)  # #0E1520 obsidian navy
RING = (27, 35, 53)  # #1B2735 subtle border
TEAL = (45, 212, 167)  # #2DD4A7
WHITE = (244, 246, 248)
GREEN = (85, 255, 85)
RED = (255, 85, 85)
YELLOW = (255, 255, 85)
EMPTY = (42, 51, 66)

FONT = r"C:\Windows\Fonts\arialbd.ttf"
TITLE = "STATUS PLAYER"
ROWS = [
    ("Bauen", GREEN, "Max"),
    ("Farmen", YELLOW, "Tom"),
    ("AFK", RED, "Noah"),
    ("PvP", TEAL, "Finn"),
]

SUPER = 4
BASE = 512  # layout designed in 512 space


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=r"C:\Users\Steven\Desktop\StatusMod\docs\statusmod-icon.png")
    ap.add_argument("--size", type=int, default=512, help="final pixel size")
    ap.add_argument("--transparent", action="store_true", help="transparent corners (RGBA)")
    args = ap.parse_args()

    FINAL = args.size
    K = FINAL / BASE
    SIZE = FINAL * SUPER

    def s(v):
        return int(round(v * K * SUPER))

    title_font = ImageFont.truetype(FONT, round(46 * K * SUPER))
    status_font = ImageFont.truetype(FONT, round(38 * K * SUPER))
    name_font = ImageFont.truetype(FONT, round(40 * K * SUPER))

    img = Image.new(
        "RGBA" if args.transparent else "RGB",
        (SIZE, SIZE),
        (0, 0, 0, 0) if args.transparent else BG,
    )
    d = ImageDraw.Draw(img)

    d.rounded_rectangle([0, 0, SIZE - 1, SIZE - 1], radius=s(96), fill=BG)
    d.rounded_rectangle(
        [s(10), s(10), SIZE - s(10) - 1, SIZE - s(10) - 1],
        radius=s(88),
        outline=RING,
        width=s(4),
    )

    def lettered_text(cx, cy, text, font, fill, spacing_px):
        widths = [d.textlength(ch, font=font) for ch in text]
        total = sum(widths) + spacing_px * (len(text) - 1)
        x = s(cx) - total / 2
        for ch, wd in zip(text, widths):
            d.text((x, s(cy)), ch, font=font, fill=fill, anchor="lm")
            x += wd + spacing_px

    lettered_text(256, 100, TITLE, title_font, WHITE, s(6))
    d.rounded_rectangle([s(96), s(138), s(416), s(142)], radius=s(2), fill=TEAL)

    for i, (status, status_col, name) in enumerate(ROWS):
        cy = 205 + i * 72
        d.text((s(115), s(cy)), status, font=status_font, fill=status_col, anchor="lm")
        d.text((s(290), s(cy)), name, font=name_font, fill=WHITE, anchor="lm")

    for i, (status, _, name) in enumerate(ROWS):
        w_s = d.textlength(status, font=status_font)
        w_n = d.textlength(name, font=name_font)
        print(f"row{i}: status '{status}' ends ~{(115 + w_s / SUPER) * K:.0f}  name '{name}' ends ~{(290 + w_n / SUPER) * K:.0f}")
    w_t = d.textlength(TITLE, font=title_font) + 12 * 6 * SUPER
    print(f"title width ~{w_t / SUPER * K:.0f} (canvas {FINAL})")

    img = img.resize((FINAL, FINAL), Image.LANCZOS)
    img.save(args.out, "PNG")
    print("WROTE", args.out)


if __name__ == "__main__":
    main()
