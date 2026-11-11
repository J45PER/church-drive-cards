import importlib.util
import pathlib
import unittest

path = pathlib.Path(__file__).parent.parent / "custom_components" / "church_drive" / "mirror_logic.py"
spec = importlib.util.spec_from_file_location("mirror_logic", path)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class MirrorLogic(unittest.TestCase):
    def test_values_are_firestore_typed(self):
        self.assertEqual(m.to_value(None), {"nullValue": None})
        self.assertEqual(m.to_value(True), {"booleanValue": True})  # bool before int
        self.assertEqual(m.to_value(3), {"integerValue": "3"})
        self.assertEqual(m.to_value(1.5), {"doubleValue": 1.5})
        self.assertEqual(m.to_value("x"), {"stringValue": "x"})
        self.assertEqual(m.to_value(["a", 1]), {"arrayValue": {"values": [{"stringValue": "a"}, {"integerValue": "1"}]}})
        self.assertEqual(m.to_value({"k": {"j": 2}}), {"mapValue": {"fields": {"k": {"mapValue": {"fields": {"j": {"integerValue": "2"}}}}}}})
        self.assertEqual(m.document_body({"a": 1}), {"fields": {"a": {"integerValue": "1"}}})

    def test_the_documents_kept(self):
        config = {"revision": 4, "palettes": {"ok": {"colours": ["#1"]}}, "kind_palettes": {"light": "lights"}}
        versions = {"palette": [{"revision": 3, "overrides": {}}], "icons": [{"revision": 1, "data": {"x": 1}}, {"no_revision": True}]}
        docs = m.build_documents(config, {"ok": {}}, {"c1": {"palette": "ok"}}, versions, "0.140.0", 100.0)
        ids = [(c, i) for c, i, _ in docs]
        self.assertEqual(ids, [("palettes", "current"), ("cards", "picks"), ("history", "icons-1"), ("history", "palette-3"), ("meta", "revision")])
        self.assertEqual(docs[0][2]["revision"], 4)
        self.assertEqual(docs[2][2]["kind"], "icons")
        self.assertEqual(docs[-1][2]["version"], "0.140.0")

    def test_a_quiet_house_sends_nothing(self):
        a = {"colours": 4, "version": "1", "updated": 1.0}
        b = {"colours": 4, "version": "1", "updated": 99.0}
        self.assertEqual(m.fingerprint(a), m.fingerprint(b))
        self.assertNotEqual(m.fingerprint(a), m.fingerprint({**a, "colours": 5}))

    def test_urls(self):
        self.assertEqual(m.doc_url("p", "palettes", "current"), "https://firestore.googleapis.com/v1/projects/p/databases/(default)/documents/palettes/current")
        self.assertEqual(m.KEY_FILE, "church_drive/firebase-writer.json")


if __name__ == "__main__":
    unittest.main()
