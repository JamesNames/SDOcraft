"""Windows-compatible Java paths and concrete installation checks."""
import os
import csv
import json
from pathlib import Path
import re
import subprocess
import zipfile
import uuid
from game_launch import external_environment


def java_game_directory(directory):
    """Use the existing Windows short alias; never move the user's game."""
    directory = Path(directory).absolute()
    directory.mkdir(parents=True, exist_ok=True)
    if os.name != 'nt':
        return directory
    import ctypes
    short_path = ctypes.windll.kernel32.GetShortPathNameW
    short_path.argtypes = [ctypes.c_wchar_p, ctypes.c_wchar_p, ctypes.c_uint]
    short_path.restype = ctypes.c_uint
    size = short_path(str(directory), None, 0)
    if size:
        buffer = ctypes.create_unicode_buffer(size)
        if short_path(str(directory), buffer, size):
            return Path(buffer.value)
    return directory


def _private_windows_directory(path):
    """Create an ASCII runtime folder accessible only to its owner and admins."""
    import ctypes
    from ctypes import wintypes
    windows = Path(os.environ['SystemRoot']) / 'System32'
    identity = subprocess.check_output([str(windows / 'whoami.exe'), '/user', '/fo', 'csv', '/nh'],
                                       creationflags=subprocess.CREATE_NO_WINDOW).decode('utf-8', 'replace')
    sid = next(csv.reader(identity.strip().splitlines()))[-1]
    if not re.fullmatch(r'S-1-[0-9-]+', sid):
        raise RuntimeError('Не удалось определить владельца папки Java.')
    advapi = ctypes.WinDLL('advapi32', use_last_error=True)
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    convert = advapi.ConvertStringSecurityDescriptorToSecurityDescriptorW
    convert.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, ctypes.POINTER(ctypes.c_void_p), ctypes.c_void_p]
    convert.restype = wintypes.BOOL
    descriptor = ctypes.c_void_p()
    sddl = f'D:P(A;OICI;FA;;;{sid})(A;OICI;FA;;;SY)(A;OICI;FA;;;BA)'
    if not convert(sddl, 1, ctypes.byref(descriptor), None):
        raise ctypes.WinError(ctypes.get_last_error())
    class Attributes(ctypes.Structure):
        _fields_ = [('length', wintypes.DWORD), ('descriptor', ctypes.c_void_p), ('inherit', wintypes.BOOL)]
    attributes = Attributes(ctypes.sizeof(Attributes), descriptor, False)
    create = kernel.CreateDirectoryW
    create.argtypes = [wintypes.LPCWSTR, ctypes.POINTER(Attributes)]
    create.restype = wintypes.BOOL
    kernel.LocalFree.argtypes = [ctypes.c_void_p]
    kernel.LocalFree.restype = ctypes.c_void_p
    try:
        if not create(str(path), ctypes.byref(attributes)):
            raise ctypes.WinError(ctypes.get_last_error())
    finally:
        kernel.LocalFree(descriptor)


def java_runtime_directory(root, game):
    """Keep Java's executable path ASCII even when NTFS short names are disabled."""
    if os.name != 'nt' or str(game).isascii():
        return game
    from updater import atomic_json
    from modrinth_pack import inside
    candidates = [Path(os.environ.get('ProgramData', r'C:\ProgramData')),
                  Path(os.environ.get('PUBLIC', r'C:\Users\Public'))]
    candidates = [p for p in candidates if p.is_absolute() and str(p).isascii() and p.is_dir()]
    locator = inside(root, 'java-location.json')
    if locator.is_file():
        path = Path(json.loads(locator.read_text(encoding='utf-8'))['path'])
        if (path.parent not in candidates or not re.fullmatch(r'SDOcraft-Java-[a-f0-9]{32}', path.name)
                or path.is_symlink() or path.is_junction()):
            raise RuntimeError('Повреждён путь к Java в настройках лаунчера.')
        if path.is_dir():
            return path
    for parent in candidates:
        path = parent / ('SDOcraft-Java-' + uuid.uuid4().hex)
        try:
            _private_windows_directory(path)
        except OSError:
            continue
        atomic_json(locator, {'path': str(path)})
        return path
    raise RuntimeError('Не удалось создать папку Java без кириллицы. '
                       'Укажи SDOCRAFT_DATA_DIR с латинским путём, например C:/SDOcraft.')


def verify_java(java):
    flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
    with external_environment() as environment:
        result = subprocess.run([str(java), '-version'], stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                                timeout=30, creationflags=flags, env=environment)
    output = result.stdout.decode('utf-8', 'replace')
    if result.returncode or not re.search(r'version "17(?:\.|\")', output):
        raise RuntimeError('Java 17 не запускается. Нажми «Восстановить». '
                           'Если в пути Windows есть кириллица и ошибка повторяется, '
                           'укажи папку с латинским путём через SDOCRAFT_DATA_DIR.\n' + output[-1000:])


def verify_forge_files(game, forge):
    # minecraft-launcher-lib can return after failed Java processors. Check
    # their generated output before writing a success marker or launching.
    client = Path(game) / 'libraries/net/minecraftforge/forge' / forge / ('forge-' + forge + '-client.jar')
    if not client.is_file() or not zipfile.is_zipfile(client):
        raise RuntimeError('Forge не завершил установку: отсутствует подготовленный клиент. '
                           'Нажми «Восстановить», чтобы повторить установку.')


def verify_command_files(command):
    paths = []
    for index, value in enumerate(command):
        if value in ('-cp', '-classpath', '--class-path', '-p', '--module-path'):
            paths.extend(command[index + 1].split(os.pathsep))
        if value.startswith('-DlegacyClassPath=') or value.startswith('--module-path='):
            paths.extend(value.partition('=')[2].split(os.pathsep))
    missing = [p for p in paths if p and not Path(p).is_file()]
    if missing:
        raise RuntimeError('Не хватает библиотек игры. Нажми «Восстановить».\n' + '\n'.join(missing[:3]))
