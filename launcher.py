"""SDOcraft — Minecraft Forge 1.20.1 desktop launcher."""
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import shutil
import subprocess
import sys
import threading
import tkinter as tk
from tkinter import ttk, messagebox, filedialog
import uuid
from config import DATA, GAME, SERVER, FORGE, MINECRAFT, PHOTO, VERSION
from updater import fetch_manifest, sync_files, check_files
from gunpacks import check_gunpacks, sync_gunpacks

ROOT = DATA
DEFAULTS = {'nickname': 'Player', 'ram': '4', 'server': SERVER, 'forge': FORGE}

BG, CARD, TEXT, MUTED, RED = '#101116', '#1b1d25', '#f5f5f7', '#989daa', '#ef4444'



def offline_uuid(name):
    return uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:' + name).encode('utf-8')).digest(), version=3).hex


def russian_status(value):
    text = str(value)
    translations = {
        'Download Libraries': 'Загрузка библиотек',
        'Download Assets': 'Загрузка ресурсов Minecraft',
        'Install java runtime': 'Установка Java',
        'Installation complete': 'Установка завершена',
    }
    if text in translations:
        return translations[text]
    for original, translated in [('Download ', 'Загрузка '), ('Running processor ', 'Установка компонента ')]:
        if text.startswith(original):
            return translated + text[len(original):]
    return text


def validate(settings):
    if not re.fullmatch(r'[A-Za-z0-9_]{3,16}', settings['nickname']):
        raise ValueError('Ник: 3–16 латинских букв, цифр или _.')
    ram = int(settings['ram'])
    if not 2 <= ram <= 32:
        raise ValueError('Оперативная память: от 2 до 32 ГБ.')
    if not re.fullmatch(r'47\.\d+\.\d+', settings['forge']):
        raise ValueError('Forge для 1.20.1 имеет формат 47.x.x.')
    if tuple(map(int, settings['forge'].split('.'))) < (47, 4, 0):
        raise ValueError('DragonRise из этой сборки требует Forge 47.4.0 или новее.')
    server = settings['server']
    if server and (re.search(r'\s|[/\\]', server) or len(server) > 255):
        raise ValueError('Введи адрес сервера без https:// и пробелов.')
    return settings


def save_settings(settings):
    ROOT.mkdir(parents=True, exist_ok=True)
    temp = ROOT / 'settings.tmp'
    persisted = {key: settings[key] for key in ('nickname', 'ram')}
    temp.write_text(json.dumps(persisted, ensure_ascii=False, indent=2), encoding='utf-8')
    temp.replace(ROOT / 'settings.json')


