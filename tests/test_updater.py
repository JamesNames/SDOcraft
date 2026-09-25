import copy
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import URLError
import updater
from config import FORGE


def entry(path, payload):
    return {'path': path, 'size': len(payload), 'sha256': hashlib.sha256(payload).hexdigest(),
            'url': 'https://github.com/JamesNames/SDOcraft/releases/download/v1.3.0/' + Path(path).name,
            'mod_ids': []}


def manifest(payload=b'mod-v1'):
    return {'schema': 1, 'minecraft': '1.20.1', 'forge': FORGE,
            'files': [entry('mods/example.jar', payload), entry('assets/squad.webp', b'photo')]}


class Response(io.BytesIO):
    def geturl(self):
        return 'https://release-assets.githubusercontent.com/test'


class UpdaterTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.data = manifest()

    def tearDown(self):
        self.tmp.cleanup()

    def open(self, request, **kwargs):
        return Response(b'photo' if 'squad.webp' in request.full_url else b'mod-v1')

    def test_first_install_and_no_redownload(self):
        with patch.object(updater, 'urlopen', side_effect=self.open) as http:
            self.assertEqual(updater.sync_files(self.root, self.data), 2)
            self.assertEqual(http.call_count, 2)
            self.assertEqual(updater.sync_files(self.root, self.data), 0)
            self.assertEqual(http.call_count, 2)
        self.assertEqual(updater.check_files(self.root, self.data), [])

    def test_repair_downloads_only_corrupt_file_and_keeps_backup(self):
        with patch.object(updater, 'urlopen', side_effect=self.open):
            updater.sync_files(self.root, self.data)
        target = self.root / 'minecraft/mods/example.jar'
        target.write_bytes(b'broken')
        with patch.object(updater, 'urlopen', side_effect=self.open) as http:
            self.assertEqual(updater.sync_files(self.root, self.data), 1)
            self.assertEqual(http.call_count, 1)
        backups = list((self.root / 'mod-backups').rglob('*.jar'))
        self.assertEqual(backups[0].read_bytes(), b'broken')

    def test_corrupt_download_preserves_existing_files(self):
        target = self.root / 'minecraft/mods/example.jar'
        target.parent.mkdir(parents=True)
        target.write_bytes(b'old-good')
        with patch.object(updater, 'urlopen', return_value=Response(b'WRONG!')):
            with self.assertRaises(RuntimeError):
                updater.sync_files(self.root, self.data)
        self.assertEqual(target.read_bytes(), b'old-good')
        self.assertFalse(list(self.root.glob('.update-*')))

    def test_network_failure_preserves_existing_files(self):
        target = self.root / 'minecraft/mods/example.jar'
        target.parent.mkdir(parents=True)
        target.write_bytes(b'old-good')
        with patch.object(updater, 'urlopen', side_effect=URLError('offline')):
            with self.assertRaises(URLError):
                updater.sync_files(self.root, self.data)
        self.assertEqual(target.read_bytes(), b'old-good')

    def test_removed_managed_mod_is_backed_up_extra_mod_stays(self):
        with patch.object(updater, 'urlopen', side_effect=self.open):
            updater.sync_files(self.root, self.data)
        extra = self.root / 'minecraft/mods/extra.jar'
        extra.write_bytes(b'extra')
        updated = copy.deepcopy(self.data)
        updated['files'][0]['path'] = 'mods/new.jar'
        with patch.object(updater, 'urlopen', side_effect=self.open):
            updater.sync_files(self.root, updated)
        self.assertFalse((self.root / 'minecraft/mods/example.jar').exists())
        self.assertTrue((self.root / 'minecraft/mods/new.jar').exists())
        self.assertEqual(extra.read_bytes(), b'extra')
        self.assertEqual(len(list((self.root / 'mod-backups').rglob('example.jar'))), 1)

    def test_failed_commit_rolls_back(self):
        with patch.object(updater, 'urlopen', side_effect=self.open):
            updater.sync_files(self.root, self.data)
        updated = manifest(b'mod-v2')
        def new_open(request, **kw):
            return Response(b'photo' if 'squad.webp' in request.full_url else b'mod-v2')
        with patch.object(updater, 'urlopen', side_effect=new_open), patch.object(updater, 'atomic_json', side_effect=OSError('disk full')):
            with self.assertRaises(OSError):
                updater.sync_files(self.root, updated)
        self.assertEqual((self.root / 'minecraft/mods/example.jar').read_bytes(), b'mod-v1')

    def test_rejects_unsafe_paths_and_urls(self):
        for path in ['../escape.jar', 'mods/../../escape.jar', 'mods\\escape.jar', '/mods/escape.jar', 'mods/start.py']:
            bad = copy.deepcopy(self.data)
            bad['files'][0]['path'] = path
            with self.assertRaises(ValueError):
                updater.validate_manifest(bad)
        for url in ['http://github.com/JamesNames/SDOcraft/releases/download/v1/x.jar',
                    'https://github.com/Other/Repo/releases/download/v1/x.jar']:
            bad = copy.deepcopy(self.data)
            bad['files'][0]['url'] = url
            with self.assertRaises(ValueError):
                updater.validate_manifest(bad)

    def test_cached_manifest_on_network_failure(self):
        updater.atomic_json(self.root / 'modpack.json', self.data)
        with patch.object(updater, 'urlopen', side_effect=URLError('offline')):
            self.assertEqual(updater.fetch_manifest(self.root), self.data)

    def test_maxstuff_uses_only_its_official_modrinth_project(self):
        updated = copy.deepcopy(self.data)
        item = updated['files'][0]
        item['mod_ids'] = ['maxstuff']
        item['url'] = 'https://cdn.modrinth.com/data/zUHF7oUB/versions/gcj2HDH2/maxstuff-legacy-1.8.3_hotfix.jar'
        updater.validate_manifest(updated)
        item['url'] = item['url'].replace('zUHF7oUB', 'anotherProject')
        with self.assertRaises(ValueError):
            updater.validate_manifest(updated)

    def test_photo_bootstrap_does_not_download_mods(self):
        with patch.object(updater, 'urlopen', side_effect=self.open) as http:
            self.assertEqual(updater.sync_files(self.root, self.data, photo_only=True), 1)
            self.assertEqual(http.call_count, 1)
        self.assertFalse((self.root / 'installed-pack.json').exists())
        self.assertFalse((self.root / 'minecraft/mods/example.jar').exists())


if __name__ == '__main__':
    unittest.main()
