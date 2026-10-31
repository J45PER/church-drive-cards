"""tools/publish-release-assets.sh, with a stand-in `gh` that keeps its releases in a JSON file."""
import json
import os
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent.parent / "tools" / "publish-release-assets.sh"

FAKE_GH = r'''#!/usr/bin/env python3
import json, sys
state_file = "%STATE%"
state = json.load(open(state_file))
args = sys.argv[1:]
def save(): json.dump(state, open(state_file, "w"))
assert args[0] == "release"
cmd, tag = args[1], args[2]
state.setdefault("calls", []).append(args[1:3])
if cmd == "view":
    if tag not in state["releases"]:
        save(); sys.exit(1)
    if "--json" in args:
        print("\n".join(state["releases"][tag]))
    save(); sys.exit(0)
if cmd == "create":
    if state.get("create_fails") and tag in state["releases"]:
        save(); sys.exit(1)
    if state.get("race"):   # someone else made it first
        state["releases"][tag] = state["releases"].get(tag, []); state["race"] = False; save(); sys.exit(1)
    state["releases"][tag] = []
    save(); sys.exit(0)
if cmd == "upload":
    files = [a for a in args[3:] if not a.startswith("--")]
    state["releases"][tag] += [f.split("/")[-1] for f in files]
    save(); sys.exit(0)
sys.exit(2)
'''


class PublishReleaseAssets(unittest.TestCase):
    def run_script(self, releases, marker, files, race=False):
        with tempfile.TemporaryDirectory() as tmp:
            state = Path(tmp) / "state.json"
            state.write_text(json.dumps({"releases": releases, "race": race}))
            gh = Path(tmp) / "gh"
            gh.write_text(FAKE_GH.replace("%STATE%", str(state)))
            gh.chmod(gh.stat().st_mode | stat.S_IEXEC)
            env = dict(os.environ, PATH=f"{tmp}:{os.environ['PATH']}", GITHUB_SHA="abc1234")
            done = subprocess.run([str(SCRIPT), "0.80.0", marker, *files], capture_output=True, text=True, env=env)
            return done, json.loads(state.read_text())

    def test_missing_release_is_created_and_files_added(self):
        done, state = self.run_script({}, "church-drive-todo-0.80.0-", ["x/church-drive-todo-0.80.0-b1.apk", "x/church-drive-todo-0.80.0-b1.aab"])
        self.assertEqual(done.returncode, 0, done.stderr)
        self.assertEqual(done.stdout.strip().splitlines()[-1], "new=true")
        self.assertEqual(sorted(state["releases"]["v0.80.0"]), ["church-drive-todo-0.80.0-b1.aab", "church-drive-todo-0.80.0-b1.apk"])

    def test_existing_release_gets_the_apps_files_beside_the_others(self):
        done, state = self.run_script({"v0.80.0": ["church-drive-cards.js", "church-drive-todo-0.80.0-b1.aab"]}, "church-drive-satellites-0.80.0-", ["church-drive-satellites-0.80.0-b1.aab"])
        self.assertEqual(done.stdout.strip().splitlines()[-1], "new=true")
        self.assertEqual(state["releases"]["v0.80.0"], ["church-drive-cards.js", "church-drive-todo-0.80.0-b1.aab", "church-drive-satellites-0.80.0-b1.aab"])

    def test_a_second_build_of_the_same_version_is_not_added(self):
        done, state = self.run_script({"v0.80.0": ["church-drive-todo-0.80.0-b1.aab"]}, "church-drive-todo-0.80.0-", ["church-drive-todo-0.80.0-b2.aab"])
        self.assertEqual(done.stdout.strip().splitlines()[-1], "new=false")
        self.assertEqual(state["releases"]["v0.80.0"], ["church-drive-todo-0.80.0-b1.aab"])

    def test_phone_and_watch_markers_do_not_clash(self):
        # the watch bundle is church-drive-manager-wear-<version>-..., the phone's church-drive-manager-<version>-...
        done, _ = self.run_script({"v0.80.0": ["church-drive-manager-wear-0.80.0-b1.aab"]}, "church-drive-manager-0.80.0-", ["church-drive-manager-0.80.0-b1.aab"])
        self.assertEqual(done.stdout.strip().splitlines()[-1], "new=true")

    def test_losing_the_race_to_create_is_fine(self):
        done, state = self.run_script({}, "church-drive-todo-0.80.0-", ["church-drive-todo-0.80.0-b1.aab"], race=True)
        self.assertEqual(done.returncode, 0, done.stderr)
        self.assertEqual(done.stdout.strip().splitlines()[-1], "new=true")
        self.assertEqual(state["releases"]["v0.80.0"], ["church-drive-todo-0.80.0-b1.aab"])


if __name__ == "__main__":
    unittest.main()
