from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch
from game_launch import configure_game, launch_game


class GameLaunchTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.game = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_new_game_gets_russian_language_and_scale_two(self):
        configure_game(self.game)
        self.assertEqual((self.game / 'options.txt').read_text(), 'guiScale:2\nlang:ru_ru\n')

    def test_other_preferences_survive_and_repeat_does_not_rewrite(self):
        path = self.game / 'options.txt'
        path.write_bytes(b'version:3465\r\nguiScale:4\r\nfov:0.75\r\nlang:en_us\r\n'
                         b'guiScale:0\r\nkey_key.chat:key.keyboard.t\r\nsoundCategory_music:0.2')
        configure_game(self.game)
        expected = (b'version:3465\r\nguiScale:2\r\nfov:0.75\r\nlang:ru_ru\r\n'
                    b'key_key.chat:key.keyboard.t\r\nsoundCategory_music:0.2')
        self.assertEqual(path.read_bytes(), expected)
        timestamp = path.stat().st_mtime_ns
        configure_game(self.game)
        self.assertEqual(path.stat().st_mtime_ns, timestamp)

    def test_failed_write_keeps_original_options(self):
        path = self.game / 'options.txt'
        original = b'lang:en_us\nfov:0.6\n'
        path.write_bytes(original)
        with patch.object(Path, 'replace', side_effect=OSError('disk error')):
            with self.assertRaises(OSError):
                configure_game(self.game)
        self.assertEqual(path.read_bytes(), original)
        self.assertFalse(list(self.game.glob('.options-*')))

    def test_immediate_game_failure_is_reported_with_log_preserved(self):
        with self.assertRaisesRegex(RuntimeError, '7'):
            launch_game([sys.executable, '-c', "print('startup failed'); raise SystemExit(7)"],
                        self.game, startup_timeout=10)
        self.assertIn('startup failed', (self.game / 'game-output.log').read_text())

    def test_game_keeps_running_after_launcher_process_exits(self):
        trigger = self.game / 'parent-exited'
        result = self.game / 'child-result'
        child = (
            'import sys,time\nfrom pathlib import Path\n'
            'trigger=Path(sys.argv[1]); result=Path(sys.argv[2])\n'
            'deadline=time.monotonic()+10\n'
            'while not trigger.exists() and time.monotonic()<deadline: time.sleep(0.02)\n'
            "print('child still running after parent exit', flush=True)\n"
            'sys.stdout.close(); sys.stderr.close()\n'
            "if trigger.exists(): result.write_text('survived', encoding='utf-8')\n"
        )
        parent = (
            'import sys\nfrom game_launch import launch_game\n'
            'launch_game([sys.executable,"-c",sys.argv[2],sys.argv[3],sys.argv[4]],'
            'sys.argv[1],startup_timeout=0.1)\n'
        )
        completed = subprocess.run([sys.executable, '-c', parent, str(self.game), child,
                                    str(trigger), str(result)],
                                   cwd=Path(__file__).resolve().parents[1],
                                   capture_output=True, text=True, timeout=10)
        self.assertEqual(completed.returncode, 0, completed.stderr)
        trigger.touch()  # The child can finish only after the parent has exited.
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            if result.exists() and result.read_text(encoding='utf-8') == 'survived':
                break
            time.sleep(0.02)
        self.assertEqual(result.read_text(encoding='utf-8'), 'survived')
        self.assertIn('child still running after parent exit', (self.game / 'game-output.log').read_text())
        # Windows releases inherited log handles only when child shutdown ends.
        # The result marker can become visible a few milliseconds before that.
        deadline = time.monotonic() + 5
        while True:
            try:
                (self.game / 'game-output.log').unlink()
                break
            except PermissionError:
                if time.monotonic() >= deadline:
                    raise
                time.sleep(0.02)


if __name__ == '__main__':
    unittest.main()
