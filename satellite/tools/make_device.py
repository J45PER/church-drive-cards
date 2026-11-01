#!/usr/bin/env python3
"""Makes a new satellite: its permanent ID, its own API key, its ESPHome file and its sticker.

    python3 satellite/tools/make_device.py --model RND --gen 1 [--number 123] [--room "Living room"]

The ID looks like RND1-000123-7: a model code (RND round screen, SPK speaker with no screen, DSP small display), the hardware
generation, a running number for that model and generation, and a check character that lets the app tell a typing mistake from a
code that doesn't exist (the same sum as app/satellites DeviceId.kt). The number comes from satellite/devices/registry.csv, which holds
every device's key, so it is git-ignored: keep a backup of it. Needs `pip install qrcode pillow`.

Writes satellite/devices/<id>/:
  <id>.yaml        compile and flash this one: `esphome run satellite/devices/<id>/<id>.yaml`
  <id>-label.png   the sticker, at 203 dpi (a Phomemo D30). Default size 14x60 mm; --size picks another (see --list-sizes), QR code on the
                   left and the ID and name beside it on a long label, QR code on top and the ID underneath on a squarish one
The sticker's QR code is clive://pair?id=...&k=... : it carries the device's own key, so there is no shared key to type.

Other things it does with devices already made:
  --reprint ID [--size 14x30]   draw that device's sticker again (a lost or a different size one)
  --export-csv labels.csv       one row per device (id, model, room, name, qr_text) for a printer app that prints a QR code from a spreadsheet
                                column. The file holds every device's key inside qr_text: keep it out of git and delete it after printing.
"""
import argparse, base64, csv, secrets, sys, urllib.parse
from pathlib import Path

ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"   # Crockford base 32: no I, L, O or U
MODELS = {"RND": "Round screen", "SPK": "Speaker", "DSP": "Small display"}
ROOT = Path(__file__).resolve().parents[1]
DEVICES = ROOT / "devices"


def check_char(body: str) -> str:
    """Luhn mod 32 over the characters of body (model, generation and number, without dashes)."""
    n, factor, total = len(ALPHABET), 2, 0
    for c in reversed(body):
        addend = factor * ALPHABET.index(c)
        factor = 1 if factor == 2 else 2
        total += addend // n + addend % n
    return ALPHABET[(n - total % n) % n]


def make_id(model: str, gen: int, number: int) -> str:
    body = f"{model}{gen}{number:06d}"
    return f"{model}{gen}-{number:06d}-{check_char(body)}"


def next_number(rows, model, gen):
    mine = [int(r["number"]) for r in rows if r["model"] == model and r["gen"] == str(gen)]
    return max(mine, default=0) + 1


# Label sizes in millimetres (width x height, the way the label is read). The D30 prints up to 15 mm wide; any WxH works with --size.
SIZES = {"12x40": (40, 12), "14x30": (30, 14), "14x40": (40, 14), "14x50": (50, 14), "14x60": (60, 14), "15x50": (50, 15), "25x25": (25, 25)}
DPI = 203


def parse_size(text: str):
    if text in SIZES: return SIZES[text]
    try:
        a, b = text.lower().split("x"); return float(b), float(a)       # "14x60" is 14 wide as the label comes off the roll: 60 long
    except ValueError:
        sys.exit("Sizes look like 14x60 (millimetres): " + ", ".join(SIZES))


