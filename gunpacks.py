"""Restore Maxstuff's embedded TaCZ pack before Minecraft starts."""
import json
from pathlib import Path, PurePosixPath
import shutil
import stat
import tempfile
import uuid
import zipfile
import zlib
from updater import destination, matches

PREFIX = 'assets/maxstuff/gunpack/maxstuff/'
MAX_UNPACKED = 512 * 1024 * 1024
RESERVED = {'CON', 'PRN', 'AUX', 'NUL'} | {f'{p}{i}' for p in ('COM', 'LPT') for i in range(1, 10)}


def pack_entries(jar):
    entries = []
    seen = set()
    total = 0
    for info in jar.infolist():
        # ZipInfo normalizes backslashes on Windows; validate the original name.
        if not info.orig_filename.startswith(PREFIX) or info.is_dir():
            continue
        name = info.orig_filename[len(PREFIX):]
        path = PurePosixPath(name)
        if (not name or path.is_absolute() or path.as_posix() != name
                or '..' in path.parts or '\\' in name or ':' in name
                or any(ord(c) < 32 for c in name)
                or any(p.endswith((' ', '.')) or p.split('.')[0].upper() in RESERVED for p in path.parts)
                or stat.S_ISLNK(info.external_attr >> 16)
                or name.casefold() in seen):
            raise ValueError('Небезопасный путь в ресурсах Maxstuff: ' + name)
        seen.add(name.casefold())
        total += info.file_size
        entries.append((path, info))
        if total > MAX_UNPACKED or len(entries) > 10000:
            raise ValueError('Ресурсы Maxstuff слишком большие')
    if 'gunpack.meta.json' not in seen:
        raise ValueError('В моде Maxstuff не найдена папка оружейного пакета')
    meta = json.loads(jar.read(PREFIX + 'gunpack.meta.json'))
    if meta.get('namespace') != 'maxstuff':
        raise ValueError('Неправильное имя оружейного пакета Maxstuff')
    return entries


def pack_folder(root):
    folder = root / 'minecraft' / 'tacz' / 'maxstuff'
    for part in (root / 'minecraft', folder.parent, folder):
        if part.is_symlink() or not part.resolve().is_relative_to(root.resolve()):
            raise ValueError('Небезопасный путь к папке tacz/maxstuff')
    if folder.exists() and not folder.is_dir():
        raise ValueError('Вместо папки tacz/maxstuff найден файл')
    return folder


def resource_matches(folder, relative, info):
    file = folder.joinpath(*relative.parts)
    for part in (file, *file.parents):
        if part == folder:
            break
        if part.is_symlink():
            raise ValueError('Символическая ссылка в ресурсах Maxstuff: ' + str(relative))
    if not file.is_file() or file.stat().st_size != info.file_size:
        return False
    crc = 0
    with file.open('rb') as stream:
        while chunk := stream.read(256 * 1024):
            crc = zlib.crc32(chunk, crc)
    return crc == info.CRC


def sources(root, manifest):
    for item in manifest['files']:
        if 'maxstuff' in item.get('mod_ids', []):
            yield destination(root, item), item


def check_gunpacks(root, manifest):
    missing = []
    for source, item in sources(root, manifest):
        # The regular mod check reports a missing or corrupt JAR first.
        if not matches(source, item):
            continue
        folder = pack_folder(root)
        with zipfile.ZipFile(source) as jar:
            entries = pack_entries(jar)
            if any(not resource_matches(folder, path, info) for path, info in entries):
                missing.append('tacz/maxstuff (модели, текстуры и значки)')
    return missing


def sync_gunpacks(root, manifest, status=lambda text: None):
    restored = 0
    for source, item in sources(root, manifest):
        if not matches(source, item):
            raise RuntimeError('Сначала установи или восстанови мод Maxstuff')
        folder = pack_folder(root)
        with zipfile.ZipFile(source) as jar:
            entries = pack_entries(jar)
            if all(resource_matches(folder, path, info) for path, info in entries):
                continue
            status('Восстановление моделей, текстур и значков Maxstuff…')
            with tempfile.TemporaryDirectory(prefix='.gunpack-', dir=root) as temporary:
                staged = Path(temporary) / 'maxstuff'
                staged.mkdir()
                for relative, info in entries:
                    target = staged.joinpath(*relative.parts)
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with jar.open(info) as src, target.open('wb') as dst:
                        shutil.copyfileobj(src, dst)
                # Every CRC has been checked by ZipFile before touching the old pack.
                folder.parent.mkdir(parents=True, exist_ok=True)
                backup = None
                if folder.exists():
                    backup = root / 'mod-backups' / uuid.uuid4().hex / 'tacz' / 'maxstuff'
                    backup.parent.mkdir(parents=True, exist_ok=True)
                    folder.replace(backup)
                try:
                    staged.replace(folder)
                except Exception:
                    if backup is not None:
                        backup.replace(folder)
                    raise
                restored += 1
        status('Ресурсы Maxstuff готовы: tacz/maxstuff')
    return restored
