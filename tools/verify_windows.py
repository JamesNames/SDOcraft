"""Exercise the actual Windows installer before publishing an executable."""
import json
import os
from pathlib import Path
import sys
import time
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import archive_pack as pack
from config import FORGE, MINECRAFT, PROFILE, SERVER
from game_launch import configure_game, fix_forge_command
from server_list import ensure_server


def main():
    if os.name != 'nt':
        raise RuntimeError('This gate must run on Windows with the bundled UnRAR.')
    root = Path(os.environ['RUNNER_TEMP']) / 'SDOcraft-ci-проверка'
    report = Path('verification')
    report.mkdir(exist_ok=True)
    manifest = json.loads(Path('release/modpack-chaosz.json').read_text(encoding='utf-8'))
    last = 0

    def status(message):
        nonlocal last
        if time.monotonic() - last > 5:
            print(message, flush=True)
            last = time.monotonic()

    installed = pack.sync_files(root, manifest, status)
    assert installed == len(manifest['files'])
    assert pack.check_files(root, manifest) == []
    assert pack.sync_files(root, manifest, status) == 0
    game = root / PROFILE / 'minecraft'
    assert len(list((game / 'mods').glob('*.jar'))) == 285
    assert not (game / 'minecraftinstance.json').exists()
    assert not (game / 'local').exists()
    configure_game(game)
    ensure_server(game)
    options_text = (game / 'options.txt').read_text(encoding='utf-8')
    assert 'lang:ru_ru' in options_text and 'guiScale:2' in options_text
    print('PASS: real Drive download, SHA-256, UnRAR, 8425 files, repeated install, settings', flush=True)

    import launcher
    import appearance
    import tkinter as tk
    from PIL import ImageGrab
    with patch.object(launcher, 'ROOT', root), patch.object(launcher, 'GAME', game), \
            patch.object(appearance, 'PHOTO', root / 'assets/squad.webp'), \
            patch.object(launcher.Launcher, 'bootstrap'):
        window = tk.Tk()
        app = launcher.Launcher(window)
        for size in ('1040x720', '1120x790'):
            window.geometry(size)
            window.update()
            time.sleep(0.2)
            window.update()
            for widget in app.widgets:
                assert widget.winfo_ismapped(), f'Hidden control: {widget}'
                x = widget.winfo_rootx() - window.winfo_rootx()
                y = widget.winfo_rooty() - window.winfo_rooty()
                assert x >= 0 and x + widget.winfo_width() <= window.winfo_width(), str(widget)
                assert y >= 0 and y + widget.winfo_height() <= window.winfo_height(), str(widget)
            ImageGrab.grab(bbox=(window.winfo_rootx(), window.winfo_rooty(),
                                window.winfo_rootx() + window.winfo_width(),
                                window.winfo_rooty() + window.winfo_height())).save(report / (size + '.png'))
        window.destroy()
    print('PASS: visible Windows UI controls at both supported sizes', flush=True)

    import minecraft_launcher_lib as mcl
    callback = {'setStatus': status}
    print('Installing the Minecraft Java runtime and Forge...', flush=True)
    mcl.runtime.install_jvm_runtime('java-runtime-gamma', game, callback=callback)
    java = mcl.runtime.get_executable_path('java-runtime-gamma', game)
    assert java and Path(java).is_file()
    forge = MINECRAFT + '-' + FORGE
    mcl.forge.install_forge_version(forge, game, callback=callback, java=java)
    version = mcl.forge.forge_to_installed_version(forge)
    command = fix_forge_command(mcl.command.get_minecraft_command(version, game, {
        'username': 'SDOcraftTest', 'uuid': launcher.offline_uuid('SDOcraftTest'),
        'token': '0', 'executablePath': java, 'gameDirectory': str(game),
        'jvmArguments': ['-Xms1G', '-Xmx8G'], 'quickPlayMultiplayer': SERVER,
    }))
    assert '-Xmx8G' in command
    assert any(c.startswith('-DignoreList=') and '1.20.1.jar' in c for c in command)
    assert SERVER in command
    print('PASS: Java 17 and Forge installed; launch command includes RAM, server and module fix', flush=True)
    (report / 'result.txt').write_text(
        'PASS: original Google Drive archive, SHA-256, Windows UnRAR, all 8425 files, 285 mods, '
        'idempotent installation, language, GUI scale, server list, UI, Java and Forge installation, launch command.\n'
        'Minecraft gameplay and multiplayer connection require a player PC.\n', encoding='utf-8')


if __name__ == '__main__':
    main()
