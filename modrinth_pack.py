"""Install the owner's pinned SoloCraft mrpack into an isolated profile."""
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import tempfile
from urllib.error import URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen
import uuid
import zipfile

from config import FORGE, MINECRAFT, PROFILE, REPOSITORY, SOLOCRAFT_MANIFEST_URL, VERSION
from updater import CONTEXT, atomic_json, download, matches

MAX_FILE = 512 * 1024**2
MAX_TOTAL = 2 * 1024**3
MAX_INDEX = 2 * 1024**2
RESERVED = {'con', 'prn', 'aux', 'nul', *(f'com{i}' for i in range(1, 10)),
            *(f'lpt{i}' for i in range(1, 10))}


def safe_path(value):
    if not isinstance(value, str) or not value or len(value) > 240:
        raise ValueError('Некорректный путь в SoloCraft')
    if any(ord(c) < 32 or c in '\\:<>"|?*' for c in value):
        raise ValueError('Недопустимые символы в пути SoloCraft')
    path = PurePosixPath(value)
    if path.as_posix() != value or path.is_absolute() or '..' in path.parts:
        raise ValueError('Путь выходит за пределы сборки')
    if any(p.endswith((' ', '.')) or p.split('.')[0].casefold() in RESERVED for p in path.parts):
        raise ValueError('Недопустимое имя файла Windows')
    if value == 'options.txt':
        return value
    if len(path.parts) < 2 or path.parts[0] not in {'mods', 'config', 'defaultconfigs', 'resourcepacks', 'shaderpacks', 'kubejs', 'scripts'}:
        raise ValueError('Неподдерживаемая папка в SoloCraft: ' + value)
    if path.parts[0] == 'mods' and (len(path.parts) != 2 or path.suffix != '.jar'):
        raise ValueError('Некорректный мод в SoloCraft')
    return value


def inside(root, relative):
    path = root / relative
    if not path.resolve().is_relative_to(root.resolve()):
        raise ValueError('Путь выходит за пределы папки игры')
    for part in [path, *path.parents]:
        if part.is_symlink() or (hasattr(part, 'is_junction') and part.is_junction()):
            raise ValueError('Ссылки вместо папок игры не поддерживаются')
        if part == root:
            break
    return path


def checked_size(value, maximum=MAX_FILE):
    if type(value) is not int or not 0 <= value <= maximum:
        raise ValueError('Некорректный размер файла SoloCraft')
    return value


def checked_hash(value, algorithm):
    length = hashlib.new(algorithm).digest_size * 2
    if not isinstance(value, str) or not re.fullmatch('[a-f0-9]{' + str(length) + '}', value):
        raise ValueError('Некорректная контрольная сумма SoloCraft')
    return value


def cdn_url(value):
    if not isinstance(value, str):
        raise ValueError('Некорректный адрес Modrinth')
    url = urlsplit(value)
    if (url.scheme != 'https' or url.netloc != 'cdn.modrinth.com'
            or not url.path.startswith('/data/') or url.query or url.fragment
            or any(c.isspace() for c in value)):
        raise ValueError('Загрузка разрешена только с CDN Modrinth')
    return value


