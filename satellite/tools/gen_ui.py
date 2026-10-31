#!/usr/bin/env python3
"""Generates satellite/ui.yaml and satellite/clive_icons.h: Clive's round-screen UI for ESPHome + LVGL.

Layout is the apps' 280-unit watch layout scaled to the 800 px glass (docs/CLIVE-STYLE.md, satellite/design/flows.html),
so every position below is written in those units and converted by U(). Re-run after editing:

    python3 satellite/tools/gen_ui.py

Needs: PyYAML (ships with ESPHome) and the Material Design Icons codepoint list (tools/mdi_codepoints.json).
"""
import json
import string
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
OUT = HERE.parent
PX = 800 / 280.0


def U(n):
    return int(round(n * PX))


class Lambda(str):
    """A value template (`delay: !lambda ...`)."""


class Code(str):
    """The body of a `lambda:` action (plain block, no tag)."""


def _lambda_repr(dumper, data):
    return dumper.represent_scalar("!lambda", str(data), style="|")


yaml.add_representer(Lambda, _lambda_repr, Dumper=yaml.SafeDumper)
yaml.add_representer(Code, lambda d, v: d.represent_scalar("tag:yaml.org,2002:str", str(v), style="|"), Dumper=yaml.SafeDumper)

ICON_NAMES = {
    "weather-cloudy": "WEATHER_CLOUDY", "weather-partly-cloudy": "WEATHER_PARTLY_CLOUDY", "weather-sunny": "WEATHER_SUNNY",
    "weather-rainy": "WEATHER_RAINY", "weather-night": "WEATHER_NIGHT", "weather-snowy": "WEATHER_SNOWY",
    "weather-lightning": "WEATHER_LIGHTNING", "weather-fog": "WEATHER_FOG", "weather-windy": "WEATHER_WINDY",
    "account-multiple": "ACCOUNT_MULTIPLE", "lightning-bolt": "LIGHTNING_BOLT", "car-electric": "CAR_ELECTRIC",
    "shield-check": "SHIELD_CHECK", "shield-off": "SHIELD_OFF", "radiator": "RADIATOR", "air-filter": "AIR_FILTER",
    "volume-high": "VOLUME_HIGH", "brightness-6": "BRIGHTNESS_6", "microphone": "MICROPHONE", "microphone-off": "MICROPHONE_OFF",
    "account-voice": "ACCOUNT_VOICE", "message-reply-text": "MESSAGE_REPLY_TEXT", "spray-bottle": "SPRAY_BOTTLE", "wifi": "WIFI",
    "cog": "COG", "chevron-right": "CHEVRON_RIGHT", "chevron-down": "CHEVRON_DOWN", "lightbulb": "LIGHTBULB", "check": "CHECK",
    "close": "CLOSE", "soccer": "SOCCER", "music-note": "MUSIC_NOTE", "message-text": "MESSAGE_TEXT", "thermometer": "THERMOMETER",
    "home": "HOME", "qrcode": "QRCODE", "close-circle": "CLOSE_CIRCLE", "check-circle": "CHECK_CIRCLE",
}
cps = json.load(open(HERE / "mdi_codepoints.json"))


def utf8_escape(cp_hex):
    return "".join("\\x%02X" % b for b in chr(int(cp_hex, 16)).encode("utf-8"))


def yaml_glyph(cp_hex):
    return chr(int(cp_hex, 16))


# ------------------------------------------------------------------ fonts
TEXT_GLYPHS = "".join(c for c in string.printable if c not in "\t\n\r\x0b\x0c") + "°·£–’"
NUM_GLYPHS = "0123456789:°-. "
ICON_GLYPHS = [yaml_glyph(cps[k]) for k in ICON_NAMES]
MDI = "https://raw.githubusercontent.com/Templarian/MaterialDesign-Webfont/v7.4.47/fonts/materialdesignicons-webfont.ttf"


