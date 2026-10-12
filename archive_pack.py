"""Install the owner's checked ChaosZProject archive in an isolated profile."""
from html.parser import HTMLParser
from http.cookiejar import CookieJar
import hashlib
import json
import os
from pathlib import Path, PurePosixPath, PureWindowsPath
import re
import shutil
import subprocess
import tempfile
from urllib.error import URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import Request, build_opener, HTTPSHandler, HTTPCookieProcessor
import uuid

import rarfile
from config import (BUNDLED, FORGE, MINECRAFT, PROFILE, PACK_MANIFEST_URL,
                    PACK_DRIVE_ID, PACK_ROOT, REPOSITORY, VERSION)
from modrinth_pack import inside, sync_photo
from updater import CONTEXT, atomic_json, matches

MAX_JSON = 4 * 1024**2
MAX_FILE = 1024**3
MAX_ARCHIVE = 4 * 1024**3
MAX_TOTAL = 12 * 1024**3
FOLDERS = {'mods', 'config', 'defaultconfigs', 'resourcepacks', 'shaderpacks',
           'kubejs', 'tacz', 'data', 'keybinding presets'}
ROOT_FILES = {'options.txt', 'emi.json', 'rhino.local.properties'}
MUTABLE = ('config/', 'defaultconfigs/', 'keybinding presets/')
RESERVED = {'con', 'prn', 'aux', 'nul', *(f'com{i}' for i in range(1, 10)),
            *(f'lpt{i}' for i in range(1, 10))}


def safe_path(value, managed=True):
    if not isinstance(value, str) or not value or len(value) > 230:
        raise ValueError('Некорректный путь в архиве сборки')
    if any(ord(c) < 32 or c in '\\:<>"|?*' for c in value):
        raise ValueError('Недопустимое имя файла в архиве')
    path = PurePosixPath(value)
    if path.as_posix() != value or path.is_absolute() or '..' in path.parts:
        raise ValueError('Путь выходит за пределы папки сборки')
    if any(p.endswith((' ', '.')) or p.split('.')[0].casefold() in RESERVED for p in path.parts):
        raise ValueError('Недопустимое имя файла Windows')
    if managed:
        if value not in ROOT_FILES and (len(path.parts) < 2 or path.parts[0] not in FOLDERS):
            raise ValueError('Неподдерживаемая папка сборки: ' + value)
        if path.parts[0] == 'mods' and (len(path.parts) != 2 or path.suffix != '.jar'):
            raise ValueError('Некорректный файл мода')
    return value


def validate_item(item, maximum=MAX_FILE):
    if (not isinstance(item, dict) or type(item.get('size')) is not int
            or not 0 <= item['size'] <= maximum
            or not re.fullmatch('[a-f0-9]{64}', str(item.get('sha256', '')))):
        raise ValueError('Некорректное описание файла сборки')


def validate_manifest(data):
    if (not isinstance(data, dict) or data.get('schema') != 4
            or data.get('profile') != 'chaosz'):
        raise ValueError('Неподдерживаемое описание ChaosZProject')
    if data.get('minecraft') != MINECRAFT or data.get('forge') != FORGE:
        raise ValueError('Для этой сборки нужен обновлённый SDOcraft.exe.')
    archive = data.get('archive', {})
    validate_item(archive, MAX_ARCHIVE)
    if archive.get('drive_id') != PACK_DRIVE_ID or archive.get('root') != PACK_ROOT:
        raise ValueError('Архив не соответствует сборке ChaosZProject')
    entries = data.get('files')
    if not isinstance(entries, list) or not 1 <= len(entries) <= 20000:
        raise ValueError('Некорректный список файлов сборки')
    names = set()
    for entry in entries:
        validate_item(entry)
        name = safe_path(entry.get('path')).casefold()
        if name in names:
            raise ValueError('Повторяющийся файл сборки')
        names.add(name)
    if sum(e['size'] for e in entries) > MAX_TOTAL:
        raise ValueError('Сборка слишком большая')
    for name in names:
        if any(str(p) in names for p in PurePosixPath(name).parents):
            raise ValueError('Конфликт файла и папки в сборке')
    photo = data.get('photo', {})
    validate_item(photo, 10 * 1024**2)
    url = urlsplit(photo.get('url', ''))
    if (photo.get('path') != 'assets/squad.webp' or url.scheme != 'https'
            or url.netloc != 'github.com' or url.query or url.fragment
            or not url.path.startswith(f'/{REPOSITORY}/releases/download/')):
        raise ValueError('Некорректное оформление лаунчера')
    return data


