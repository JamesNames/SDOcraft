"""SDOcraft desktop appearance. Game operations are supplied by Launcher."""
from pathlib import Path
import tkinter as tk
from tkinter import ttk
from PIL import Image, ImageTk, ImageOps
from config import PHOTO, ICON, LOGO
import sys

BG = '#101310'
PANEL = '#191e18'
FIELD = '#242c22'
TEXT = '#f1f3e9'
MUTED = '#929d89'
ACCENT = '#c6de73'
BORDER = '#303b2b'
FONT = 'Segoe UI'


def label(parent, text, size=10, color=TEXT, bold=False, bg=None):
    return tk.Label(parent, text=text, bg=bg or parent.cget('bg'), fg=color,
                    font=(FONT, size, 'bold' if bold else 'normal'), anchor='w')


def button(parent, text, command, primary=False, small=False):
    bg, fg = (ACCENT, '#17200f') if primary else (FIELD, TEXT)
    widget = tk.Button(parent, text=text, command=command, bg=bg, fg=fg,
                       activebackground='#d8ed94' if primary else BORDER,
                       activeforeground=fg, disabledforeground='#6e795e',
                       relief='flat', bd=0, highlightthickness=0, cursor='hand2',
                       padx=12, pady=8 if small else 13,
                       font=(FONT, 10 if small else 12, 'bold'))
    def hover(_):
        if str(widget.cget('state')) != 'disabled':
            widget.configure(bg='#d8ed94' if primary else BORDER)
    widget.bind('<Enter>', hover)
    widget.bind('<Leave>', lambda _: widget.configure(bg=bg))
    return widget