def fonts():
    out = []
    for px in (63, 126, 149, 183, 206):
        out.append({"file": "gfonts://Roboto@300", "id": f"f_light_{px}", "size": px, "glyphs": NUM_GLYPHS, "bpp": 4})
    for px in (26, 30, 34, 38, 41):
        out.append({"file": "gfonts://Roboto@400", "id": f"f_reg_{px}", "size": px, "glyphs": TEXT_GLYPHS, "bpp": 4})
    for px in (34, 40, 46, 51):
        out.append({"file": "gfonts://Roboto@500", "id": f"f_med_{px}", "size": px, "glyphs": TEXT_GLYPHS, "bpp": 4})
    for px in (46, 103):
        out.append({"file": "gfonts://Roboto@700", "id": f"f_bold_{px}", "size": px, "glyphs": TEXT_GLYPHS, "bpp": 4})
    for px in (40, 46, 51, 57, 131):
        out.append({"file": MDI, "id": f"f_mdi_{px}", "size": px, "glyphs": ICON_GLYPHS, "bpp": 4})
    return out


# ------------------------------------------------------------------ the mark's frames
POSES = {"idle": (24, 4000), "listen": (12, 1400), "think": (12, 1200), "speak": (10, 700)}


def images():
    out = []
    for pose, (n, _) in POSES.items():
        for i in range(n):
            out.append({"platform": "file", "file": f"assets/mark/{pose}_{i:02d}.png", "id": f"mk_{pose}_{i:02d}", "type": "RGB565", "transparency": "alpha_channel"})
    return out


# ------------------------------------------------------------------ widget helpers
GROUND = 0x000000
SOFT, DIM, FAINT = 0xE8EAFF, 0x9096B0, 0x5A6078


def base(id_=None, x=0, y=0, w=800, h=800, **kw):
    d = {"x": x, "y": y, "width": w, "height": h, "bg_opa": "TRANSP", "border_width": 0, "pad_all": 0, "radius": 0,
         "scrollable": False, "clickable": False}
    if id_:
        d["id"] = id_
    d.update(kw)
    return d


def label(id_, txt, font, color=0xFFFFFF, cx=400, cy=400, style=None, **kw):
    """A label centred on (cx, cy) px."""
    d = {"text": txt, "text_font": font, "text_color": color, "align": "CENTER", "x": cx - 400, "y": cy - 400}
    if id_:
        d["id"] = id_
    if style:
        d["styles"] = style
        d.pop("text_color")
    d.update(kw)
    return {"label": d}


def box(id_, widgets, cx=400, cy=400, flow="row", gap=0, **kw):
    """A flex box sized to its content, centred on (cx, cy) px."""
    d = {"align": "CENTER", "x": cx - 400, "y": cy - 400, "width": "SIZE_CONTENT", "height": "SIZE_CONTENT", "bg_opa": "TRANSP", "border_width": 0,
         "pad_all": 0, "scrollable": False, "clickable": False,
         "layout": {"type": "flex", "flex_flow": flow, "flex_align_main": "CENTER", "flex_align_cross": "CENTER", "flex_align_track": "CENTER"},
         "widgets": widgets}
    if gap:
        d["layout"]["pad_column" if flow == "row" else "pad_row"] = gap
    if id_:
        d["id"] = id_
    d.update(kw)
    return {"obj": d}


def icon_label(id_, icon, size, color=0xFFFFFF, **kw):
    d = {"text": chr(int(cps[icon], 16)), "text_font": f"f_mdi_{size}", "text_color": color}
    if id_:
        d["id"] = id_
    d.update(kw)
    return {"label": d}


def pill_button(id_, cx, cy, w, h, widgets, on_click=None, fill=False, **kw):
    d = {"id": id_, "align": "CENTER", "x": cx - 400, "y": cy - 400, "width": w, "height": h, "radius": 1000,
         "bg_color": 0x080914, "bg_opa": "35%", "border_width": max(2, U(1)), "border_color": 0xDCE0FF, "border_opa": "50%",
         "pad_all": 0, "shadow_width": 0, "scrollable": False, "widgets": widgets}
    if fill:
        d.update({"bg_color": 0xFFFFFF, "bg_opa": "20%", "border_color": 0xFFFFFF, "border_opa": "75%"})
    if on_click:
        d["on_click"] = on_click
    d.update(kw)
    return {"button": d}


def centred_text(txt, font, color=0xEEF0FF, **kw):
    d = {"text": txt, "text_font": font, "text_color": color, "align": "CENTER"}
    d.update(kw)
    return {"label": d}


# ------------------------------------------------------------------ layers
def glow_layer():
    blobs = []
    for i in range(3):
        blobs.append({"obj": base(f"blob{i}", x=0, y=0, w=992, h=992, radius=496, bg_opa="COVER", bg_grad="g_blob", border_width=0)})
    vig = {"obj": base("vignette", bg_opa="COVER", bg_grad="g_vignette")}
    return {"obj": base("glow", hidden=True, widgets=blobs + [vig])}


