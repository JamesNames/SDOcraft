import copy
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import archive_pack as pack


def item(path, data):
    return {'path': path, 'size': len(data), 'sha256': hashlib.sha256(data).hexdigest()}


class Response(io.BytesIO):
    def __init__(self, data, status=200, headers=None, url='https://drive.usercontent.google.com/download'):
        super().__init__(data)
        self.status, self.headers, self.url = status, headers or {}, url

    def geturl(self):
        return self.url


class ArchivePackTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.manifest = json.loads((Path(__file__).resolve().parents[1] / 'release/modpack-chaosz.json').read_text())
        self.files = {'mods/example.jar': b'mod', 'config/example.json': b'config', 'options.txt': b'lang:en_us\n'}
        self.manifest['files'] = [item(p, b) for p, b in self.files.items()]
        self.game = self.root / pack.PROFILE / 'minecraft'
        self.manifest['archive'].update(item('serverpack.rar', b'archive data'))

    def tearDown(self):
        self.temporary.cleanup()

    def extract(self, archive, entries, staging, status):
        for entry in entries:
            target = staging / pack.PACK_ROOT / entry['path']
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(self.files[entry['path']])

    def install(self, repair=False):
        with patch.object(pack, 'cached_archive', return_value=self.root/'pack.rar'), \
                patch.object(pack, 'validate_archive'), \
                patch.object(pack, 'extract_selected', side_effect=self.extract), \
                patch.object(pack, 'sync_photo'):
            return pack.sync_files(self.root, self.manifest, repair=repair)

    def test_reject_paths_and_unmanaged_player_data(self):
        for path in ('../mods/a.jar', '/mods/a.jar', 'mods\\a.jar', 'mods/a.jar:stream',
                     'mods/CON.jar', 'mods/a.jar.', 'mods/nested/a.jar', 'mods/a.exe',
                     'servers.dat', 'minecraftinstance.json', 'saves/world/level.dat',
                     'config/a/../b', 'config//b', 'config/./b'):
            with self.subTest(path=path), self.assertRaises(ValueError):
                pack.safe_path(path)

    def test_manifest_rejects_case_collisions_conflicts_and_other_pack(self):
        cases = []
        duplicate = copy.deepcopy(self.manifest)
        duplicate['files'].append(item('mods/EXAMPLE.jar', b'mod'))
        cases.append(duplicate)
        conflict = copy.deepcopy(self.manifest)
        conflict['files'] += [item('config/folder', b'a'), item('config/folder/file', b'b')]
        cases.append(conflict)
        other = copy.deepcopy(self.manifest)
        other['archive']['drive_id'] = 'other'
        cases.append(other)
        for manifest in cases:
            with self.assertRaises(ValueError):
                pack.validate_manifest(manifest)

    def test_download_follows_only_expected_public_confirmation(self):
        html = ('<form id="download-form" action="https://drive.usercontent.google.com/download">'
                f'<input type="hidden" name="id" value="{pack.PACK_DRIVE_ID}">'
                '<input type="hidden" name="export" value="download">'
                '<input type="hidden" name="confirm" value="t">'
                '<input type="hidden" name="uuid" value="confirmation"></form>').encode()
        with patch.object(pack, 'build_opener') as factory:
            factory.return_value.open.side_effect = [Response(html, headers={'Content-Type': 'text/html'}), Response(b'rar')]
            with pack.drive_response(pack.PACK_DRIVE_ID, 12) as response:
                self.assertEqual(response.read(), b'rar')
            request = factory.return_value.open.call_args.args[0]
            self.assertIn('confirm=t', request.full_url)
            self.assertEqual(request.get_header('Range'), 'bytes=12-')
        with patch.object(pack, 'build_opener') as factory:
            factory.return_value.open.return_value = Response(html.replace(b'drive.usercontent.google.com/download', b'evil.example/download'), headers={'Content-Type': 'text/html'})
            with self.assertRaises(RuntimeError):
                pack.drive_response(pack.PACK_DRIVE_ID)
            self.assertEqual(factory.return_value.open.call_count, 1)

    def test_download_resumes_verifies_and_reuses_cache(self):
        meta = self.manifest['archive']
        partial = self.root / 'downloads' / (meta['sha256'] + '.rar.part')
        partial.parent.mkdir()
        partial.write_bytes(b'archive ')
        with patch.object(pack, 'drive_response', return_value=Response(b'data', 206, {'Content-Range': 'bytes 8-11/12'})) as download:
            target = pack.cached_archive(self.root, self.manifest)
            download.assert_called_once_with(pack.PACK_DRIVE_ID, 8)
        self.assertEqual(target.read_bytes(), b'archive data')
        with patch.object(pack, 'drive_response') as download:
            self.assertEqual(pack.cached_archive(self.root, self.manifest), target)
            download.assert_not_called()

    def test_ignored_range_restarts_download_and_bad_hash_never_installs(self):
        meta = self.manifest['archive']
        partial = self.root / 'downloads' / (meta['sha256'] + '.rar.part')
        partial.parent.mkdir()
        partial.write_bytes(b'archive ')
        with patch.object(pack, 'drive_response', return_value=Response(b'archive data')):
            target = pack.cached_archive(self.root, self.manifest)
        self.assertEqual(target.read_bytes(), b'archive data')
        target.unlink()
        with patch.object(pack, 'drive_response', return_value=Response(b'corrupt data')):
            with self.assertRaises(RuntimeError):
                pack.cached_archive(self.root, self.manifest)
        self.assertFalse(target.exists())
        self.assertFalse(partial.exists())

    def test_incorrect_range_keeps_partial_for_retry(self):
        meta = self.manifest['archive']
        partial = self.root / 'downloads' / (meta['sha256'] + '.rar.part')
        partial.parent.mkdir()
        partial.write_bytes(b'archive ')
        with patch.object(pack, 'drive_response', return_value=Response(b'data', 206, {'Content-Range': 'bytes 0-3/12'})):
            with self.assertRaises(RuntimeError):
                pack.cached_archive(self.root, self.manifest)
        self.assertEqual(partial.read_bytes(), b'archive ')

    def test_repeat_preserves_settings_repair_fixes_mods_and_configs(self):
        self.assertEqual(self.install(), 3)
        (self.game/'config/example.json').write_bytes(b'user config')
        (self.game/'options.txt').write_bytes(b'lang:ru_ru\n')
        self.assertEqual(self.install(), 0)
        (self.game/'mods/example.jar').write_bytes(b'broken')
        self.assertEqual(pack.check_files(self.root, self.manifest), ['mods/example.jar'])
        self.assertEqual(self.install(), 1)
        self.assertEqual((self.game/'config/example.json').read_bytes(), b'user config')
        self.assertEqual(self.install(repair=True), 1)
        self.assertEqual((self.game/'config/example.json').read_bytes(), b'config')
        self.assertEqual((self.game/'options.txt').read_bytes(), b'lang:ru_ru\n')

    def test_apply_failure_restores_original_files_and_state(self):
        self.install()
        state = self.root/pack.PROFILE/'installed-pack.json'
        previous = state.read_bytes()
        (self.game/'mods/example.jar').write_bytes(b'original local mod')
        with patch.object(pack, 'atomic_json', side_effect=OSError('disk error')):
            with self.assertRaises(OSError):
                self.install()
        self.assertEqual((self.game/'mods/example.jar').read_bytes(), b'original local mod')
        self.assertEqual(state.read_bytes(), previous)

    def test_only_removed_managed_files_are_retired(self):
        self.install()
        (self.game/'mods/personal.jar').write_bytes(b'personal')
        world = self.game/'saves/world/level.dat'
        world.parent.mkdir(parents=True)
        world.write_bytes(b'world')
        self.manifest['files'] = [e for e in self.manifest['files'] if e['path'] != 'mods/example.jar']
        self.install()
        self.assertFalse((self.game/'mods/example.jar').exists())
        self.assertEqual((self.game/'mods/personal.jar').read_bytes(), b'personal')
        self.assertEqual(world.read_bytes(), b'world')


if __name__ == '__main__':
    unittest.main()
