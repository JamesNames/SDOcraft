"""Build release assets from an owner's client mod folder (Python 3.12)."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys
import tomllib
from urllib.parse import quote
import zipfile
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from config import MINECRAFT, FORGE, REPOSITORY, VERSION


def prepare(mods, photo, output, tag):
    files = sorted(mods.glob('*.jar'))
    if not files:
        raise ValueError('Не найдены моды .jar')
    if output.exists() and any(output.iterdir()):
        raise ValueError('Папка результата должна быть пустой')
    output.mkdir(parents=True, exist_ok=True)
    manifest = {'schema': 1, 'version': tag, 'minecraft': MINECRAFT, 'forge': FORGE, 'files': []}
    for index, file in enumerate([*files, photo], 1):
        is_mod = index <= len(files)
        asset = f'mod-{index:02d}.jar' if is_mod else 'squad.webp'
        path = 'mods/' + file.name if is_mod else 'assets/squad.webp'
        ids = []
        if is_mod:
            with zipfile.ZipFile(file) as jar:
                if 'META-INF/mods.toml' in jar.namelist():
                    data = tomllib.loads(jar.read('META-INF/mods.toml').decode('utf-8'))
                    ids = [item['modId'] for item in data.get('mods', [])]
        shutil.copy2(file, output / asset)
        with file.open('rb') as stream:
            sha = hashlib.file_digest(stream, 'sha256').hexdigest()
        manifest['files'].append({'path': path, 'size': file.stat().st_size, 'sha256': sha,
                                  'url': f'https://github.com/{REPOSITORY}/releases/download/{quote(tag, safe="")}/{asset}',
                                  'mod_ids': ids})
    (output / 'modpack.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'Создано файлов: {len(manifest["files"])} + modpack.json. Папка: {output}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--mods', type=Path, required=True)
    parser.add_argument('--photo', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--tag', default='v' + VERSION)
    args = parser.parse_args()
    prepare(args.mods, args.photo, args.output, args.tag)