def date_line(id_prefix, y, parts):
    return None


def idle_glance():
    row = box("glance_dateline", [
        {"label": {"id": "glance_date", "text": "Sat 31 Oct", "text_font": "f_reg_30", "styles": "st_dim"}},
        {"label": {"text": "  ·  ", "text_font": "f_reg_30", "styles": "st_faint"}},
        {"label": {"id": "glance_out", "text": "12° out", "text_font": "f_reg_30", "styles": "st_soft"}},
        {"label": {"text": "  ·  ", "text_font": "f_reg_30", "styles": "st_faint"}},
        {"label": {"id": "glance_in", "text": "19° in", "text_font": "f_reg_30", "styles": "st_soft"}},
    ], cy=U(102))
    glances = []
    for i, x in enumerate((90, 140, 190)):
        glances.append(box(None, [
            icon_label(f"g_icon{i}", "account-multiple", 51),
            {"label": {"id": f"g_value{i}", "text": "2 of 4", "text_font": "f_med_40", "text_color": 0xFFFFFF}},
            {"label": {"id": f"g_label{i}", "text": "home", "text_font": "f_reg_26", "styles": "st_dim"}},
        ], cx=U(x), cy=U(168), flow="column", gap=6))
    return {"obj": base("idle_glance", widgets=[
        {"obj": base(None, x=400 - 37, y=U(9) - 4, w=74, h=9, radius=100, bg_color=0x2E3246, bg_opa="50%")},
        label("time_glance", "14:35", "f_light_126", cy=U(68)), row] + glances)}


def idle_clock():
    return {"obj": base("idle_clock", widgets=[
        {"obj": base(None, x=400 - 37, y=U(9) - 4, w=74, h=9, radius=100, bg_color=0x2E3246, bg_opa="50%")},
        box(None, [icon_label("clock_icon", "weather-cloudy", 46), {"label": {"id": "clock_temp", "text": "12°", "text_font": "f_reg_41", "styles": "st_soft"}}], cy=U(52), gap=14),
        label("time_clock", "14:35", "f_light_206", cy=U(104)),
        {"label": {"id": "clock_date", "text": "Sat 31 Oct", "text_font": "f_reg_34", "styles": "st_dim", "align": "CENTER", "x": 0, "y": U(152) - 400}}])}


def idle_weather():
    return {"obj": base("idle_weather", widgets=[
        {"obj": base(None, x=400 - 37, y=U(9) - 4, w=74, h=9, radius=100, bg_color=0x2E3246, bg_opa="50%")},
        box(None, [icon_label("weather_icon", "weather-cloudy", 131), {"label": {"id": "weather_temp", "text": "12°", "text_font": "f_light_183", "text_color": 0xFFFFFF}}], cy=U(74), gap=20),
        {"label": {"id": "weather_cond", "text": "Cloudy", "text_font": "f_med_40", "styles": "st_soft", "align": "CENTER", "x": 0, "y": U(118) - 400}},
        {"label": {"id": "weather_hl", "text": "H 14° · L 8°", "text_font": "f_reg_30", "styles": "st_dim", "align": "CENTER", "x": 0, "y": U(136) - 400}},
        box(None, [{"label": {"id": "time_weather", "text": "14:35", "text_font": "f_light_63", "text_color": 0xFFFFFF}},
                   {"label": {"id": "weather_small_date", "text": " · Sat 31 Oct", "text_font": "f_reg_34", "styles": "st_dim"}}], cy=U(174))])}


def arc(id_, r_units, width_px, value_max, track_opa, color_opa):
    d = U(2 * r_units)
    return {"arc": {"id": id_, "align": "CENTER", "x": 0, "y": 0, "width": d, "height": d, "min_value": 0, "max_value": value_max, "value": 0,
                    "rotation": 270, "start_angle": 0, "end_angle": 360, "adjustable": False, "clickable": False,
                    "bg_opa": "TRANSP", "border_width": 0, "pad_all": 0,
                    "main": {"arc_color": 0xFFFFFF, "arc_opa": track_opa, "arc_width": width_px},
                    "indicator": {"arc_color": 0xFFFFFF, "arc_opa": color_opa, "arc_width": width_px, "arc_rounded": True},
                    "knob": {"bg_opa": "TRANSP", "pad_all": 0}}}


