import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import gunpacks


class GunpackTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.source = self.root / 'minecraft/mods/maxstuff.jar'
        self.source.parent.mkdir(parents=True)
        self.folder = self.root / 'minecraft/tacz/maxstuff'
        self.icon = self.folder / 'assets/maxstuff/textures/gun/slot/test.png'
        self.contents = {
            'gunpack.meta.json': b'{"namespace":"maxstuff"}',
            'assets/maxstuff/textures/gun/slot/test.png': b'icon-data',
            'data/maxstuff/index/guns/test.json': b'{}',
        }
        self.make_jar()

    def tearDown(self):
        self.tmp.cleanup()

    def make_jar(self, extras=None):
        with zipfile.ZipFile(self.source, 'w') as jar:
            for path, payload in (self.contents | (extras or {})).items():
                jar.writestr(gunpacks.PREFIX + path, payload)
        raw = self.source.read_bytes()
        self.manifest = {'files': [{'path': 'mods/maxstuff.jar', 'size': len(raw),
                                  'sha256': hashlib.sha256(raw).hexdigest(), 'mod_ids': ['maxstuff']}]}

    def test_first_install_and_second_launch_leave_files_untouched(self):
        self.assertTrue(gunpacks.check_gunpacks(self.root, self.manifest))
        self.assertEqual(gunpacks.sync_gunpacks(self.root, self.manifest), 1)
        timestamp = self.icon.stat().st_mtime_ns
        self.assertEqual(self.icon.read_bytes(), b'icon-data')
        self.assertEqual(gunpacks.check_gunpacks(self.root, self.manifest), [])
        self.assertEqual(gunpacks.sync_gunpacks(self.root, self.manifest), 0)
        self.assertEqual(self.icon.stat().st_mtime_ns, timestamp)
        self.assertFalse((self.root / 'mod-backups').exists())

    def test_restores_corrupt_and_missing_resources_and_preserves_backup(self):
        gunpacks.sync_gunpacks(self.root, self.manifest)
        self.icon.write_bytes(b'bad--data')  # Same size: content must also be checked.
        (self.folder / 'gunpack.meta.json').unlink()
        (self.folder / 'personal-note.txt').write_text('keep in backup')
        other = self.folder.parent / 'another-pack'
        other.mkdir()
        (other / 'keep.txt').write_text('other pack')
        self.assertTrue(gunpacks.check_gunpacks(self.root, self.manifest))
        gunpacks.sync_gunpacks(self.root, self.manifest)
        self.assertEqual(self.icon.read_bytes(), b'icon-data')
        backups = list((self.root / 'mod-backups').rglob('personal-note.txt'))
        self.assertEqual(len(backups), 1)
        self.assertEqual(backups[0].read_text(), 'keep in backup')
        self.assertEqual((other / 'keep.txt').read_text(), 'other pack')

    def test_unsafe_archive_does_not_touch_existing_pack(self):
        gunpacks.sync_gunpacks(self.root, self.manifest)
        for path in ('../escape.txt', 'assets/../../../../escape.txt', 'C:/escape.txt',
                     'assets\\escape.txt', 'assets/CON.png'):
            with self.subTest(path=path):
                self.make_jar({path: b'unsafe'})
                with self.assertRaises(ValueError):
                    gunpacks.sync_gunpacks(self.root, self.manifest)
                self.assertEqual(self.icon.read_bytes(), b'icon-data')
        self.assertFalse((self.root / 'escape.txt').exists())

    def test_failed_install_restores_previous_folder(self):
        gunpacks.sync_gunpacks(self.root, self.manifest)
        self.icon.write_bytes(b'old-icon')
        real_replace = Path.replace
        def fail_new_pack(path, target):
            if path.parent.name.startswith('.gunpack-'):
                raise OSError('disk error')
            return real_replace(path, target)
        with patch.object(Path, 'replace', fail_new_pack):
            with self.assertRaises(OSError):
                gunpacks.sync_gunpacks(self.root, self.manifest)
        self.assertEqual(self.icon.read_bytes(), b'old-icon')
        self.assertFalse(list(self.root.glob('.gunpack-*')))

    def test_corrupt_jar_keeps_existing_pack(self):
        gunpacks.sync_gunpacks(self.root, self.manifest)
        self.source.write_bytes(b'broken')
        with self.assertRaises(RuntimeError):
            gunpacks.sync_gunpacks(self.root, self.manifest)
        self.assertEqual(self.icon.read_bytes(), b'icon-data')


if __name__ == '__main__':
    unittest.main()
