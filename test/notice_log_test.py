import importlib.util, pathlib
spec = importlib.util.spec_from_file_location("notice_log", pathlib.Path(__file__).parent.parent / "custom_components/church_drive/notice_log.py")
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)

def test():
    log = m.NoticeLog()
    now = 10_000_000_000
    a = log.next_seq(now); b = log.next_seq(now)
    assert b > a, "seq always rises"
    log.record("p", a, "t", "one", {"tag": "x"})
    log.record("p", b, "t", "two", {"tag": "y"})
    assert [i["message"] for i in log.missed("p", 0, now)] == ["one", "two"]
    assert [i["message"] for i in log.missed("p", a, now)] == ["two"], "only after what was seen"
    assert log.missed("q", 0, now) == []
    c = log.next_seq(now)
    log.record("p", c, "t", "one again", {"tag": "x"})
    assert [i["message"] for i in log.missed("p", 0, now)] == ["two", "one again"], "same tag replaces"
    assert log.missed("p", 0, now + m.MAX_AGE_S * 1000 + 5000) == [], "old ones are dropped"
    for n in range(30):
        log.record("p", log.next_seq(now), "t", f"m{n}", {})
    assert len(log._items["p"]) == m.KEEP
    assert len(log.missed("p", 0, now)) == m.MAX_REPLAY
    assert log.latest("p") == log._items["p"][-1]["seq"]

test()
print("OK")
