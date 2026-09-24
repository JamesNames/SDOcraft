"""Download checked release assets and install only files that changed."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import ssl
import tempfile
import tomllib
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen
import uuid
import zipfile
import certifi
from config import REPOSITORY, MANIFEST_URL, MINECRAFT, FORGE

MAX_MANIFEST = 1024 * 1024
MAX_FILE = 512 * 1024 * 1024
CONTEXT = ssl.create_default_context(cafile=certifi.where())


def digest(path):
    with Path(path).open('rb') as file:
        return hashlib.file_digest(file, 'sha256').hexdigest()


def valid_name(value):
    if not isinstance(value, str) or '\\' in value or ':' in value or any(ord(c) < 32 for c in value):
        raise ValueError('Некоректний шлях у збірці')
    path = PurePosixPath(value)
    if path.as_posix() != value or path.is_absolute() or '..' in path.parts or len(path.parts) != 2:
        raise ValueError('Некоректний шлях у збірці')
    if path.parts[0] == 'mods' and path.name.endswith('.jar'):
        return value
    if value == 'assets/squad.webp':
        return value
    raise ValueError('Збірка містить непідтримуваний тип файла')


def validate_manifest(data):
    if not isinstance(data, dict) or data.get('schema') != 1:
        raise ValueError('Непідтримуваний формат збірки')
    if data.get('minecraft') != MINECRAFT or data.get('forge') != FORGE:
        raise ValueError('Оновлена збірка потребує нової версії лаунчера. Завантаж SDOcraft.exe із Releases.')
    files = data.get('files')
    if not isinstance(files, list) or not 1 <= len(files) <= 500:
        raise ValueError('Некоректний список файлів')
    seen = set()
    total = 0
    for item in files:
        if not isinstance(item, dict):
            raise ValueError('Некоректний опис файла')
        name = valid_name(item.get('path'))
        if name.casefold() in seen:
            raise ValueError('Дублікат файла у збірці')
        seen.add(name.casefold())
        if not isinstance(item.get('size'), int) or not 0 < item['size'] <= MAX_FILE:
            raise ValueError('Некоректний розмір файла')
        if not re.fullmatch('[a-f0-9]{64}', str(item.get('sha256', ''))):
            raise ValueError('Некоректна контрольна сума')
        url = urlsplit(item.get('url', ''))
        prefix = f'/{REPOSITORY}/releases/download/'
        if url.scheme != 'https' or url.netloc != 'github.com' or not url.path.startswith(prefix) or url.query or url.fragment:
            raise ValueError('Файли збірки мають завантажуватися з Releases SDOcraft')
        if not isinstance(item.get('mod_ids', []), list) or not all(isinstance(v, str) for v in item.get('mod_ids', [])):
            raise ValueError('Некоректні ідентифікатори модів')
        total += item['size']
    if total > 2 * 1024**3 or 'assets/squad.webp' not in seen or not any(n.startswith('mods/') for n in seen):
        raise ValueError('Неповна або завелика збірка')
    return data


def destination(root, item):
    relative = valid_name(item['path'])
    if relative.startswith('mods/'):
        path = root / 'minecraft' / relative
    else:
        path = root / relative
    if not path.resolve().is_relative_to(root.resolve()):
        raise ValueError('Шлях виходить за межі папки SDOcraft')
    if path.is_symlink():
        raise ValueError('Замість файла знайдено символічне посилання')
    return path


def atomic_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=path.parent, delete=False) as f:
        tmp = Path(f.name)
        json.dump(data, f, ensure_ascii=False, indent=2)
    try:
        tmp.replace(path)
    finally:
        tmp.unlink(missing_ok=True)


def cached_manifest(root, name='modpack.json'):
    return validate_manifest(json.loads((root / name).read_text(encoding='utf-8')))


def fetch_manifest(root, status=lambda text: None):
    try:
        request = Request(MANIFEST_URL, headers={'User-Agent': 'SDOcraft/1.3', 'Cache-Control': 'no-cache'})
        with urlopen(request, timeout=15, context=CONTEXT) as response:
            payload = response.read(MAX_MANIFEST + 1)
        if len(payload) > MAX_MANIFEST:
            raise ValueError('Файл опису збірки завеликий')
        data = validate_manifest(json.loads(payload))
        atomic_json(root / 'modpack.json', data)
        return data
    except (URLError, TimeoutError, OSError) as exc:
        try:
            data = cached_manifest(root)
        except (OSError, ValueError, KeyError):
            raise RuntimeError('Не вдалося отримати збірку. Перевір інтернет. Власник має опублікувати modpack.json і файли у GitHub Releases.') from exc
        status('GitHub недоступний. Використовую збережений список файлів збірки.')
        return data


def matches(path, item):
    return path.is_file() and path.stat().st_size == item['size'] and digest(path) == item['sha256']


def check_files(root, manifest, photo_only=False):
    return [item for item in manifest['files'] if (not photo_only or item['path'].startswith('assets/'))
            and not matches(destination(root, item), item)]


def download(item, target, status=lambda text: None, progress=lambda value: None):
    request = Request(item['url'], headers={'User-Agent': 'SDOcraft/1.3'})
    size = 0
    sha = hashlib.sha256()
    try:
        with urlopen(request, timeout=30, context=CONTEXT) as response, target.open('wb') as output:
            if urlsplit(response.geturl()).scheme != 'https':
                raise RuntimeError('Незахищене перенаправлення завантаження')
            while True:
                chunk = response.read(256 * 1024)
                if not chunk:
                    break
                size += len(chunk)
                if size > item['size']:
                    raise RuntimeError('Розмір файла не відповідає опису збірки')
                output.write(chunk)
                sha.update(chunk)
                progress(size * 100 / item['size'])
        if size != item['size'] or sha.hexdigest() != item['sha256']:
            raise RuntimeError('Перевірка SHA-256 не пройдена: ' + item['path'])
    except Exception:
        target.unlink(missing_ok=True)
        raise


def duplicate_mods(root, manifest, obsolete):
    known = {Path(item['path']).name.casefold() for item in manifest['files'] if item['path'].startswith('mods/')}
    ids = {mod for item in manifest['files'] for mod in item.get('mod_ids', [])}
    skip = {Path(item['path']).name.casefold() for item in obsolete}
    for file in (root / 'minecraft' / 'mods').glob('*.jar'):
        if file.name.casefold() in known | skip:
            continue
        try:
            with zipfile.ZipFile(file) as jar:
                if 'META-INF/mods.toml' not in jar.namelist():
                    continue
                meta = tomllib.loads(jar.read('META-INF/mods.toml').decode('utf-8'))
            if ids & {mod['modId'] for mod in meta.get('mods', [])}:
                raise RuntimeError('Знайдено іншу версію мода. Перенеси файл із mods перед запуском: ' + file.name)
        except (OSError, ValueError, KeyError, zipfile.BadZipFile):
            continue


def sync_files(root, manifest, status=lambda text: None, progress=lambda value: None, photo_only=False):
    """Stage all downloads, then install with rollback; preserve replaced files."""
    validate_manifest(manifest)
    root.mkdir(parents=True, exist_ok=True)
    wanted = manifest['files']
    obsolete = []
    if not photo_only:
        try:
            previous = cached_manifest(root, 'installed-pack.json')
        except (OSError, ValueError, KeyError):
            previous = {'files': []}
        current = {item['path'].casefold() for item in wanted}
        obsolete = [i for i in previous['files'] if i['path'].casefold() not in current]
        duplicate_mods(root, manifest, obsolete)
    changed = check_files(root, manifest, photo_only)
    backup_root = root / 'mod-backups' / uuid.uuid4().hex
    changes = []
    with tempfile.TemporaryDirectory(prefix='.update-', dir=root) as staging:
        staging = Path(staging)
        for index, item in enumerate(changed):
            status(f'Завантаження {index + 1}/{len(changed)}: {Path(item["path"]).name}')
            download(item, staging / str(index), status, progress)
        try:
            for index, item in enumerate(changed + obsolete):
                dst = destination(root, item)
                src = staging / str(index) if index < len(changed) else None
                if src is None and not dst.exists():
                    continue
                backup = None
                if dst.exists():
                    if not dst.is_file():
                        raise RuntimeError('Папка замість файла: ' + item['path'])
                    backup = backup_root / item['path']
                    backup.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(dst, backup)
                changes.append((dst, backup))
                dst.parent.mkdir(parents=True, exist_ok=True)
                if src is not None:
                    src.replace(dst)
                else:
                    dst.unlink()
            if not photo_only:
                atomic_json(root / 'installed-pack.json', manifest)
        except Exception:
            for dst, backup in reversed(changes):
                if backup is not None:
                    shutil.copy2(backup, dst)
                else:
                    dst.unlink(missing_ok=True)
            raise
    return len(changed)
