from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile
from java_setup import verify_java, verify_forge_files, verify_command_files


class JavaSetupTests(unittest.TestCase):
    def test_nonworking_or_wrong_java_is_not_reported_as_success(self):
        for code, output in ((1, b'Error: could not find java.dll'), (0, b'openjdk version "21.0.2"')):
            with patch('java_setup.subprocess.run', return_value=subprocess.CompletedProcess([], code, output)):
                with self.assertRaises(RuntimeError):
                    verify_java('java.exe')
        with patch('java_setup.subprocess.run', return_value=subprocess.CompletedProcess([], 0, b'openjdk version "17.0.15"')):
            verify_java('java.exe')

    def test_missing_processor_output_is_not_reported_as_installed(self):
        with tempfile.TemporaryDirectory() as temporary:
            game = Path(temporary)
            forge = '1.20.1-47.4.6'
            with self.assertRaises(RuntimeError):
                verify_forge_files(game, forge)
            target = game/'libraries/net/minecraftforge/forge'/forge/('forge-'+forge+'-client.jar')
            target.parent.mkdir(parents=True)
            with zipfile.ZipFile(target, 'w') as archive:
                archive.writestr('test', b'valid jar')
            verify_forge_files(game, forge)

    def test_missing_launch_library_is_reported(self):
        with self.assertRaises(RuntimeError):
            verify_command_files(['java', '-cp', '/missing-sdocraft-library.jar'])