def idle_ring():
    return {"obj": base("idle_ring", widgets=[
        arc("ring_sec", 134, 6, 60, "12%", "85%"), arc("ring_hour", 124, 4, 720, "7%", "50%"),
        label("time_ring", "14:35", "f_light_149", cy=U(98)),
        {"label": {"id": "ring_date", "text": "Sat 31 Oct · 12°", "text_font": "f_reg_30", "styles": "st_dim", "align": "CENTER", "x": 0, "y": U(136) - 400}}])}


def card_layer():
    yes = pill_button("card_yes", U(92), U(181), U(88), U(30), [centred_text("Yes, charge", "f_med_40", 0xFFFFFF)], fill=True,
                      on_click=[{"lambda": Code("clive::card_answer(true);")}, {"script.execute": {"id": "ui_hook_answer", "accepted": True}}], hidden=True)
    no = pill_button("card_no", U(180), U(181), U(72), U(30), [centred_text("No", "f_med_40", 0xEEF0FF)],
                     on_click=[{"lambda": Code("clive::card_answer(false);")}, {"script.execute": {"id": "ui_hook_answer", "accepted": False}}], hidden=True)
    stack = box("card_stack", [
        {"label": {"id": "card_big", "text": "", "text_font": "f_bold_103", "text_color": 0xFFFFFF, "text_align": "CENTER"}},
        {"label": {"id": "card_title", "text": "", "text_font": "f_med_51", "text_color": 0xFFFFFF, "text_align": "CENTER", "long_mode": "DOT", "width": 640}},
        {"label": {"id": "card_sub", "text": "", "text_font": "f_reg_41", "styles": "st_soft", "text_align": "CENTER", "long_mode": "DOT", "width": 640}},
        {"label": {"id": "card_row0", "text": "", "text_font": "f_reg_38", "styles": "st_soft", "text_align": "CENTER", "long_mode": "DOT", "width": 640}},
        {"label": {"id": "card_row1", "text": "", "text_font": "f_reg_38", "styles": "st_soft", "text_align": "CENTER", "long_mode": "DOT", "width": 640}},
        {"label": {"id": "card_row2", "text": "", "text_font": "f_reg_38", "styles": "st_soft", "text_align": "CENTER", "long_mode": "DOT", "width": 640}},
        {"bar": {"id": "card_bar", "width": 400, "height": 18, "min_value": 0, "max_value": 100, "value": 60, "radius": 100,
                 "bg_color": 0xFFFFFF, "bg_opa": "14%", "border_width": 0, "pad_all": 0,
                 "indicator": {"bg_color": 0xFFFFFF, "bg_opa": "92%", "radius": 100}, "hidden": True}},
    ], cy=U(146), flow="column", gap=8, width=680, height="SIZE_CONTENT")
    # Below the icon and above the dock: a stack from y 252 px down (centred horizontally).
    stack["obj"].update({"align": "TOP_MID", "x": 0, "y": 238, "layout": dict(stack["obj"]["layout"], flex_align_main="START")})
    return {"obj": base("card", hidden=True, widgets=[
        {"label": {"id": "card_icon", "text": "", "text_font": "f_mdi_131", "text_color": 0xFFFFFF, "align": "CENTER", "x": 0, "y": U(60) - 400, "hidden": True}},
        stack, yes, no])}


def dock_layer():
    top = U(208)
    d = {"obj": base("dock", x=0, y=top, w=800, h=800 - top, bg_color=0x000000, bg_opa="42%", border_width=2, border_color=0xFFFFFF, border_opa="8%", border_side="TOP",
                     clickable=True, on_click=[{"lambda": Code("clive::on_dock_tap();")}, {"script.execute": "ui_hook_talk"}])}
    marks = []
    for pose, (n, ms) in POSES.items():
        marks.append({"animimg": {"id": f"mark_{pose}", "x": 400 - 56, "y": U(245) - 56, "width": 112, "height": 112,
                                  "src": [f"mk_{pose}_{i:02d}" for i in range(n)], "duration": f"{ms}ms", "auto_start": True, "repeat_count": "forever",
                                  "clickable": False, "hidden": pose != "idle"}})
    return [d] + marks


