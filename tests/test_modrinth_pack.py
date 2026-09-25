import copy
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import URLError
import zipfile

from config import FORGE, MINECRAFT, PROFILE
import modrinth_pack as pack


class Response(io.BytesIO):
    def geturl(self):
        return 'https://cdn.modrinth.com/data/test/versions/test/mod.jar'


def remote(path='mods/example.jar', content=b'good mod', client='required'):
    return {'path': path, 'fileSize': len(content),
            'hashes': {'sha512': hashlib.sha512(content).hexdigest()},
            'downloads': ['https://cdn.modrinth.com/data/test/versions/test/mod.jar'],
            'env': {'client': client, 'server': 'required'}}


class ModrinthPackTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.game = self.root / PROFILE / 'minecraft'
        source = Path(__file__).resolve().parents[1] / 'release/modpack-solocraft.json'
        self.manifest = json.loads(source.read_text(encoding='utf-8'))
        self.index = {'formatVersion': 1, 'game': 'minecraft', 'versionId': '0.1.2',
                      'dependencies': {'minecraft': MINECRAFT, 'forge': FORGE},
                      'files': [remote()]}
        self.archive = self.root / 'test.mrpack'
        self.write_archive()
        self.addCleanup(patch.stopall)
        patch.object(pack, 'sync_photo', return_value=0).start()
        patch.object(pack, 'cached_archive', side_effect=lambda *a: self.archive).start()
        self.http = patch.object(pack, 'urlopen', side_effect=lambda *a, **k: Response(b'good mod')).start()

    def write_archive(self, overrides=None):
        if overrides is None:
            overrides = {'overrides/config/example.toml': b'value=1',
                         'overrides/options.txt': b'lang:en_us\nguiScale:4\nfov:70\n',
                         'overrides/mods/embedded.jar': b'embedded mod'}
        with zipfile.ZipFile(self.archive, 'w') as archive:
            archive.writestr('modrinth.index.json', json.dumps(self.index))
            for name, data in overrides.items():
                # Preserve raw backslashes in test ZIPs on Windows as well.
                info = zipfile.ZipInfo()
                info.filename = name
                archive.writestr(info, data)

    def install(self, **kwargs):
        return pack.sync_files(self.root, self.manifest, **kwargs)

    def test_install_isolated_profile_all_layers_and_no_redownload(self):
        old = self.root / 'minecraft/mods/old-war-mod.jar'
        old.parent.mkdir(parents=True)
        old.write_bytes(b'old pack stays here')
        self.index['files'].append(remote('mods/server-only.jar', client='unsupported'))
        self.write_archive({'overrides/config/example.toml': b'common',
                            'client-overrides/config/example.toml': b'client',
                            'server-overrides/mods/server-override.jar': b'server'})
        self.assertEqual(self.install(), 2)
        self.assertEqual((self.game / 'config/example.toml').read_bytes(), b'client')
        self.assertEqual([p.name for p in (self.game / 'mods').iterdir()], ['example.jar'])
        self.assertEqual(self.install(), 0)
        self.assertEqual(self.http.call_count, 1)
        self.assertEqual(old.read_bytes(), b'old pack stays here')

    def test_preserves_user_options_and_config_and_repairs_mod(self):
        self.install()
        options = self.game / 'options.txt'
        options.write_bytes(b'lang:ru_ru\nguiScale:2\nfov:90\n')
        config = self.game / 'config/example.toml'
        config.write_bytes(b'value=42')
        (self.game / 'mods/example.jar').write_bytes(b'broken')
        self.assertEqual(pack.check_files(self.root, self.manifest), ['mods/example.jar'])
        self.assertEqual(self.install(), 1)
        self.assertEqual(config.read_bytes(), b'value=42')
        self.assertEqual(self.install(repair=True), 1)
        self.assertEqual(config.read_bytes(), b'value=1')
        self.assertEqual(options.read_bytes(), b'lang:ru_ru\nguiScale:2\nfov:90\n')
        backups = list((self.root / PROFILE / 'backups').rglob('example.toml'))
        self.assertEqual(backups[0].read_bytes(), b'value=42')

    def test_corrupt_download_does_not_apply_any_changes(self):
        self.install()
        old = (self.game / 'mods/example.jar').read_bytes()
        self.index['files'][0] = remote(content=b'new good mod')
        self.write_archive({'overrides/config/example.toml': b'value=2'})
        self.http.side_effect = lambda *a, **k: Response(b'bad download')
        with self.assertRaises(RuntimeError):
            self.install()
        self.assertEqual((self.game / 'mods/example.jar').read_bytes(), old)
        self.assertEqual((self.game / 'config/example.toml').read_bytes(), b'value=1')
        self.assertFalse(list((self.root / PROFILE).glob('.update-*')))

    def test_state_write_failure_rolls_back_changes_and_removals(self):
        self.install()
        previous_state = (self.root / PROFILE / 'installed-pack.json').read_bytes()
        self.index['files'][0]['path'] = 'mods/replacement.jar'
        self.write_archive({'overrides/config/example.toml': b'value=2'})
        with patch.object(pack, 'atomic_json', side_effect=OSError('disk full')):
            with self.assertRaises(OSError):
                self.install()
        self.assertEqual((self.game / 'mods/example.jar').read_bytes(), b'good mod')
        self.assertEqual((self.game / 'mods/embedded.jar').read_bytes(), b'embedded mod')
        self.assertFalse((self.game / 'mods/replacement.jar').exists())
        self.assertEqual((self.root / PROFILE / 'installed-pack.json').read_bytes(), previous_state)

    def test_update_backs_up_removed_files_preserves_extra_mods(self):
        self.install()
        (self.game / 'mods/extra.jar').write_bytes(b'custom')
        self.index['files'][0]['path'] = 'mods/replacement.jar'
        self.write_archive({'overrides/config/example.toml': b'value=2'})
        self.install()
        self.assertFalse((self.game / 'mods/example.jar').exists())
        self.assertTrue((self.game / 'mods/replacement.jar').exists())
        self.assertEqual((self.game / 'mods/extra.jar').read_bytes(), b'custom')
        self.assertTrue(list((self.root / PROFILE / 'backups').rglob('example.jar')))

    def test_rejects_paths_in_index_and_raw_archive(self):
        for name in ['../escape', '/config/x', 'config/../x', 'mods\\x.jar', 'config/CON',
                     'config/name.', 'config/x:stream', 'mods/run.exe', 'saves/world/level.dat']:
            with self.subTest(name=name):
                self.index['files'][0]['path'] = name
                self.write_archive()
                with self.assertRaises(ValueError):
                    pack.read_pack(self.archive, self.manifest)
        self.index['files'][0]['path'] = 'mods/example.jar'
        for name in ['overrides/../../outside', 'overrides/config\\outside', 'client-overrides/config/NUL.txt']:
            self.write_archive({name: b'bad'})
            with self.assertRaises(ValueError):
                pack.read_pack(self.archive, self.manifest)

    def test_rejects_duplicate_and_parent_paths_wrong_dependencies_and_hosts(self):
        self.index['files'].append(remote('mods/EXAMPLE.jar'))
        self.write_archive()
        with self.assertRaises(ValueError):
            pack.read_pack(self.archive, self.manifest)
        self.index['files'].pop()
        self.write_archive({'overrides/config/one': b'file', 'overrides/config/one/two': b'child'})
        with self.assertRaises(ValueError):
            pack.read_pack(self.archive, self.manifest)
        self.index['dependencies']['forge'] = '47.4.0'
        self.write_archive()
        with self.assertRaises(ValueError):
            pack.read_pack(self.archive, self.manifest)
        self.index['dependencies']['forge'] = FORGE
        self.index['files'][0]['downloads'] = ['https://example.com/mod.jar']
        self.write_archive()
        with self.assertRaises(ValueError):
            pack.read_pack(self.archive, self.manifest)

    def test_manifest_pins_project_and_forge_legacy_manifest_stays_separate(self):
        pack.validate_manifest(self.manifest)
        for key, value in [('url', self.manifest['pack']['url'].replace('Rkfueyz7', 'another')), ('sha256', '0'), ('size', -1)]:
            bad = copy.deepcopy(self.manifest)
            bad['pack'][key] = value
            with self.assertRaises(ValueError):
                pack.validate_manifest(bad)
        legacy = json.loads((Path(__file__).resolve().parents[1] / 'release/modpack.json').read_text())
        self.assertEqual(legacy['schema'], 1)
        self.assertEqual(legacy['forge'], '47.4.0')
        self.assertEqual(len(legacy['files']), 12)

    def test_offline_uses_only_solocraft_manifest(self):
        pack.atomic_json(self.root / 'solocraft-manifest.json', self.manifest)
        self.http.side_effect = URLError('offline')
        self.assertEqual(pack.fetch_manifest(self.root), self.manifest)
        (self.root / 'solocraft-manifest.json').unlink()
        pack.atomic_json(self.root / 'modpack.json', self.manifest)
        with self.assertRaises(RuntimeError):
            pack.fetch_manifest(self.root)

    def test_rejects_symlink_destination_before_download(self):
        outside = self.root / 'outside'
        outside.mkdir()
        self.game.mkdir(parents=True)
        try:
            (self.game / 'mods').symlink_to(outside, target_is_directory=True)
        except OSError:
            self.skipTest('symlinks unavailable for this Windows account')
        with self.assertRaises(ValueError):
            self.install()
        self.assertEqual(list(outside.iterdir()), [])
        self.http.assert_not_called()


if __name__ == '__main__':
    unittest.main()