def fetch_manifest(root, status=lambda text: None):
    # The manifest bundled with the EXE is always a usable first-install fallback.
    from urllib.request import urlopen
    cache = inside(root, 'chaosz-manifest.json')
    try:
        req = Request(PACK_MANIFEST_URL, headers={'User-Agent': 'SDOcraft/' + VERSION})
        with urlopen(req, timeout=15, context=CONTEXT) as response:
            raw = response.read(MAX_JSON + 1)
        if len(raw) > MAX_JSON:
            raise ValueError('Описание сборки слишком большое')
        data = validate_manifest(json.loads(raw))
        atomic_json(cache, data)
        return data
    except (URLError, OSError, TimeoutError):
        try:
            return validate_manifest(json.loads(cache.read_text(encoding='utf-8')))
        except (OSError, ValueError, TypeError):
            status('Использую сборку, закреплённую в этом лаунчере.')
            return validate_manifest(json.loads(
                (BUNDLED / 'release/modpack-chaosz.json').read_text(encoding='utf-8')))


class DownloadForm(HTMLParser):
    def __init__(self):
        super().__init__()
        self.action = None
        self.fields = {}
        self.in_form = False

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'form':
            self.in_form = attrs.get('id') == 'download-form'
            if self.in_form:
                self.action = attrs.get('action')
        if self.in_form and tag == 'input' and attrs.get('type') == 'hidden':
            if attrs.get('name') in {'id', 'export', 'confirm', 'uuid'}:
                self.fields[attrs['name']] = attrs.get('value', '')

    def handle_endtag(self, tag):
        if tag == 'form':
            self.in_form = False


def drive_response(file_id, offset=0):
    opener = build_opener(HTTPSHandler(context=CONTEXT), HTTPCookieProcessor(CookieJar()))
    url = 'https://drive.usercontent.google.com/download?' + urlencode({'id': file_id, 'export': 'download'})
    headers = {'User-Agent': 'SDOcraft/' + VERSION}
    if offset:
        headers['Range'] = f'bytes={offset}-'
    for _ in range(3):
        response = opener.open(Request(url, headers=headers), timeout=60)
        parsed = urlsplit(response.geturl())
        host = parsed.hostname or ''
        if parsed.scheme != 'https' or not (host in {'drive.google.com', 'drive.usercontent.google.com'}
                                          or host.endswith('.googleusercontent.com')):
            response.close()
            raise RuntimeError('Неожиданное перенаправление Google Drive')
        if 'text/html' not in response.headers.get('Content-Type', '').lower():
            return response
        html = response.read(256 * 1024).decode('utf-8', 'replace')
        response.close()
        form = DownloadForm()
        form.feed(html)
        if (form.action != 'https://drive.usercontent.google.com/download'
                or form.fields.get('id') != file_id or form.fields.get('export') != 'download'
                or form.fields.get('confirm') != 't'):
            raise RuntimeError('Google Drive не разрешил скачать архив. Проверь доступ по ссылке; '
                               'если исчерпан лимит скачиваний, повтори позже.')
        url = form.action + '?' + urlencode(form.fields)
    raise RuntimeError('Google Drive не предоставил архив. Повтори попытку позже.')