def tile(id_, cx, cy, icon, label_txt, key, checked):
    d = U(38)
    btn = {"button": {"id": id_, "align": "CENTER", "x": cx - 400, "y": cy - 400 - 0, "width": d, "height": d, "radius": 1000, "checkable": True,
                      "bg_color": 0x080914, "bg_opa": "50%", "border_width": max(2, U(1)), "border_color": 0xDCE0FF, "border_opa": "28%", "shadow_width": 0, "pad_all": 0,
                      "checked": {"bg_color": 0xFFFFFF, "bg_opa": "20%", "border_color": 0xFFFFFF, "border_opa": "70%"},
                      "widgets": [{"label": {"text": chr(int(cps[icon], 16)), "text_font": "f_mdi_57", "text_color": 0xFFFFFF, "align": "CENTER", "text_opa": "55%"}}],
                      "on_click": [{"script.execute": {"id": "ui_hook_tile", "key": key, "checked": Lambda(f"return lv_obj_has_state(id({id_}), LV_STATE_CHECKED);")}}]}}
    if checked:
        btn["button"]["state"] = {"checked": True}
    cap = {"label": {"id": f"{id_}_cap", "text": label_txt, "text_font": "f_reg_26", "text_color": DIM, "align": "CENTER", "x": cx - 400, "y": cy - 400 + U(26)}}
    return [btn, cap]


def shade_layer():
    kids = [{"obj": base(None, x=400 - 40, y=U(12) - 5, w=80, h=11, radius=100, bg_color=0xFFFFFF, bg_opa="35%")}]
    for (y, icon, ident) in ((44, "volume-high", "qs_volume"), (72, "brightness-6", "qs_bright")):
        kids.append(icon_label(None, icon, 46, align="CENTER", x=U(58) - 400 + 0, y=U(y) - 400))
        kids.append({"slider": {"id": ident, "align": "CENTER", "x": U(150) - 400, "y": U(y) - 400, "width": U(140), "height": 18, "min_value": 0, "max_value": 100, "value": 60,
                                "bg_color": 0xFFFFFF, "bg_opa": "14%", "radius": 100,
                                "indicator": {"bg_color": 0xFFFFFF, "bg_opa": "92%"}, "knob": {"bg_opa": "TRANSP", "pad_all": 0, "width": 4, "height": 4},
                                "on_value": [{"script.execute": {"id": "ui_hook_slider", "which": 0 if ident == 'qs_volume' else 1, "value": Lambda("return x;")}}]}})
    for i, (icon, name, cap) in enumerate((("microphone", "t_mic", "Mic"), ("account-voice", "t_voice", "Spoken"), ("message-reply-text", "t_follow", "Follow-up"))):
        kids += tile(name, U(86 + 54 * i), U(122), icon, cap, i, True)
    for i, (icon, name, cap, on) in enumerate((("weather-night", "t_night", "Night", False), ("spray-bottle", "t_clean", "Clean", False), ("wifi", "t_wifi", "Home Wi-Fi", True))):
        kids += tile(name, U(86 + 54 * i), U(176), icon, cap, 3 + i, on)
    kids.append(pill_button("btn_settings", 400, U(244), U(96), U(26), [
        box(None, [icon_label(None, "cog", 40), {"label": {"text": " Settings", "text_font": "f_reg_34", "text_color": 0xEEF0FF}}])], on_click=[{"lambda": Code("clive::open_menu();")}]))
    kids.append({"obj": base(None, x=400 - 37, y=U(270) - 4, w=74, h=9, radius=100, bg_color=0xFFFFFF, bg_opa="40%")})
    return {"obj": base("shade", y=-800, bg_color=0x0E101A, bg_opa="COVER", hidden=True, clickable=True, widgets=kids,
                        on_press=[{"lambda": Code("clive::touch_down();")}], on_release=[{"lambda": Code("clive::touch_up(true);")}])}


