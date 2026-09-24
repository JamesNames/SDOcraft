"""Add a visible SDOcraft entry without rewriting other servers' NBT data."""
from pathlib import Path
import shutil
import struct
import tempfile
import uuid
from config import SERVER

MAX_SIZE = 16 * 1024 * 1024


class _Reader:
    def __init__(self, data, position=0):
        self.data, self.pos = data, position

    def take(self, size):
        if size < 0 or self.pos + size > len(self.data):
            raise ValueError('Повреждён файл списка серверов')
        start = self.pos
        self.pos += size
        return self.data[start:self.pos]

    def number(self, fmt):
        return struct.unpack(fmt, self.take(struct.calcsize(fmt)))[0]

    def string(self):
        # Keep Java's modified UTF-8 intact, including emoji in other names.
        return self.take(self.number('>H'))

    def count(self):
        count = self.number('>i')
        if not 0 <= count <= MAX_SIZE:
            raise ValueError('Некорректная длина NBT')
        return count

    def skip(self, kind, depth=0):
        if depth > 64:
            raise ValueError('Слишком сложный файл списка серверов')
        if kind in (1, 2, 3, 4, 5, 6):
            self.take({1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}[kind])
        elif kind in (7, 11, 12):
            self.take(self.count() * {7: 1, 11: 4, 12: 8}[kind])
        elif kind == 8:
            self.string()
        elif kind == 9:
            subtype, count = self.number('>B'), self.count()
            for _ in range(count):
                self.skip(subtype, depth + 1)
        elif kind == 10:
            while subtype := self.number('>B'):
                self.string()
                self.skip(subtype, depth + 1)
        else:
            raise ValueError('Неподдерживаемый тег NBT')

    def fields(self):
        result = []
        while True:
            start = self.pos
            kind = self.number('>B')
            if kind == 0:
                return result
            name = self.string()
            payload = self.pos
            self.skip(kind)
            result.append((kind, name, start, payload, self.pos))


def _string(value):
    return struct.pack('>H', len(value)) + value


def _text_tag(name, value):
    return b'\x08' + _string(name) + _string(value)


def _visible_entry(entry):
    result = []
    has_name = False
    for kind, name, start, payload, end in _Reader(entry).fields():
        if name == b'name':
            if not has_name:
                result.append(_text_tag(b'name', b'SDOcraft'))
                has_name = True
        elif name == b'hidden':
            result.append(b'\x01' + _string(b'hidden') + b'\x00')
        else:
            result.append(entry[start:end])
    if not has_name:
        result.append(_text_tag(b'name', b'SDOcraft'))
    return b''.join(result) + b'\x00'


def update_server_list(data):
    """Patch the uncompressed Java Edition servers.dat compound."""
    if len(data) > MAX_SIZE:
        raise ValueError('Файл списка серверов слишком большой')
    reader = _Reader(data)
    if reader.number('>B') != 10:
        raise ValueError('Неправильный формат списка серверов')
    reader.string()
    fields = reader.fields()
    if reader.pos != len(data):
        raise ValueError('Лишние данные в списке серверов')
    lists = [field for field in fields if field[1] == b'servers']
    if len(lists) > 1 or (lists and lists[0][0] != 9):
        raise ValueError('Некорректный список серверов')
    entries = []
    matching = []
    address = SERVER.encode('ascii')
    if lists:
        _, _, start, payload, end = lists[0]
        reader.pos = payload
        subtype, count = reader.number('>B'), reader.count()
        if subtype not in (0, 10) or (count and subtype != 10):
            raise ValueError('Некорректные записи серверов')
        for _ in range(count):
            begin = reader.pos
            tags = reader.fields()
            entry = data[begin:reader.pos]
            if any(kind == 8 and name == b'ip' and _Reader(data, position).string() == address
                   for kind, name, _, position, _ in tags):
                hidden = any(kind == 1 and name == b'hidden' and data[position:end] != b'\x00'
                             for kind, name, _, position, end in tags)
                matching.append((len(entries), hidden))
            entries.append(entry)
    else:
        start = end = len(data) - 1
    if matching:
        # Prefer the player's visible entry and its icon/resource-pack setting.
        selected = next((index for index, hidden in matching if not hidden), matching[0][0])
        duplicates = {index for index, _ in matching}
        entries = [_visible_entry(entry) if index == selected else entry
                   for index, entry in enumerate(entries)
                   if index == selected or index not in duplicates]
    else:
        entries.insert(0, _text_tag(b'name', b'SDOcraft') + _text_tag(b'ip', address)
                       + b'\x01' + _string(b'hidden') + b'\x00\x00')
    updated = (b'\x09' + _string(b'servers') + b'\x0a'
               + struct.pack('>i', len(entries)) + b''.join(entries))
    return data[:start] + updated + data[end:]


def ensure_server(game_directory):
    game_directory = Path(game_directory)
    game_directory.mkdir(parents=True, exist_ok=True)
    path = game_directory / 'servers.dat'
    try:
        original = path.read_bytes()
    except FileNotFoundError:
        original = None
    try:
        updated = update_server_list(original if original is not None else b'\x0a\x00\x00\x00')
    except ValueError:
        # Minecraft cannot load a malformed list either. Preserve it before repair.
        updated = update_server_list(b'\x0a\x00\x00\x00')
    if updated == original:
        return
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode='wb', dir=game_directory,
                                         prefix='.servers-', delete=False) as file:
            temporary = Path(file.name)
            file.write(updated)
        if original is not None:
            backup = game_directory / 'server-list-backups' / (uuid.uuid4().hex + '.dat')
            backup.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, backup)
        temporary.replace(path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)
