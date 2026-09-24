"""Owner-controlled launcher configuration. Not editable in the player UI."""
import os
from pathlib import Path

VERSION = '1.3.0'
REPOSITORY = 'JamesNames/SDOcraft'
MINECRAFT = '1.20.1'
FORGE = '47.4.0'
SERVER = '93.119.104.217:25587'
DATA = Path(os.environ.get('APPDATA', str(Path.home()))) / 'SDOcraft'
GAME = DATA / 'minecraft'
MANIFEST_URL = f'https://github.com/{REPOSITORY}/releases/latest/download/modpack.json'
PHOTO = DATA / 'assets' / 'squad.webp'