def menu_layer():
    items = ("Name and room", "Voice and volume", "Idle screen", "Wi-Fi and network", "Pair or reset")
    kids = [{"label": {"text": "Settings", "text_font": "f_bold_46", "text_color": 0xFFFFFF, "align": "CENTER", "x": 0, "y": U(24) - 400}}]
    for i, t in enumerate(items):
        kids.append(pill_button(f"menu_{i}", 400, U(50 + 34 * i) + U(15), U(192), U(30), [
            {"label": {"text": t, "text_font": "f_reg_34", "text_color": 0xEEF0FF, "align": "LEFT_MID", "x": 36}},
            icon_label(None, "chevron-right", 40, align="RIGHT_MID", x=-28)], on_click=[{"lambda": Code(f"clive::on_menu({i});")}]))
    kids.append(pill_button("menu_back", 400, U(250), U(26), U(26), [icon_label(None, "chevron-down", 46, align="CENTER")], on_click=[{"lambda": Code("clive::close_menu();")}]))
    return {"obj": base("menu", bg_color=0x000000, bg_opa="COVER", hidden=True, clickable=True, widgets=kids)}


def pair_layer():
    kids = [
        {"label": {"text": "Set up this satellite", "text_font": "f_bold_46", "text_color": 0xFFFFFF, "align": "CENTER", "x": 0, "y": U(20) - 400}},
        {"obj": base("qr_tile", x=400 - U(54), y=U(44), w=U(108), h=U(108), radius=U(8), bg_color=0xF4F5FA, bg_opa="COVER", widgets=[
            {"qrcode": {"id": "pair_qr", "size": U(100), "light_color": 0xF4F5FA, "dark_color": 0x101218, "text": "clive://pair", "align": "CENTER"}}])},
        {"label": {"text": "Scan with the Church Drive Satellites app", "text_font": "f_med_34", "text_color": 0xFFFFFF, "align": "CENTER", "x": 0, "y": U(168) - 400}},
        {"label": {"id": "pair_code", "text": "Kitchen · code 482 913", "text_font": "f_reg_30", "styles": "st_dim", "align": "CENTER", "x": 0, "y": U(192) - 400}},
        {"label": {"text": "The code runs out in 10 minutes", "text_font": "f_reg_26", "styles": "st_faint", "align": "CENTER", "x": 0, "y": U(212) - 400}},
        pill_button("pair_close", 400, U(250), U(26), U(26), [icon_label(None, "close", 46, align="CENTER")], on_click=[{"lambda": Code("clive::close_menu();")}]),
    ]
    return {"obj": base("pair", bg_color=0x000000, bg_opa="COVER", hidden=True, clickable=True, widgets=kids)}


# ------------------------------------------------------------------ the lvgl block
IDS = [
    "glow", "blob0", "blob1", "blob2", "vignette", "idle_glance", "idle_clock", "idle_weather", "idle_ring", "card", "dock", "shade", "menu", "pair",
    "mark_idle", "mark_listen", "mark_think", "mark_speak",
    "time_glance", "time_clock", "time_weather", "time_ring", "glance_date", "glance_out", "glance_in", "clock_date", "clock_temp", "clock_icon",
    "weather_icon", "weather_temp", "weather_cond", "weather_hl", "weather_small_date", "ring_date", "ring_sec", "ring_hour",
    "g_icon0", "g_icon1", "g_icon2", "g_value0", "g_value1", "g_value2", "g_label0", "g_label1", "g_label2",
    "card_icon", "card_big", "card_title", "card_sub", "card_row0", "card_row1", "card_row2", "card_bar", "card_yes", "card_no", "card_stack",
    "qs_volume", "qs_bright", "pair_qr", "pair_code", "t_mic", "t_voice", "t_follow", "t_night", "t_clean",
]


