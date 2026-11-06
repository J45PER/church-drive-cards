import importlib.util, pathlib
spec = importlib.util.spec_from_file_location("sports_log", pathlib.Path(__file__).parent.parent / "custom_components/church_drive/sports_log.py")
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)

def test_known_things_are_not_logged():
    r = m.LogoRequests()
    assert not r.record("Premier League", "football", "2026-11-06T10:00:00")
    assert not r.record("UEFA Champions League", "", "2026-11-06T10:00:00")
    assert not r.record("", "soccer", "2026-11-06T10:00:00")
    assert r.as_list() == []

def test_a_competition_without_a_logo_is_logged_and_counted():
    r = m.LogoRequests()
    assert r.record("Eredivisie", "football", "2026-11-06T10:00:00")
    assert r.record("eredivisie", "football", "2026-11-07T10:00:00")
    (item,) = r.as_list()
    assert item["competition"] == "Eredivisie" and item["count"] == 2
    assert item["first"] == "2026-11-06T10:00:00" and item["last"] == "2026-11-07T10:00:00"
    assert item["reason"] == "competition has no logo"

def test_another_sport_is_logged_as_using_the_generic_trophy():
    r = m.LogoRequests()
    assert r.record("Wimbledon", "tennis", "2026-11-06T10:00:00")
    assert r.record("", "darts", "2026-11-06T11:00:00")
    reasons = {i["sport"]: i["reason"] for i in r.as_list()}
    assert reasons["tennis"] == "competition has no logo and sport has no icon of its own"
    assert reasons["darts"] == "sport has no icon of its own"

def test_most_asked_about_comes_first_and_the_list_is_capped():
    r = m.LogoRequests()
    r.record("A League", "football", "t1"); r.record("B League", "football", "t2"); r.record("B League", "football", "t3")
    assert [i["competition"] for i in r.as_list()] == ["B League", "A League"]
    big = m.LogoRequests()
    for n in range(m.KEEP + 20): big.record(f"Cup {n}", "football", f"{n:06d}")
    assert len(big.items) == m.KEEP
