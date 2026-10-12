"""Windows-compatible Java paths and concrete installation checks."""
import os
from pathlib import Path
import re
import subprocess
import zipfile
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