def bind_lambda():
    L = ["// Fills clive::w with the widgets declared in ui.yaml (generated by tools/gen_ui.py)."]
    for i in IDS:
        pass
    m = {
        "glow": "w.glow", "blob0": "w.blob[0]", "blob1": "w.blob[1]", "blob2": "w.blob[2]", "vignette": "w.vignette",
        "idle_glance": "w.idle[0]", "idle_clock": "w.idle[1]", "idle_weather": "w.idle[2]", "idle_ring": "w.idle[3]",
        "card": "w.card", "dock": "w.dock", "shade": "w.shade", "menu": "w.menu", "pair": "w.pair",
        "mark_idle": "w.mark[0]", "mark_listen": "w.mark[1]", "mark_think": "w.mark[2]", "mark_speak": "w.mark[3]",
        "time_glance": "w.time_label[0]", "time_clock": "w.time_label[1]", "time_weather": "w.time_label[2]", "time_ring": "w.time_label[3]",
        "glance_date": "w.glance_date", "glance_out": "w.glance_out", "glance_in": "w.glance_in", "clock_date": "w.date_label[1]",
        "clock_temp": "w.clock_temp", "clock_icon": "w.clock_icon", "weather_icon": "w.weather_icon", "weather_temp": "w.weather_temp",
        "weather_cond": "w.weather_cond", "weather_hl": "w.weather_hl", "weather_small_date": "w.weather_small_date", "ring_date": "w.ring_date",
        "ring_sec": "w.ring_sec", "ring_hour": "w.ring_hour",
        "g_icon0": "w.g_icon[0]", "g_icon1": "w.g_icon[1]", "g_icon2": "w.g_icon[2]", "g_value0": "w.g_value[0]", "g_value1": "w.g_value[1]", "g_value2": "w.g_value[2]",
        "g_label0": "w.g_label[0]", "g_label1": "w.g_label[1]", "g_label2": "w.g_label[2]",
        "card_icon": "w.card_icon", "card_big": "w.card_big", "card_title": "w.card_title", "card_sub": "w.card_sub", "card_row0": "w.card_row[0]",
        "card_row1": "w.card_row[1]", "card_row2": "w.card_row[2]", "card_bar": "w.card_bar", "card_yes": "w.card_yes", "card_no": "w.card_no",
        "card_stack": "w.card_stack", "qs_volume": "w.qs_volume", "qs_bright": "w.qs_bright", "pair_qr": "w.pair_qr", "pair_code": "w.pair_code",
        "t_mic": "w.tile[0]", "t_voice": "w.tile[1]", "t_follow": "w.tile[2]", "t_night": "w.tile[3]", "t_clean": "w.tile[4]",
    }
    for k, v in m.items():
        L.append(f"clive::{v} = id({k});")
    L.append("clive::st_dim = id(st_dim); clive::st_faint = id(st_faint); clive::st_soft = id(st_soft);")
    L.append("ESP_LOGI(\"clive\", \"widgets bound, glow=%p dock=%p\", (void *) clive::w.glow, (void *) clive::w.dock);")
    L.append("clive::init_ui();")
    return "\n".join(L)


def lvgl_block():
    return {
        "bg_color": GROUND, "buffer_size": "100%", "log_level": "WARN",
        "style_definitions": [{"id": "st_dim", "text_color": DIM}, {"id": "st_faint", "text_color": FAINT}, {"id": "st_soft", "text_color": SOFT}],
        "gradients": [
            {"id": "g_blob", "direction": "RADIAL", "radial": {"center_x": "50%", "center_y": "50%", "to_x": "100%", "to_y": "50%"},
             "stops": [{"color": 0x5C6BC0, "position": "0%", "opa": "70%"}, {"color": 0x5C6BC0, "position": "100%", "opa": "0%"}]},
            {"id": "g_vignette", "direction": "RADIAL", "radial": {"center_x": "50%", "center_y": "50%", "to_x": "100%", "to_y": "50%"},
             "stops": [{"color": 0x05060E, "position": "0%", "opa": "0%"}, {"color": 0x05060E, "position": "80%", "opa": "0%"}, {"color": 0x05060E, "position": "100%", "opa": "70%"}]},
        ],
        "pages": [{"id": "pg_main", "bg_color": GROUND, "widgets": [
            {"obj": base("root", bg_color=GROUND, bg_opa="COVER", clickable=True,
                         on_press=[{"lambda": Code("clive::touch_down();")}], on_release=[{"lambda": Code("clive::touch_up(false);")}],
                         widgets=[glow_layer(), idle_glance(), idle_clock(), idle_weather(), idle_ring(), card_layer()] + dock_layer() + [shade_layer(), menu_layer(), pair_layer()])}]}],
    }


GLANCE_OPTIONS = ["Who's home", "Electricity", "Car charge", "Alarm", "Heating", "Air quality", "Forecast"]


