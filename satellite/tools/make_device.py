#!/usr/bin/env python3
"""Makes a new satellite: its permanent ID, its own API key, its ESPHome file and its sticker.

    python3 satellite/tools/make_device.py --model RND --gen 1 [--number 123] [--room "Living room"]

The ID looks like RND1-000123-7: a model code (RND round screen, SPK speaker with no screen, DSP small display), the hardware
generation, a running number for that model and generation, and a check character that lets the app tell a typing mistake from a
code that doesn't exist (the same sum as app/satellites DeviceId.kt). The number comes from satellite/devices/registry.csv, which holds
every device's key, so it is git-ignored: keep a backup of it. Needs `pip install qrcode pillow`.

Writes satellite/devices/<id>/:
  <id>.yaml        compile and flash this one: `esphome run satellite/devices/<id>/<id>.yaml`
  <id>-label.png   the sticker, drawn for a 14 x 60 mm label at 203 dpi (a Phomemo D30), QR code on the left, the ID and name beside it
The sticker's QR code is clive://pair?id=...&d=...&k=... : it carries the device's own key, so there is no shared key to type.
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


def draw_label(path: Path, device_id: str, model_name: str, payload: str, room: str):
    import qrcode
    from PIL import Image, ImageDraw, ImageFont
    w, h = 480, 112                                # 60 x 14 mm at 203 dpi
    img = Image.new("1", (w, h), 1)
    qr = qrcode.QRCode(border=0, box_size=1, error_correction=qrcode.constants.ERROR_CORRECT_M)
    qr.add_data(payload); qr.make(fit=True)
    q = qr.make_image(fill_color="black", back_color="white").convert("1")
    side = h - 8
    q = q.resize((side, side), Image.NEAREST)
    img.paste(q, (4, 4))
    d = ImageDraw.Draw(img)
    def font(size, bold=False):
        for name in (["DejaVuSansMono-Bold.ttf", "DejaVuSans-Bold.ttf"] if bold else ["DejaVuSans.ttf"]):
            try: return ImageFont.truetype(name, size)
            except OSError: pass
        return ImageFont.load_default()
    x = side + 16
    d.text((x, 12), device_id, font=font(34, True), fill=0)
    d.text((x, 62), model_name + (" · " + room if room else ""), font=font(22), fill=0)
    img.save(path, dpi=(203, 203))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", required=True, choices=sorted(MODELS))
    ap.add_argument("--gen", type=int, default=1)
    ap.add_argument("--number", type=int, help="use this running number instead of the next free one")
    ap.add_argument("--room", default="", help="printed on the sticker, and the device's friendly name")
    ap.add_argument("--base", default="clive-round.yaml", help="the ESPHome file the device is built from")
    a = ap.parse_args()
    if not 1 <= a.gen <= 9: sys.exit("The generation is one digit, 1 to 9.")
    DEVICES.mkdir(exist_ok=True)
    reg = DEVICES / "registry.csv"
    rows = list(csv.DictReader(reg.open())) if reg.exists() else []
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
    payload = "clive://pair?id=%s&d=%s&k=%s" % (device_id, name, urllib.parse.quote(key, safe=""))
    draw_label(out / f"{device_id}-label.png", device_id, MODELS[a.model], payload, a.room)
    new = not reg.exists()
    with reg.open("a", newline="") as f:
        w = csv.DictWriter(f, ["id", "model", "gen", "number", "name", "key", "room"])
        if new: w.writeheader()
        w.writerow({"id": device_id, "model": a.model, "gen": a.gen, "number": number, "name": name, "key": key, "room": a.room})
    print(f"{device_id}  ->  {out}")
    print(f"  flash:  esphome run {out / (device_id + '.yaml')}")
    print(f"  label:  {out / (device_id + '-label.png')}  (14 x 60 mm)")


if __name__ == "__main__":
    main()
