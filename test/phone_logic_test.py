import importlib.util, pathlib
spec = importlib.util.spec_from_file_location("phone_logic", pathlib.Path(__file__).parent.parent / "custom_components/church_drive/phone_logic.py")
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)

CD = "com.churchdrive.manager"

def e(i, name, user="u1", app=CD):
    return {"entry_id": i, "app_id": app, "user_id": user, "device_name": name}

def test():
    three = [e("01M5867", "XITOL-J45PER-P10PXL-CDA"), e("01M5FW8", "XITOL-J45PER-P10PXL-CDA"), e("01M69P6", "XITOL-J45PER-P10PXL-CDA")]
    assert m.leftover_entries(three) == ["01M5867", "01M5FW8"], "the newest is kept"
    assert m.leftover_entries(three[:1]) == []
    # two people with the same phone name are not each other's duplicates
    assert m.leftover_entries([e("1", "Pixel 9-CDA", "a"), e("2", "Pixel 9-CDA", "b")]) == []
    # the Home Assistant app and entries with no person or name are never touched
    assert m.leftover_entries([e("1", "Pixel", app="io.homeassistant.companion.android"), e("2", "Pixel", app="io.homeassistant.companion.android")]) == []
    assert m.leftover_entries([e("1", "x", None), e("2", "x", None), e("3", ""), e("4", " ")]) == []
    assert m.leftover_entries([e("1", "Pixel 9-CDA"), e("2", " pixel  9-cda ")]) == ["1"]

test()
print("OK")