def validate_manifest(data):
    if not isinstance(data, dict) or data.get('schema') != 2 or data.get('profile') != 'solocraft':
        raise ValueError('Неподдерживаемое описание SoloCraft')
    if data.get('minecraft') != MINECRAFT or data.get('forge') != FORGE:
        raise ValueError('Сборка требует нового лаунчера. Скачай последнюю версию SDOcraft.exe.')
    pack = data.get('pack', {})
    if pack.get('project_id') != 'Rkfueyz7' or not re.fullmatch('[A-Za-z0-9]+', str(pack.get('version_id', ''))):
        raise ValueError('Неверный проект SoloCraft')
    if not isinstance(pack.get('version'), str) or not pack['version']:
        raise ValueError('Не указана версия SoloCraft')
    url = urlsplit(cdn_url(pack.get('url')))
    if not url.path.startswith('/data/Rkfueyz7/versions/' + pack['version_id'] + '/') or not url.path.endswith('.mrpack'):
        raise ValueError('Архив не принадлежит выбранной версии SoloCraft')
    checked_size(pack.get('size'), 128 * 1024**2)
    checked_hash(pack.get('sha256'), 'sha256')
    photo = data.get('photo', {})
    photo_url = urlsplit(photo.get('url', ''))
    if (photo.get('path') != 'assets/squad.webp' or photo_url.scheme != 'https'
            or photo_url.netloc != 'github.com'
            or not photo_url.path.startswith(f'/{REPOSITORY}/releases/download/')
            or photo_url.query or photo_url.fragment):
        raise ValueError('Некорректное фото лаунчера')
    checked_size(photo.get('size'), 10 * 1024**2)
    checked_hash(photo.get('sha256'), 'sha256')
    return data


def fetch_manifest(root, status=lambda text: None):
    cache = root / 'solocraft-manifest.json'
    try:
        request = Request(SOLOCRAFT_MANIFEST_URL, headers={'User-Agent': 'SDOcraft/' + VERSION, 'Cache-Control': 'no-cache'})
        with urlopen(request, timeout=15, context=CONTEXT) as response:
            data = response.read(MAX_INDEX + 1)
        if len(data) > MAX_INDEX:
            raise ValueError('Описание сборки слишком большое')
        manifest = validate_manifest(json.loads(data))
        atomic_json(cache, manifest)
        return manifest
    except (URLError, OSError, TimeoutError) as exc:
        try:
            manifest = validate_manifest(json.loads(cache.read_text(encoding='utf-8')))
        except (OSError, ValueError, TypeError):
            raise RuntimeError('Не удалось получить SoloCraft. Проверь интернет и повтори запуск.') from exc
        status('GitHub недоступен. Использую сохранённую сборку SoloCraft.')
        return manifest


def cached_archive(root, manifest, status=lambda text: None, progress=lambda value: None):
    item = manifest['pack']
    path = inside(root, 'downloads/' + item['sha256'] + '.mrpack')
    if matches(path, item):
        return path
    path.parent.mkdir(parents=True, exist_ok=True)
    status('Загрузка описания и настроек SoloCraft…')
    with tempfile.TemporaryDirectory(prefix='.mrpack-', dir=path.parent) as temporary:
        staged = Path(temporary) / 'pack.mrpack'
        download(item, staged, status, progress)
        staged.replace(path)
    return path


