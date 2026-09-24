from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
from server_list import ensure_server, update_server_list

# A complete, uncompressed Minecraft servers.dat fixture: one visible server.
VISIBLE = bytes.fromhex(
    '0a0000 09000773657276657273 0a00000001 '
    '0800046e616d65 000853444f6372616674 '
    '0800026970 001439332e3131392e3130342e3231373a3235353837 '
    '01000668696464656e00 00 00'
)
ENTRY = VISIBLE[18:-1]
HEADER = VISIBLE[:14]
HIDDEN = ENTRY.replace(b'hidden\x00', b'hidden\x01')
OTHER = ENTRY.replace(b'SDOcraft', b'OtherSrv').replace(b'93.119.104.217:25587', b'93.119.104.218:25587')


def server_file(entries, extra=b''):
    return HEADER + struct.pack('>i', len(entries)) + b''.join(entries) + extra + b'\x00'


class ServerListTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.game = Path(self.tmp.name)
        self.path = self.game / 'servers.dat'

    def tearDown(self):
        self.tmp.cleanup()

    def test_first_launch_creates_visible_server_without_duplicates(self):
        ensure_server(self.game)
        self.assertEqual(self.path.read_bytes(), VISIBLE)
        timestamp = self.path.stat().st_mtime_ns
        ensure_server(self.game)
        self.assertEqual(self.path.read_bytes(), VISIBLE)
        self.assertEqual(self.path.stat().st_mtime_ns, timestamp)
        self.assertFalse((self.game / 'server-list-backups').exists())

    def test_quick_play_hidden_entry_becomes_visible(self):
        existing = server_file([HIDDEN])
        self.path.write_bytes(existing)
        ensure_server(self.game)
        self.assertEqual(self.path.read_bytes(), VISIBLE)
        backup, = (self.game / 'server-list-backups').glob('*.dat')
        self.assertEqual(backup.read_bytes(), existing)

    def test_other_servers_and_unknown_nbt_tags_are_preserved(self):
        # Java modified UTF-8 encodes this supplementary character as two surrogates.
        emoji = b'\xed\xa0\xbd\xed\xb8\x80'
        foreign = OTHER[:-1] + b'\x08\x00\x04note\x00\x06' + emoji + b'\x00'
        extra = b'\x0b\x00\x05extra\x00\x00\x00\x02\x00\x00\x00\x01\xff\xff\xff\xff'
        original = server_file([foreign], extra)
        self.assertEqual(update_server_list(original), server_file([ENTRY, foreign], extra))

    def test_visible_entry_is_kept_when_quick_play_added_a_duplicate(self):
        settings = b'\x01\x00\x0eacceptTextures\x01'
        existing = ENTRY[:-1] + settings + b'\x00'
        original = server_file([HIDDEN, OTHER, existing])
        self.assertEqual(update_server_list(original), server_file([OTHER, existing]))

    def test_corrupt_list_is_backed_up_before_repair(self):
        original = b'broken NBT file'
        self.path.write_bytes(original)
        ensure_server(self.game)
        self.assertEqual(self.path.read_bytes(), VISIBLE)
        backup, = (self.game / 'server-list-backups').glob('*.dat')
        self.assertEqual(backup.read_bytes(), original)

    def test_write_failure_leaves_previous_server_list_intact(self):
        original = server_file([OTHER])
        self.path.write_bytes(original)
        with patch.object(Path, 'replace', side_effect=OSError('disk error')):
            with self.assertRaises(OSError):
                ensure_server(self.game)
        self.assertEqual(self.path.read_bytes(), original)
        self.assertFalse(list(self.game.glob('.servers-*')))


if __name__ == '__main__':
    unittest.main()