class PhotoPanel(tk.Canvas):
    def __init__(self, parent):
        super().__init__(parent, bg='#0b1009', highlightthickness=1, highlightbackground=BORDER, height=100)
        self.original = None
        self.photo = None
        self.pending = None
        self.bind('<Configure>', self.schedule)
        self.load_photo()

    def load_photo(self):
        try:
            with Image.open(PHOTO) as source:
                self.original = source.convert('RGB')
        except (OSError, ValueError):
            pass
        self.schedule(None)

    def schedule(self, _):
        if self.pending is not None:
            self.after_cancel(self.pending)
        self.pending = self.after(70, self.render)

    def render(self):
        self.pending = None
        width, height = self.winfo_width(), self.winfo_height()
        if width < 2 or height < 2:
            return
        self.delete('all')
        if self.original is not None:
            # Preserve the whole team photo: no crop or stretch.
            fitted = ImageOps.contain(self.original, (width-2, height-2), Image.Resampling.LANCZOS)
            self.photo = ImageTk.PhotoImage(fitted)
            self.create_image(width//2, height//2, image=self.photo)
        else:
            self.create_text(width//2, height//2, text='SDOcraft', fill=ACCENT, font=(FONT, 40, 'bold'))
            self.create_text(width//2, height//2+50, text='Фото завантажиться з інтернету', fill=MUTED, font=(FONT, 11))


def build(app):
    root = app.root
    root.configure(bg=BG)
    root.title('SDOcraft • Твоя команда. Твій світ.')
    with Image.open(LOGO) as source:
        app.brand_image = source.convert('RGBA')
    app.window_icons = [ImageTk.PhotoImage(app.brand_image.resize((size, size), Image.Resampling.LANCZOS)) for size in (16, 32, 48, 64)]
    if sys.platform == 'win32':
        root.iconbitmap(default=str(ICON))
    else:
        root.iconphoto(True, *app.window_icons)
    root.geometry('1120x790')
    root.minsize(1040, 720)
    style = ttk.Style(root)
    style.theme_use('clam')
    style.configure('SDO.Horizontal.TProgressbar', troughcolor=FIELD, background=ACCENT,
                    bordercolor=FIELD, lightcolor=ACCENT, darkcolor=ACCENT, borderwidth=0)
    shell = tk.Frame(root, bg=BG)
    shell.pack(fill='both', expand=True)
    sidebar = tk.Frame(shell, bg=PANEL, width=252)
    sidebar.pack(side='left', fill='y')
    sidebar.pack_propagate(False)
    tk.Frame(shell, bg=BORDER, width=1).pack(side='left', fill='y')
    rail = tk.Frame(sidebar, bg=PANEL)
    rail.pack(fill='both', expand=True, padx=22, pady=24)

    brand = tk.Frame(rail, bg=PANEL)
    brand.pack(fill='x', pady=(0, 4))
    app.sidebar_icon = ImageTk.PhotoImage(app.brand_image.resize((36, 36), Image.Resampling.LANCZOS))
    mark = tk.Label(brand, image=app.sidebar_icon, bg=PANEL, bd=0)
    mark.pack(side='left', padx=(0, 10))
    label(brand, 'SDOcraft', 21, bold=True).pack(side='left')
    label(rail, 'ТЕРИТОРІЯ СВОЇХ', 9, MUTED).pack(anchor='w', pady=(3, 21))
    nav = tk.Frame(rail, bg=FIELD, highlightthickness=1, highlightbackground=BORDER)
    nav.pack(fill='x')
    tk.Frame(nav, bg=ACCENT, width=3).pack(side='left', fill='y')
    label(nav, '  /  ГОЛОВНА', 10, ACCENT, True).pack(anchor='w', padx=9, pady=10)
    label(rail, 'ПРОФІЛЬ ГРАВЦЯ', 9, MUTED, True).pack(anchor='w', pady=(24, 10))
    for key, title, variable in [
        ('nickname', 'Нік', app.vars['nickname']),
        ('password', 'Пароль', app.password),
    ]:
        label(rail, title, 10, MUTED).pack(anchor='w', pady=(10, 6))
        outer = tk.Frame(rail, bg=BORDER, padx=1, pady=1)
        outer.pack(fill='x')
        entry = tk.Entry(outer, textvariable=variable, relief='flat', bd=0,
                         bg=FIELD, fg=TEXT, insertbackground=ACCENT,
                         disabledbackground=PANEL, disabledforeground=MUTED,
                         font=(FONT, 11), highlightthickness=0,
                         show='•' if key == 'password' else '')
        entry.pack(fill='x', padx=8, ipady=10)
        entry.bind('<FocusIn>', lambda _, f=outer: f.configure(bg=ACCENT))
        entry.bind('<FocusOut>', lambda _, f=outer: f.configure(bg=BORDER))
        app.widgets.append(entry)
    copy_login = button(rail, 'Скопіювати /login', app.copy_login, small=True)
    copy_login.pack(fill='x', pady=(10, 0))
    note = label(rail, 'Після входу в гру: відкрий чат,\nнатисни Ctrl+V та Enter.\nПароль не зберігається.', 9, MUTED)
    note.configure(justify='left', wraplength=205)
    note.pack(anchor='w', pady=(12, 0))
    tk.Frame(rail, bg=BORDER, height=1).pack(fill='x', pady=23)
    label(rail, 'ТВІЙ ЗАГІН ЧЕКАЄ', 9, ACCENT, True).pack(anchor='w')
    desc = label(rail, 'Збери команду.\nЗайми свою позицію.\nСтвори власну історію.', 10, MUTED)
    desc.configure(justify='left')
    desc.pack(anchor='w', pady=(10, 0))
    footer = tk.Frame(rail, bg=PANEL)
    footer.pack(side='bottom', fill='x', pady=(15, 0))
    tk.Frame(footer, bg=BORDER, height=1).pack(fill='x', pady=(0, 12))
    label(footer, 'SDOCRAFT', 9, ACCENT, True).pack(anchor='w')
    label(footer, 'Вхід за ніком · без Microsoft', 8, MUTED).pack(anchor='w', pady=(5, 0))

    main = tk.Frame(shell, bg=BG)
    main.pack(side='left', fill='both', expand=True, padx=25, pady=24)
    header = tk.Frame(main, bg=BG)
    header.pack(fill='x')
    label(header, 'ГОТОВИЙ ДО ГРИ?', 10, ACCENT, True).pack(side='left')
    label(header, 'SDO  /  MULTIPLAYER', 9, MUTED).pack(side='right')
    title = tk.Frame(main, bg=BG)
    title.pack(fill='x', pady=(8, 3))
    label(title, 'Твоя команда. Твій світ.', 25, bold=True).pack(anchor='w')
    label(main, 'Збирай загін і приєднуйся до SDOcraft.', 11, MUTED).pack(anchor='w', pady=(0, 18))

    hero = tk.Frame(main, bg=PANEL, highlightthickness=1, highlightbackground=BORDER)
    hero.pack(fill='both', expand=True)
    app.hero = PhotoPanel(hero)
    app.hero.pack(fill='both', expand=True)
    caption = tk.Frame(hero, bg=PANEL)
    caption.pack(fill='x', padx=14, pady=10)
    label(caption, 'SDO  /  НАШ ЗАГІН', 9, ACCENT, True).pack(side='left')
    label(caption, 'ЗБІРКА З АВТООНОВЛЕННЯМ', 9, MUTED).pack(side='right')

    tools = tk.Frame(main, bg=BG)
    tools.pack(fill='x', pady=(14, 12))
    for title, command in [
        ('Папка гри', lambda: app.open_folder(app.game_folder)),
        ('Додати моди', app.add_mods),
        ('Перевірити збірку', app.check_mods),
        ('Відновити', lambda: app.start(True)),
    ]:
        b = button(tools, title, command, small=True)
        b.pack(side='left', padx=(0, 7))
        app.widgets.append(b)

    bottom = tk.Frame(main, bg=PANEL, highlightthickness=1, highlightbackground=BORDER)
    bottom.pack(fill='x')
    launch = tk.Frame(bottom, bg=PANEL)
    launch.pack(fill='x', padx=15, pady=13)
    note = tk.Frame(launch, bg=PANEL)
    note.pack(side='left', fill='both', expand=True)
    label(note, 'УСЕ ДЛЯ СТАРТУ', 9, ACCENT, True).pack(anchor='w')
    label(note, 'Гра, Java та моди встановляться автоматично.', 9, MUTED).pack(anchor='w', pady=(4, 0))
    app.play = button(launch, 'ГРАТИ  →', lambda: app.start(False), primary=True)
    app.play.configure(width=13)
    app.play.pack(side='right', padx=(12, 0))
    app.widgets.append(app.play)
    app.progress = ttk.Progressbar(bottom, style='SDO.Horizontal.TProgressbar', maximum=100)
    app.progress.pack(fill='x', padx=15, pady=(0, 10))
    app.status = tk.StringVar(value='Готово до запуску. Перше встановлення потребує інтернету.')
    status = tk.Label(main, textvariable=app.status, bg=BG, fg=MUTED,
                      font=(FONT, 9), anchor='w', justify='left', wraplength=720)
    status.pack(fill='x', pady=(10, 0))
    # Keep verbose installation output available without covering the photo.
    log_frame = tk.Frame(main, bg=BG)
    app.log = tk.Text(log_frame, height=5, bg=PANEL, fg=MUTED, relief='flat',
                      font=('Consolas', 9), state='disabled', wrap='word')
    scroll = ttk.Scrollbar(log_frame, orient='vertical', command=app.log.yview)
    app.log.configure(yscrollcommand=scroll.set)
    scroll.pack(side='right', fill='y')
    app.log.pack(fill='both', expand=True)
    def toggle():
        if log_frame.winfo_manager():
            log_frame.pack_forget()
            logs.configure(text='+ Журнал запуску')
        else:
            log_frame.pack(fill='x', pady=(5, 0))
            logs.configure(text='− Сховати журнал')
    logs = tk.Button(main, text='+ Журнал запуску', command=toggle, bg=BG, fg=MUTED,
                     activebackground=BG, activeforeground=ACCENT, bd=0,
                     cursor='hand2', font=(FONT, 9), anchor='w', highlightthickness=0)
    logs.pack(fill='x', pady=(6, 0))