def scripts_etc():
    info_params = {k: "string" for k in ("outside", "cond", "high", "low", "inside", "g_home", "g_energy", "g_car", "g_alarm", "g_heat", "g_air")}
    info_lambda = "\n".join([f"clive::info.{k} = {k};" for k in info_params]) + "\nclive::update_info_labels();"
    return {
        "script": [
            {"id": "ui_bind", "then": [{"lambda": Code(bind_lambda())}]},
            {"id": "ui_update_info", "parameters": info_params, "then": [{"lambda": Code(info_lambda)}]},
            {"id": "ui_set_state", "parameters": {"state": "int"}, "then": [{"lambda": Code("clive::set_state(state);")}]},
            {"id": "ui_card_timer", "mode": "restart", "parameters": {"seconds": "int"},
             "then": [{"delay": Lambda("return seconds * 1000;")}, {"lambda": Code("clive::card_hide();")}]},
            {"id": "ui_show_card", "mode": "restart",
             "parameters": {"icon": "string", "big": "string", "title": "string", "sub": "string", "r1": "string", "r2": "string", "r3": "string",
                            "percent": "int", "ask": "bool", "pal": "string", "seconds": "int"},
             "then": [{"lambda": Code("clive::card_show_args(icon, big, title, sub, r1, r2, r3, percent, ask, pal);")},
                      {"script.execute": {"id": "ui_card_timer", "seconds": Lambda("return ask ? 300 : seconds;")}}]},
            {"id": "ui_hide_card", "then": [{"script.stop": "ui_card_timer"}, {"lambda": Code("clive::card_hide();")}]},
        ],
        "interval": [
            {"interval": "60ms", "then": [{"lambda": Code("static uint32_t last = 0; uint32_t now = esphome::millis();\nfloat dt = last ? (now - last) / 1000.0f : 0.06f; last = now;\nclive::tick(dt > 0.2f ? 0.2f : dt);")}]},
            {"interval": "1s", "then": [{"lambda": Code(
                "auto t = id(clock_time).now(); if (!t.is_valid()) return;\n"
                "static const char *DAYS[] = {\"Sun\", \"Mon\", \"Tue\", \"Wed\", \"Thu\", \"Fri\", \"Sat\"};\n"
                "static const char *MONTHS[] = {\"Jan\", \"Feb\", \"Mar\", \"Apr\", \"May\", \"Jun\", \"Jul\", \"Aug\", \"Sep\", \"Oct\", \"Nov\", \"Dec\"};\n"
                "char d[24]; snprintf(d, sizeof d, \"%s %d %s\", DAYS[(t.day_of_week + 6) % 7], t.day_of_month, MONTHS[t.month - 1]);\n"
                "clive::update_time(t.hour, t.minute, t.second, d, d);")}]},
        ],
        "select": [
            {"platform": "template", "name": "Design", "id": "sel_design", "options": ["Glance", "Clock", "Weather", "Ring"], "initial_option": "Glance",
             "optimistic": True, "restore_value": True, "entity_category": "config", "on_value": [{"lambda": Code("clive::set_design(i);")}]},
        ] + [
            {"platform": "template", "name": f"Glance {n + 1}", "id": f"sel_glance{n}", "options": GLANCE_OPTIONS, "initial_option": GLANCE_OPTIONS[n],
             "optimistic": True, "restore_value": True, "entity_category": "config", "on_value": [{"lambda": Code(f"clive::set_glance({n}, i);")}]}
            for n in range(3)],
    }


def build():
    cfg = {
        # The main file runs `script.execute: ui_bind` first thing in its own on_boot (a package's on_boot would be dropped).
        "esphome": {"includes": ["clive_ui.h", "clive_icons.h"]},
        "font": fonts(),
        "image": images(),
        "lvgl": lvgl_block(),
    }
    cfg.update(scripts_etc())
    return cfg


def icons_header():
    L = ["// Generated by tools/gen_ui.py: Material Design Icons (v7.4.47) as UTF-8 strings for the icon font.", "#pragma once", "#include <string>"]
    for k, v in ICON_NAMES.items():
        L.append(f'#define ICON_{v} "{utf8_escape(cps[k])}"')
    L.append("inline const char *icon_by_name(const std::string &n) {")
    for k, v in ICON_NAMES.items():
        L.append(f'  if (n == "{k}") return ICON_{v};')
    L.append("  return ICON_MESSAGE_TEXT;")
    L.append("}")
    return "\n".join(L) + "\n"


if __name__ == "__main__":
    cfg = build()
    header = "# GENERATED by satellite/tools/gen_ui.py. Edit that, not this file.\n"
    (OUT / "ui.yaml").write_text(header + yaml.dump(cfg, Dumper=yaml.SafeDumper, sort_keys=False, width=200, allow_unicode=True))
    (OUT / "clive_icons.h").write_text(icons_header())
    print("wrote ui.yaml and clive_icons.h")