def read_pack(archive, manifest):
    """Read index + client overrides without ever extracting arbitrary paths."""
    entries = {}
    with zipfile.ZipFile(archive) as pack:
        infos = pack.infolist()
        if len(infos) > 20000 or sum(i.file_size for i in infos) > MAX_TOTAL:
            raise ValueError('Архив SoloCraft слишком большой')
        names = set()
        for info in infos:
            name = info.orig_filename
            if name.casefold() in names or '\\' in name or '\x00' in name or stat.S_ISLNK(info.external_attr >> 16):
                raise ValueError('Небезопасный или повторяющийся путь в архиве')
            names.add(name.casefold())
            checked_size(info.file_size)
        index_info = pack.getinfo('modrinth.index.json')
        checked_size(index_info.file_size, MAX_INDEX)
        index = json.loads(pack.read(index_info))
        if (index.get('formatVersion') != 1 or index.get('game') != 'minecraft'
                or index.get('versionId') != manifest['pack']['version']
                or index.get('dependencies') != {'minecraft': MINECRAFT, 'forge': FORGE}):
            raise ValueError('Версия игры или Forge не соответствует SoloCraft')
        files = index.get('files')
        if not isinstance(files, list) or not 1 <= len(files) <= 1000:
            raise ValueError('Некорректный список модов SoloCraft')
        for file in files:
            name = safe_path(file['path'])
            key = name.casefold()
            if key in entries:
                raise ValueError('Повторяющийся файл SoloCraft')
            environment = file.get('env', {}).get('client', 'required')
            if environment not in {'required', 'optional', 'unsupported'}:
                raise ValueError('Некорректная среда мода')
            if environment == 'unsupported':
                continue
            urls = file.get('downloads')
            if not isinstance(urls, list) or not 1 <= len(urls) <= 10:
                raise ValueError('Не указан адрес мода')
            entries[key] = {'path': name, 'size': checked_size(file['fileSize']),
                            'algorithm': 'sha512', 'hash': checked_hash(file['hashes']['sha512'], 'sha512'),
                            'urls': [cdn_url(url) for url in urls]}
        for prefix in ('overrides/', 'client-overrides/'):
            for info in infos:
                if not info.orig_filename.startswith(prefix) or info.is_dir():
                    continue
                name = safe_path(info.orig_filename[len(prefix):])
                with pack.open(info) as stream:
                    digest = hashlib.file_digest(stream, 'sha256').hexdigest()
                entries[name.casefold()] = {'path': name, 'size': info.file_size, 'algorithm': 'sha256',
                                           'hash': digest, 'member': info.orig_filename}
    if sum(e['size'] for e in entries.values()) > MAX_TOTAL:
        raise ValueError('Сборка SoloCraft слишком большая')
    # A file cannot also be another file's parent directory, including on Windows.
    for key in entries:
        if any(str(p) in entries for p in PurePosixPath(key).parents):
            raise ValueError('Конфликт папок и файлов SoloCraft')
    return list(entries.values())


def same_file(path, entry):
    if not path.is_file() or path.stat().st_size != entry['size']:
        return False
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, entry['algorithm']).hexdigest() == entry['hash']


def previous_entries(profile):
    try:
        data = json.loads((profile / 'installed-pack.json').read_text(encoding='utf-8'))
        entries = data['files']
        for entry in entries:
            safe_path(entry['path'])
            if entry['algorithm'] not in {'sha256', 'sha512'}:
                raise ValueError('Invalid hash algorithm')
            checked_hash(entry['hash'], entry['algorithm'])
            checked_size(entry['size'])
        return {e['path'].casefold(): e for e in entries}
    except FileNotFoundError:
        return {}
    except (ValueError, TypeError, KeyError) as exc:
        raise RuntimeError('Повреждён installed-pack.json в папке SoloCraft. Сохрани его и убери из папки перед восстановлением.') from exc


def changed_entries(game, entries, previous, repair=False):
    changed = []
    for entry in entries:
        path = inside(game, entry['path'])
        if path.exists() and not path.is_file():
            raise RuntimeError('Папка вместо файла: ' + entry['path'])
        # The pack supplies defaults once. Minecraft and the player own options.txt.
        if path.is_file() and entry['path'] == 'options.txt':
            continue
        mutable = entry['path'].startswith(('config/', 'defaultconfigs/'))
        old = previous.get(entry['path'].casefold())
        if path.is_file() and mutable and not repair and old and old['hash'] == entry['hash']:
            continue
        if not same_file(path, entry):
            changed.append(entry)
    return changed


def download_mod(entry, target):
    last_error = None
    for url in entry['urls']:
        try:
            request = Request(url, headers={'User-Agent': 'SDOcraft/' + VERSION})
            with urlopen(request, timeout=45, context=CONTEXT) as response, target.open('wb') as output:
                cdn_url(response.geturl())
                size = 0
                digest = hashlib.new(entry['algorithm'])
                while chunk := response.read(256 * 1024):
                    size += len(chunk)
                    if size > entry['size']:
                        raise RuntimeError('Неверный размер: ' + entry['path'])
                    output.write(chunk)
                    digest.update(chunk)
            if size != entry['size'] or digest.hexdigest() != entry['hash']:
                raise RuntimeError('Ошибка проверки файла: ' + entry['path'])
            return
        except (OSError, URLError, ValueError, RuntimeError) as exc:
            target.unlink(missing_ok=True)
            last_error = exc
    raise RuntimeError('Не удалось скачать ' + entry['path'] + ': ' + str(last_error)) from last_error


