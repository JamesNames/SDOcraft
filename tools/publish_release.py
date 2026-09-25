"""Publish the prepared manifest and EXE after the Windows build succeeds."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from config import VERSION
from modrinth_pack import validate_manifest, cached_archive, read_pack


def main():
    path = Path('release/modpack-solocraft.json')
    if not path.is_file():
        print('No release prepared; keeping the Actions artifact only.')
        return
    manifest = validate_manifest(json.loads(path.read_text(encoding='utf-8')))
    tag = 'v' + VERSION
    if manifest.get('version') != tag:
        raise RuntimeError('Version in config.py must match release/modpack-solocraft.json')
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
    # Validate the original author archive and exact client layout before release.
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        files = read_pack(cached_archive(root, manifest), manifest)
        print('Verified SoloCraft client files:', len(files))
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    subprocess.run(['gh', 'release', 'create', tag, '--draft', '--target', commit,
                    '--title', 'SDOcraft ' + tag, '--notes-file', 'release/notes.md'], check=True)
    # Legacy clients keep their old pack; they must not receive SoloCraft files.
    subprocess.run(['gh', 'release', 'upload', tag, 'dist/SDOcraft.exe', str(path), 'release/modpack.json'], check=True)
    subprocess.run(['gh', 'release', 'edit', tag, '--draft=false', '--latest'], check=True)
    print('Published ' + tag)


if __name__ == '__main__':
    main()
