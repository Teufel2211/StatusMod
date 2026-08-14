from PIL import Image, ImageDraw, ImageFont

OUT = r"C:\Users\Steven\Desktop\StatusMod\docs\statusmod-icon.png"
FINAL = 512
SUPER = 4
SIZE = FINAL * SUPER

BG = (14, 21, 32)  # #0E1520 obsidian navy
RING = (27, 35, 53)  # #1B2735 subtle border
TEAL = (45, 212, 167)  # #2DD4A7
WHITE = (244, 246, 248)
GREEN = (85, 255, 85)
RED = (255, 85, 85)
YELLOW = (255, 255, 85)
EMPTY = (42, 51, 66)

TITLE_FONT = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 46 * SUPER)
STATUS_FONT = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 38 * SUPER)
NAME_FONT = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 40 * SUPER)


def s(v):
    return v * SUPER


img = Image.new("RGB", (SIZE, SIZE), BG)
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


lettered_text(256, 100, "STATUS PLAYER", TITLE_FONT, WHITE, s(6))
d.rounded_rectangle([s(96), s(138), s(416), s(142)], radius=s(2), fill=TEAL)

rows = [
    ("Bauen", GREEN, "Max"),
    ("Farmen", YELLOW, "Tom"),
    ("AFK", RED, "Noah"),
    ("PvP", TEAL, "Finn"),
]

for i, (status, status_col, name) in enumerate(rows):
    cy = 205 + i * 72
    d.text((s(115), s(cy)), status, font=STATUS_FONT, fill=status_col, anchor="lm")
    d.text((s(290), s(cy)), name, font=NAME_FONT, fill=WHITE, anchor="lm")

for i, (status, status_col, name) in enumerate(rows):
    w_s = d.textlength(status, font=STATUS_FONT)
    w_n = d.textlength(name, font=NAME_FONT)
    print(f"row{i}: status '{status}' width={w_s:.0f} (ends ~{115 + w_s:.0f})  name '{name}' width={w_n:.0f} (ends ~{290 + w_n:.0f})")
w_t = d.textlength("STATUS PLAYER", font=TITLE_FONT) + 12 * 6 * SUPER
print(f"title width ~{w_t:.0f} (canvas {SIZE})")

img = img.resize((FINAL, FINAL), Image.LANCZOS)
img.save(OUT, "PNG")
print("WROTE", OUT)