def cached_archive(root, manifest, status=lambda text: None, progress=lambda value: None):
    item = manifest['archive']
    target = inside(root, 'downloads/' + item['sha256'] + '.rar')
    if matches(target, item):
        return target
    target.parent.mkdir(parents=True, exist_ok=True)
    part = inside(root, 'downloads/' + item['sha256'] + '.rar.part')
    offset = part.stat().st_size if part.is_file() else 0
    if offset >= item['size']:
        if matches(part, item):
            part.replace(target)
            return target
        part.unlink()
        offset = 0
    status('Загрузка ChaosZProject с Google Drive. Архив около 2 ГБ…')
    if shutil.disk_usage(target.parent).free < item['size'] - offset + 256 * 1024**2:
        raise RuntimeError('Недостаточно свободного места для архива сборки.')
    with drive_response(item['drive_id'], offset) as response:
        if response.status == 206:
            value = response.headers.get('Content-Range', '')
            if not re.fullmatch(r'bytes ' + str(offset) + r'-\d+/' + str(item['size']), value):
                raise RuntimeError('Google Drive вернул неверный диапазон архива.')
        elif response.status == 200:
            offset = 0
        else:
            raise RuntimeError('Не удалось получить архив с Google Drive.')
        digest = hashlib.sha256()
        if offset:
            with part.open('rb') as existing:
                while chunk := existing.read(4 * 1024**2):
                    digest.update(chunk)
        size = offset
        with part.open('ab' if offset else 'wb') as output:
            while chunk := response.read(4 * 1024**2):
                size += len(chunk)
                if size > item['size']:
                    raise RuntimeError('Google Drive вернул другой файл.')
                output.write(chunk)
                digest.update(chunk)
                progress(size * 100 / item['size'])
                status(f'Загрузка сборки: {size // 1024**2} / {item["size"] // 1024**2} МБ')
    if size != item['size'] or digest.hexdigest() != item['sha256']:
        part.unlink(missing_ok=True)
        raise RuntimeError('Архив не прошёл проверку. Если файл на Google Drive заменён, обнови лаунчер.')
    part.replace(target)
    return target


def validate_archive(archive, manifest):
    wanted = {PACK_ROOT + '/' + e['path']: e for e in manifest['files']}
    seen = set()
    found = set()
    with rarfile.RarFile(archive, errors='strict') as rar:
        if rar.needs_password():
            raise ValueError('Архив защищён паролем')
        infos = rar.infolist()
        if len(infos) > 30000 or sum(i.file_size for i in infos) > MAX_TOTAL:
            raise ValueError('Архив слишком большой')
        for info in infos:
            name = info.filename.rstrip('/')
            safe_path(name, managed=False)
            if (name.casefold() in seen or info.is_symlink()
                    or getattr(info, 'file_redir', None) or info.file_size > MAX_FILE):
                raise ValueError('Небезопасный или повторяющийся путь в архиве')
            seen.add(name.casefold())
            if name in wanted:
                if not info.is_file() or info.file_size != wanted[name]['size']:
                    raise ValueError('Размер файла в архиве не совпадает: ' + name)
                found.add(name)
    if found != set(wanted):
        raise ValueError('В архиве не хватает файлов сборки')


def extract_selected(archive, entries, staging, status=lambda text: None):
    status('Распаковка модов и ресурсов. Это может занять несколько минут…')
    if os.name == 'nt':
        # All archive paths and links were checked before invoking UnRAR.
        listing = staging / 'files.lst'
        # UnRAR compares include-list masks with native Windows archive names;
        # unlike command-line exclusion masks, it does not normalize '/'.
        listing.write_text(''.join('"' + str(PureWindowsPath(PACK_ROOT, e['path'])) + '"\n'
                                   for e in entries), encoding='utf-16')
        result = subprocess.run([str(BUNDLED / 'tools/unrar/UnRAR.exe'), 'x', '-idq', '-y', '-o+', '-p-', '-scul',
                                 str(archive), '@' + str(listing), str(staging) + os.sep],
                                stdin=subprocess.DEVNULL, capture_output=True,
                                creationflags=subprocess.CREATE_NO_WINDOW)
        if result.returncode:
            raise RuntimeError('Не удалось распаковать RAR (код ' + str(result.returncode) + ').')
    else:
        # Development/integration checks use the system libarchive; Windows
        # ships the same official UnRAR utility checked in CI.
        import libarchive
        selected = {PACK_ROOT + '/' + e['path'] for e in entries}
        with libarchive.file_reader(str(archive)) as source:
            for entry in source:
                if entry.pathname in selected:
                    target = inside(staging, entry.pathname)
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with target.open('wb') as output:
                        for chunk in entry.get_blocks():
                            output.write(chunk)
    for entry in entries:
        if not matches(inside(staging, PACK_ROOT + '/' + entry['path']), entry):
            raise RuntimeError('Проверка распакованного файла не пройдена: ' + entry['path'])


