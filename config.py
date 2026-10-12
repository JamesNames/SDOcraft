"""Owner-controlled launcher configuration. Not editable in the player UI."""
import os
import sys
from pathlib import Path

VERSION = '2.0.0'
REPOSITORY = 'JamesNames/SDOcraft'
MINECRAFT = '1.20.1'
FORGE = '47.4.6'
SERVER = '93.119.104.217:25587'
DATA = Path(os.environ.get('APPDATA', str(Path.home()))) / 'SDOcraft'
PROFILE = Path('profiles') / 'chaosz'
GAME = DATA / PROFILE / 'minecraft'
MANIFEST_URL = f'https://github.com/{REPOSITORY}/releases/latest/download/modpack.json'
SOLOCRAFT_MANIFEST_URL = f'https://github.com/{REPOSITORY}/releases/latest/download/modpack-solocraft.json'
PACK_DRIVE_ID = '16SJGytzi1q08fMmYtbwkAET03Oq9Mewj'
PACK_ROOT = 'ChaosZProject By BlackRift Studios'
PACK_MANIFEST_URL = f'https://github.com/{REPOSITORY}/releases/latest/download/modpack-chaosz.json'
PHOTO = DATA / 'assets' / 'squad.webp'

BUNDLED = Path(getattr(sys, '_MEIPASS', Path(__file__).resolve().parent))
ICON = BUNDLED / 'branding' / 'sdocraft.ico'
LOGO = BUNDLED / 'branding' / 'sdocraft.png'