def draw_label(path: Path, device_id: str, model_name: str, payload: str, room: str, size=(60, 14)):
    import qrcode
    from PIL import Image, ImageDraw, ImageFont
    w, h = round(size[0] / 25.4 * DPI), round(size[1] / 25.4 * DPI)
    img = Image.new("1", (w, h), 1)
    qr = qrcode.QRCode(border=0, box_size=1, error_correction=qrcode.constants.ERROR_CORRECT_M)
    qr.add_data(payload); qr.make(fit=True)
    q = qr.make_image(fill_color="black", back_color="white").convert("1")
    d = ImageDraw.Draw(img)
    pad = max(3, round(h * 0.06))
    def font(px, bold=False):
        for name in (["DejaVuSansMono-Bold.ttf", "DejaVuSans-Bold.ttf"] if bold else ["DejaVuSans.ttf"]):
            try: return ImageFont.truetype(name, px)
            except OSError: pass
        return ImageFont.load_default()
    def fit(text, width, top, bold=False):
        px = top
        while px > 8 and d.textlength(text, font=font(px, bold)) > width: px -= 1
        return font(px, bold)
    sub = model_name + (" · " + room if room else "")
    if w / h >= 1.7:                                   # a long label: QR on the left, text beside it
        side = h - 2 * pad
        img.paste(q.resize((side, side), Image.NEAREST), (pad, pad))
        x, room_w = side + 3 * pad, w - side - 4 * pad
        f1 = fit(device_id, room_w, round(h * 0.34), True)
        f2 = fit(sub, room_w, round(h * 0.2))
        d.text((x, pad + round(h * 0.06)), device_id, font=f1, fill=0)
        d.text((x, round(h * 0.6)), sub, font=f2, fill=0)
    else:                                              # a squarish one: QR on top, the ID and the line under it
        id_px, sub_px = round(h * 0.12), round(h * 0.085)
        f1 = fit(device_id, w - 2 * pad, id_px, True)
        f2 = fit(sub, w - 2 * pad, sub_px)
        side = min(w - 2 * pad, h - 3 * pad - f1.size - f2.size - 8)
        img.paste(q.resize((side, side), Image.NEAREST), ((w - side) // 2, pad))
        y = pad + side + pad
        d.text(((w - d.textlength(device_id, font=f1)) / 2, y), device_id, font=f1, fill=0)
        d.text(((w - d.textlength(sub, font=f2)) / 2, y + f1.size + 6), sub, font=f2, fill=0)
    img.save(path, dpi=(DPI, DPI))


def qr_text(device_id, name, key):
    # The device name is the ID in lowercase (the app works it out), so it is left out: a shorter code is a bigger, easier to scan QR on a small label.
    return "clive://pair?id=%s&k=%s" % (device_id, urllib.parse.quote(key, safe=""))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", choices=sorted(MODELS))
    ap.add_argument("--gen", type=int, default=1)
    ap.add_argument("--number", type=int, help="use this running number instead of the next free one")
    ap.add_argument("--room", default="", help="printed on the sticker, and the device's friendly name")
    ap.add_argument("--base", default="clive-round.yaml", help="the ESPHome file the device is built from")
    ap.add_argument("--size", default="14x60", help="label size in mm, e.g. 14x60 (default), 14x30, 25x25 or any WxH; see --list-sizes")
    ap.add_argument("--list-sizes", action="store_true", help="show the named label sizes")
    ap.add_argument("--reprint", metavar="ID", help="draw this device's sticker again, at --size")
    ap.add_argument("--export-csv", metavar="FILE", help="write every device as a row of a CSV (id, model, room, name, qr_text)")
    a = ap.parse_args()
    if a.list_sizes:
        for k, (w, h) in SIZES.items(): print(f"{k:6} {w:g} x {h:g} mm")
        return
    DEVICES.mkdir(exist_ok=True)
    reg = DEVICES / "registry.csv"
    rows = list(csv.DictReader(reg.open())) if reg.exists() else []
    if a.export_csv:
        with open(a.export_csv, "w", newline="") as f:
            w = csv.writer(f); w.writerow(["id", "model", "room", "name", "qr_text"])
            for r in rows: w.writerow([r["id"], MODELS.get(r["model"], r["model"]), r["room"], r["name"], qr_text(r["id"], r["name"], r["key"])])
        print(f"{len(rows)} devices -> {a.export_csv}  (it holds their keys: keep it out of git and delete it after printing)")
        return
    if a.reprint:
        r = next((r for r in rows if r["id"].upper() == a.reprint.upper()), None)
        if r is None: sys.exit(f"{a.reprint} is not in the registry.")
        out = DEVICES / r["id"]; out.mkdir(exist_ok=True)
        p = out / f"{r['id']}-label-{a.size}.png"
        draw_label(p, r["id"], MODELS.get(r["model"], ""), qr_text(r["id"], r["name"], r["key"]), r["room"], parse_size(a.size))
        print(p)
        return
    if not a.model: sys.exit("--model is needed to make a new device.")
    if not 1 <= a.gen <= 9: sys.exit("The generation is one digit, 1 to 9.")
    number = a.number or next_number(rows, a.model, a.gen)
    if number > 999999: sys.exit("The running number is six digits.")
    device_id = make_id(a.model, a.gen, number)
    if any(r["id"] == device_id for r in rows): sys.exit(f"{device_id} is already in the registry.")
    name = f"clive-{a.model.lower()}{a.gen}-{number:06d}"       # ESPHome, Bluetooth and network name: lowercase, 17 characters
    key = base64.b64encode(secrets.token_bytes(32)).decode()
    friendly = f"Clive ({MODELS[a.model].lower()})" + (f" {a.room}" if a.room else "")
    out = DEVICES / device_id
    out.mkdir(exist_ok=True)
    (out / f"{device_id}.yaml").write_text(
        f"# {device_id}: made by tools/make_device.py. Compile and flash with: esphome run {device_id}.yaml\n"
        f"substitutions:\n  name: {name}\n  friendly_name: \"{friendly}\"\n  api_key: \"{key}\"\n\n"
        f"packages:\n  base: !include ../../{a.base}\n")
    draw_label(out / f"{device_id}-label.png", device_id, MODELS[a.model], qr_text(device_id, name, key), a.room, parse_size(a.size))
    new = not reg.exists()
    with reg.open("a", newline="") as f:
        w = csv.DictWriter(f, ["id", "model", "gen", "number", "name", "key", "room"])
        if new: w.writeheader()
        w.writerow({"id": device_id, "model": a.model, "gen": a.gen, "number": number, "name": name, "key": key, "room": a.room})
    print(f"{device_id}  ->  {out}")
    print(f"  flash:  esphome run {out / (device_id + '.yaml')}")
    print(f"  label:  {out / (device_id + '-label.png')}  ({a.size} mm)")


if __name__ == "__main__":
    main()
