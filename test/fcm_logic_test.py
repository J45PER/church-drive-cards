import importlib.util, json, pathlib
spec = importlib.util.spec_from_file_location("fcm_logic", pathlib.Path(__file__).parent.parent / "custom_components/church_drive/fcm_logic.py")
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)

def test():
    event = {"title": "Doorbell", "message": "Someone's at the door", "data": {"tag": "d", "actions": [{"action": "CD_mute:60", "title": "Mute"}]}}
    msg = m.build_message("tok" * 20, event)["message"]
    assert msg["android"]["priority"] == "HIGH"
    assert json.loads(msg["data"]["event"]) == event, "the app gets the whole event back"
    assert all(isinstance(v, str) for v in msg["data"].values()), "FCM data values must be strings"
    assert m.endpoint("p").endswith("/projects/p/messages:send")
    assert m.token_is_dead(404, "")
    assert m.token_is_dead(400, '{"error":{"details":[{"errorCode":"UNREGISTERED"}]}}')
    assert not m.token_is_dead(500, "boom")
    assert not m.token_is_dead(400, "INVALID_ARGUMENT")
    assert m.valid_token_event("mobile_app_jamie_pixel", "x" * 100)
    assert not m.valid_token_event("notify.evil", "x" * 100)
    assert not m.valid_token_event("mobile_app_a", "short")
    assert not m.valid_token_event("mobile_app_a", "has space " * 5)
    assert not m.valid_token_event(None, None)
    assert m.clean_service("notify.mobile_app_x") == "mobile_app_x", "the app sends the full notify name"
    assert m.clean_service("mobile_app_x") == "mobile_app_x"
    assert m.clean_service(None) is None
    assert m.valid_token_event(m.clean_service("notify.mobile_app_x_cda"), "x" * 100)

test()
print("OK")
