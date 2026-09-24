"""Apply the server's client defaults and start Minecraft independently."""
from contextlib import contextmanager
import os
from pathlib import Path
import subprocess
import sys
import tempfile

GAME_OPTIONS = {'guiScale': '2', 'lang': 'ru_ru'}


def configure_game(game_directory):
    """Change only the requested options; preserve all other game preferences."""
    game_directory = Path(game_directory)
    game_directory.mkdir(parents=True, exist_ok=True)
    path = game_directory / 'options.txt'
    try:
        with path.open('r', encoding='utf-8', newline='') as file:
            original = file.read()
    except FileNotFoundError:
        original = ''
    newline = '\r\n' if '\r\n' in original else '\n'
    output = []
    seen = set()
    for line in original.splitlines(keepends=True):
        key = line.partition(':')[0]
        if key in GAME_OPTIONS:
            if key not in seen:
                output.append(key + ':' + GAME_OPTIONS[key] + newline)
                seen.add(key)
        else:
            output.append(line)
    for key, value in GAME_OPTIONS.items():
        if key not in seen:
            if output and not output[-1].endswith(('\r', '\n')):
                output.append(newline)
            output.append(key + ':' + value + newline)
    updated = ''.join(output)
    if updated == original:
        return
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', newline='',
                                         dir=game_directory, prefix='.options-', delete=False) as file:
            temporary = Path(file.name)
            file.write(updated)
        temporary.replace(path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


@contextmanager
def external_environment():
    """Keep Java independent of a one-file launcher's temporary libraries."""
    environment = os.environ.copy()
    bundled = getattr(sys, '_MEIPASS', None)
    if not bundled:
        yield environment
        return
    for key in ('LD_LIBRARY_PATH', 'LIBPATH'):
        original = environment.pop(key + '_ORIG', None)
        if original is None:
            environment.pop(key, None)
        else:
            environment[key] = original
    # Hooks may add the bundle directory to PATH; Java must not use it later.
    bundle = os.path.normcase(os.path.abspath(bundled))
    def from_bundle(entry):
        path = os.path.normcase(os.path.abspath(entry.strip('"')))
        return path == bundle or path.startswith(bundle + os.sep)
    environment['PATH'] = os.pathsep.join(p for p in environment.get('PATH', '').split(os.pathsep)
                                        if not from_bundle(p))
    if sys.platform == 'win32':
        import ctypes
        set_dll_directory = ctypes.windll.kernel32.SetDllDirectoryW
        set_dll_directory.argtypes = [ctypes.c_wchar_p]
        set_dll_directory.restype = ctypes.c_int
        if not set_dll_directory(None):
            raise ctypes.WinError()
        try:
            yield environment
        finally:
            set_dll_directory(str(bundled))
    else:
        yield environment


def launch_game(command, game_directory, startup_timeout=2.0):
    """Return after startup, keeping the child and its log alive after our exit."""
    game_directory = Path(game_directory)
    configure_game(game_directory)
    flags = (subprocess.CREATE_NO_WINDOW | subprocess.CREATE_NEW_PROCESS_GROUP) if os.name == 'nt' else 0
    with (game_directory / 'game-output.log').open('w', encoding='utf-8') as log:
        with external_environment() as environment:
            process = subprocess.Popen(command, cwd=game_directory, stdin=subprocess.DEVNULL,
                                       stdout=log, stderr=subprocess.STDOUT, close_fds=True,
                                       creationflags=flags, start_new_session=os.name != 'nt',
                                       env=environment)
    try:
        code = process.wait(timeout=startup_timeout)
    except subprocess.TimeoutExpired:
        return process
    raise RuntimeError(f'Minecraft завершился при запуске (код {code}). '
                       'Пришли game-output.log или logs/latest.log из папки игры.')