class Launcher:
    def __init__(self, root):
        self.root = root
        self.events = queue.Queue()
        self.busy = False
        self.widgets = []
        ROOT.mkdir(parents=True, exist_ok=True)
        (GAME / 'mods').mkdir(parents=True, exist_ok=True)
        settings = DEFAULTS.copy()
        try:
            loaded = json.loads((ROOT / 'settings.json').read_text(encoding='utf-8'))
            if isinstance(loaded, dict):
                settings.update({k: str(loaded[k]) for k in ('nickname', 'ram') if k in loaded})
        except (OSError, ValueError):
            pass
        settings['server'] = DEFAULTS['server']
        settings['forge'] = DEFAULTS['forge']
        self.vars = {k: tk.StringVar(value=v) for k, v in settings.items()}
        self.password = tk.StringVar()
        root.title('SDOcraft • Launcher')
        root.geometry('940x730')
        root.minsize(940, 730)
        root.configure(bg=BG)
        root.protocol('WM_DELETE_WINDOW', self.close)
        style = ttk.Style(root)
        style.theme_use('clam')
        style.configure('SDO.Horizontal.TProgressbar', troughcolor=CARD, background=RED, borderwidth=0)
        self.photo_path = PHOTO
        self.game_folder = GAME
        self.build()
        root.after(100, self.poll)
        self.bootstrap()

    def bootstrap(self):
        self.busy = True
        for widget in self.widgets:
            widget.configure(state='disabled')
        def run():
            try:
                manifest = fetch_manifest(ROOT, lambda text: self.events.put(('status', text)))
                self.events.put(('status', 'Загрузка оформления…'))
                sync_files(ROOT, manifest, photo_only=True)
                self.events.put(('photo', None))
                self.events.put(('status', 'Готово. Нажми «Играть», чтобы проверить и загрузить сборку.'))
            except Exception:
                self.events.put(('status', 'Фото пока недоступно. Нажми «Играть», чтобы повторить загрузку.'))
            finally:
                self.events.put(('done', None))
        threading.Thread(target=run, daemon=True).start()

    def build(self):
        import appearance
        appearance.build(self)

    def copy_login(self):
        password = self.password.get()
        if not password:
            messagebox.showinfo('Пароль сервера', 'Введи пароль для /login на сервере.')
            return
        if any(char.isspace() or ord(char) < 32 for char in password):
            messagebox.showerror('Пароль сервера', 'Для команды /login пароль должен быть без пробелов и переносов строки.')
            return
        self.root.clipboard_clear()
        self.root.clipboard_append('/login ' + password)
        self.status.set('Команда /login скопирована. В игре открой чат → Ctrl+V → Enter.')

    def open_folder(self, path):
        path.mkdir(parents=True, exist_ok=True)
        if os.name == 'nt':
            os.startfile(str(path))
        else:
            subprocess.Popen(['open' if sys.platform == 'darwin' else 'xdg-open', str(path)])

    def add_mods(self):
        files = filedialog.askopenfilenames(title='Клиентские моды Forge 1.20.1', filetypes=[('Моды Java', '*.jar')])
        try:
            for f in files:
                src = Path(f)
                dst = GAME / 'mods' / src.name
                if src.resolve() == dst.resolve():
                    continue
                if dst.exists() and not messagebox.askyesno('Заменить мод?', src.name):
                    continue
                shutil.copy2(src, dst)
            if files:
                self.events.put(('status', 'Моды добавлены. Не забудь установить их зависимости.'))
        except OSError as exc:
            messagebox.showerror('Не удалось добавить мод', str(exc))

    def check_mods(self):
        self.busy = True
        for widget in self.widgets:
            widget.configure(state='disabled')
        def run():
            try:
                manifest = fetch_manifest(ROOT, lambda text: self.events.put(('status', text)))
                missing = [item['path'] for item in check_files(ROOT, manifest)]
                missing.extend(check_gunpacks(ROOT, manifest))
                if missing:
                    self.events.put(('info', 'Нужно установить или восстановить:\n\n' + '\n'.join(missing) +
                                     '\n\nНажми «Играть» или «Восстановить»: файлы загрузятся из интернета.'))
                else:
                    self.events.put(('info', 'Файлы сборки и ресурсы оружейных пакетов проверены.'))
            except Exception as exc:
                self.events.put(('error', str(exc)))
            finally:
                self.events.put(('done', None))
        threading.Thread(target=run, daemon=True).start()

    def start(self, repair):
        if self.busy:
            return
        try:
            settings = validate({k: v.get().strip() for k, v in self.vars.items()})
            save_settings(settings)
        except (ValueError, OSError) as exc:
            messagebox.showerror('Настройки', str(exc))
            return
        self.busy = True
        for w in self.widgets:
            w.configure(state='disabled')
        self.progress['value'] = 0
        threading.Thread(target=self.worker, args=(settings, repair), daemon=True).start()

    def worker(self, settings, repair):
        try:
            import minecraft_launcher_lib as mcl
            callback = {'setStatus': lambda s: self.events.put(('status', russian_status(s))),
                        'setProgress': lambda v: self.events.put(('progress', v)),
                        'setMax': lambda v: self.events.put(('max', max(v, 1)))}
            manifest = fetch_manifest(ROOT, callback['setStatus'])
            callback['setMax'](100)
            count = sync_files(ROOT, manifest, callback['setStatus'], callback['setProgress'])
            sync_gunpacks(ROOT, manifest, callback['setStatus'])
            self.events.put(('photo', None))
            callback['setStatus'](f'Сборка готова. Загружено файлов: {count}.')
            forge = '1.20.1-' + settings['forge']
            version = mcl.forge.forge_to_installed_version(forge)
            marker = GAME / ('sdocraft-' + forge + '.ready')
            java = mcl.runtime.get_executable_path('java-runtime-gamma', GAME)
            if not java or not Path(java).is_file():
                callback['setStatus']('Установка Java 17…')
                mcl.runtime.install_jvm_runtime('java-runtime-gamma', GAME, callback=callback)
                java = mcl.runtime.get_executable_path('java-runtime-gamma', GAME)
            if not java:
                raise RuntimeError('Не удалось установить Java 17. Проверь интернет и повтори.')
            if repair or not marker.exists() or not (GAME / 'versions' / version / (version + '.json')).is_file():
                marker.unlink(missing_ok=True)
                callback['setStatus']('Установка Minecraft и Forge. Это может занять несколько минут…')
                mcl.forge.install_forge_version(forge, GAME, callback=callback, java=java)
                marker.write_text('complete', encoding='utf-8')
            if repair:
                callback['setStatus']('Установка завершена. Можно играть!')
                return
            options = {'username': settings['nickname'], 'uuid': offline_uuid(settings['nickname']),
                       'token': '0', 'executablePath': java, 'launcherName': 'SDOcraft', 'launcherVersion': VERSION,
                       'gameDirectory': str(GAME), 'jvmArguments': ['-Xms1G', '-Xmx' + settings['ram'] + 'G']}
            if settings['server']:
                options['quickPlayMultiplayer'] = settings['server']
            command = mcl.command.get_minecraft_command(version, GAME, options)
            callback['setStatus']('Игра запущена. Журнал: minecraft/game-output.log')
            with (GAME / 'game-output.log').open('w', encoding='utf-8') as log:
                process = subprocess.Popen(command, cwd=GAME, stdout=log, stderr=subprocess.STDOUT,
                                           creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
                code = process.wait()
            if code:
                raise RuntimeError(f'Игра завершилась с кодом {code}. Пришли game-output.log или logs/latest.log из папки игры.')
            callback['setStatus']('Игра закрыта. Можно запустить снова.')
        except Exception as exc:
            self.events.put(('error', str(exc)))
        finally:
            self.events.put(('done', None))

    def poll(self):
        for _ in range(200):
            try:
                kind, value = self.events.get_nowait()
            except queue.Empty:
                break
            if kind in ('status', 'error'):
                self.status.set(value)
                self.log.configure(state='normal')
                self.log.insert('end', value + '\n')
                self.log.see('end')
                self.log.configure(state='disabled')
                if kind == 'error':
                    messagebox.showerror('SDOcraft', value)
            elif kind == 'photo':
                self.hero.load_photo()
            elif kind == 'info':
                messagebox.showinfo('Моды сборки', value)
            elif kind == 'progress':
                self.progress['value'] = value
            elif kind == 'max':
                self.progress['maximum'] = value
                self.progress['value'] = 0
            elif kind == 'done':
                self.busy = False
                for w in self.widgets:
                    w.configure(state='normal')
        self.root.after(100, self.poll)

    def close(self):
        if self.busy:
            messagebox.showinfo('SDOcraft', 'Дождись завершения установки или закрой игру перед выходом из лаунчера.')
            return
        self.root.destroy()


if __name__ == '__main__':
    if sys.platform == 'win32':
        import ctypes
        ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID('SDOcraft.Launcher')
    window = tk.Tk()
    Launcher(window)
    window.mainloop()