def previous_entries(profile):
    try:
        data = json.loads((profile / 'installed-pack.json').read_text(encoding='utf-8'))
        for entry in data['files']:
            safe_path(entry['path'])
            validate_item(entry)
        return {e['path'].casefold(): e for e in data['files']}
    except FileNotFoundError:
        return {}
    except (ValueError, KeyError, TypeError) as exc:
        raise RuntimeError('Повреждён список установленной сборки ChaosZProject.') from exc


def changed_entries(game, entries, previous, repair=False):
    changed = []
    for entry in entries:
        path = inside(game, entry['path'])
        if path.exists() and not path.is_file():
            raise RuntimeError('Папка вместо файла: ' + entry['path'])
        if path.is_file() and entry['path'] in ROOT_FILES:
            continue
        old = previous.get(entry['path'].casefold())
        if (path.is_file() and entry['path'].startswith(MUTABLE) and not repair
                and old and old['sha256'] == entry['sha256']):
            continue
        if not matches(path, entry):
            changed.append(entry)
    return changed


def check_files(root, manifest):
    validate_manifest(manifest)
    profile = inside(root, PROFILE)
    return [e['path'] for e in changed_entries(inside(profile, 'minecraft'), manifest['files'], previous_entries(profile))]


def sync_files(root, manifest, status=lambda text: None, progress=lambda value: None, photo_only=False, repair=False):
    validate_manifest(manifest)
    root.mkdir(parents=True, exist_ok=True)
    if photo_only:
        return sync_photo(root, manifest)
    profile = inside(root, PROFILE)
    profile.mkdir(parents=True, exist_ok=True)
    game = inside(profile, 'minecraft')
    status('Проверка модов и ресурсов ChaosZProject…')
    previous = previous_entries(profile)
    changed = changed_entries(game, manifest['files'], previous, repair)
    current = {e['path'].casefold() for e in manifest['files']}
    obsolete = [e for k, e in previous.items() if k not in current and e['path'] not in ROOT_FILES]
    if not changed and not obsolete:
        return 0
    if shutil.disk_usage(profile).free < sum(e['size'] for e in changed) + 256 * 1024**2:
        raise RuntimeError('Недостаточно свободного места для распаковки сборки.')
    archive = cached_archive(root, manifest, status, progress) if changed else None
    if archive:
        validate_archive(archive, manifest)
    # Downloading the archive may have consumed the space checked above.
    backup_size = sum(inside(game, e['path']).stat().st_size for e in changed + obsolete
                      if inside(game, e['path']).is_file())
    if shutil.disk_usage(profile).free < sum(e['size'] for e in changed) + backup_size + 256 * 1024**2:
        raise RuntimeError('Недостаточно свободного места для распаковки и резервной копии сборки.')
    backups = inside(profile, 'backups/' + uuid.uuid4().hex)
    changes = []
    with tempfile.TemporaryDirectory(prefix='.update-', dir=profile) as temporary:
        staging = Path(temporary)
        if changed:
            extract_selected(archive, changed, staging, status)
        try:
            for i, entry in enumerate(changed + obsolete):
                target = inside(game, entry['path'])
                replacement = inside(staging, PACK_ROOT + '/' + entry['path']) if i < len(changed) else None
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
                if i % 100 == 0:
                    status(f'Установка файлов: {i}/{len(changed) + len(obsolete)}')
                    progress(i * 100 / max(1, len(changed) + len(obsolete)))
            atomic_json(inside(profile, 'installed-pack.json'), {'archive': manifest['archive'], 'files': manifest['files']})
        except Exception:
            for target, backup in reversed(changes):
                if backup:
                    shutil.copy2(backup, target)
                else:
                    target.unlink(missing_ok=True)
            raise
    try:
        sync_photo(root, manifest)
    except (OSError, URLError, RuntimeError):
        status('Фото временно недоступно. Сборка установлена.')
    return len(changed)