def sync_photo(root, manifest):
    item = manifest['photo']
    target = inside(root, item['path'])
    if matches(target, item):
        return 0
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.photo-', dir=target.parent) as temporary:
        staged = Path(temporary) / 'photo.webp'
        download(item, staged)
        staged.replace(target)
    return 1


def check_files(root, manifest):
    validate_manifest(manifest)
    archive = cached_archive(root, manifest)
    entries = read_pack(archive, manifest)
    profile = inside(root, PROFILE)
    game = inside(profile, 'minecraft')
    return [e['path'] for e in changed_entries(game, entries, previous_entries(profile))]


def sync_files(root, manifest, status=lambda text: None, progress=lambda value: None, photo_only=False, repair=False):
    validate_manifest(manifest)
    root.mkdir(parents=True, exist_ok=True)
    if photo_only:
        return sync_photo(root, manifest)
    archive = cached_archive(root, manifest, status, progress)
    entries = read_pack(archive, manifest)
    profile = inside(root, PROFILE)
    profile.mkdir(parents=True, exist_ok=True)
    game = inside(profile, 'minecraft')
    status('Проверка модов и настроек SoloCraft…')
    previous = previous_entries(profile)
    changed = changed_entries(game, entries, previous, repair)
    current = {e['path'].casefold() for e in entries}
    obsolete = [e for key, e in previous.items() if key not in current and e['path'] != 'options.txt']
    backups = inside(profile, 'backups/' + uuid.uuid4().hex)
    changes = []
    with tempfile.TemporaryDirectory(prefix='.update-', dir=profile) as temporary:
        staging = Path(temporary)
        with zipfile.ZipFile(archive) as pack:
            for i, entry in enumerate(changed):
                if 'member' in entry:
                    with pack.open(entry['member']) as source, (staging / str(i)).open('wb') as target:
                        shutil.copyfileobj(source, target)
                    if not same_file(staging / str(i), entry):
                        raise RuntimeError('Повреждён файл в архиве: ' + entry['path'])
        remote = [(i, e) for i, e in enumerate(changed) if 'urls' in e]
        if remote:
            status(f'Загрузка SoloCraft: файлов {len(remote)}. Дождись завершения…')
        with ThreadPoolExecutor(max_workers=6) as pool:
            futures = {pool.submit(download_mod, e, staging / str(i)): e for i, e in remote}
            for done, future in enumerate(as_completed(futures), 1):
                future.result()
                status(f'Загрузка SoloCraft {done}/{len(remote)}: {Path(futures[future]["path"]).name}')
                progress(done * 100 / max(len(remote), 1))
        try:
            for i, entry in enumerate(changed + obsolete):
                target = inside(game, entry['path'])
                replacement = staging / str(i) if i < len(changed) else None
                if replacement is None and not target.exists():
                    continue
                backup = None
                if target.exists():
                    backup = inside(backups, entry['path'])
                    backup.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(target, backup)
                changes.append((target, backup))
                target.parent.mkdir(parents=True, exist_ok=True)
                if replacement is None:
                    target.unlink()
                else:
                    replacement.replace(target)
            atomic_json(inside(profile, 'installed-pack.json'), {'pack': manifest['pack'], 'files': entries})
        except Exception:
            for target, backup in reversed(changes):
                if backup is not None:
                    shutil.copy2(backup, target)
                else:
                    target.unlink(missing_ok=True)
            raise
    # An unavailable decorative image must not prevent playing an installed pack.
    try:
        sync_photo(root, manifest)
    except (OSError, URLError, RuntimeError):
        status('Фото временно недоступно. SoloCraft установлена.')
    return len(changed)
