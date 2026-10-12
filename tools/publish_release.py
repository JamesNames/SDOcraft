"""Publish the prepared manifest and EXE after the Windows build succeeds."""
import json
from pathlib import Path
import subprocess
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from config import VERSION
from archive_pack import validate_manifest


def main():
    path = Path('release/modpack-chaosz.json')
    if not path.is_file():
        print('No release prepared; keeping the Actions artifact only.')
        return
    manifest = validate_manifest(json.loads(path.read_text(encoding='utf-8')))
    tag = 'v' + VERSION
    if manifest.get('version') != tag:
        raise RuntimeError('Version in config.py must match release/modpack-chaosz.json')
    # Published versions are never overwritten by a later push.
    result = subprocess.run(['gh', 'api', f'repos/{{owner}}/{{repo}}/releases/tags/{tag}'],
                            capture_output=True, text=True)
    if result.returncode == 0:
        if json.loads(result.stdout)['draft']:
            raise RuntimeError('A draft for this version already exists; inspect it before publishing.')
        print(tag + ' is already published; keeping the new Actions artifact only.')
        return
    if 'HTTP 404' not in result.stderr:
        raise RuntimeError('Cannot check release: ' + result.stderr)
    # The required Windows job has installed and checked the original RAR.
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    subprocess.run(['gh', 'release', 'create', tag, '--draft', '--target', commit,
                    '--title', 'SDOcraft ' + tag, '--notes-file', 'release/notes.md'], check=True)
    # Each launcher generation retains its own manifest and profile.
    subprocess.run(['gh', 'release', 'upload', tag, 'dist/SDOcraft.exe', str(path),
                    'release/modpack-solocraft.json', 'release/modpack.json'], check=True)
    subprocess.run(['gh', 'release', 'edit', tag, '--draft=false', '--latest'], check=True)
    print('Published ' + tag)


if __name__ == '__main__':
    main()
