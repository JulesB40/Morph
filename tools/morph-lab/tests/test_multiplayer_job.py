import tempfile
import unittest
from pathlib import Path
import contextlib
import io
import json
import hashlib
from unittest.mock import patch

import multiplayer_job
from multiplayer_job import prepare_fixture


class MultiplayerFixtureTest(unittest.TestCase):
    def make_launch(self, source):
        generated = source / 'build/moddev'
        generated.mkdir(parents=True)
        argument_file = generated / 'labClientRunClasspath.txt'
        argument_file.write_text('-classpath\n"prepared.jar"\n')
        executable = source / 'java.exe'
        executable.write_bytes(b'harmless executable provenance fixture')
        entry = {'argv': [str(executable), '@' + str(argument_file)], 'cwd': '{game}', 'env': {}}
        launch = {'schema': 1, 'roles': {'client': entry, 'server': dict(entry)}}
        launch_file = source / 'build/morph-lab/launch.json'
        launch_file.parent.mkdir()
        launch_file.write_text(json.dumps(launch))
        return launch_file, launch, argument_file

    def test_private_server_and_distinct_client_directories(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            prepare_fixture(root, 25592)
            properties = dict(line.split("=", 1) for line in
                              (root / "server/game/server.properties").read_text().splitlines())
            self.assertEqual(properties["server-ip"], "127.0.0.1")
            self.assertEqual(properties["server-port"], "25592")
            self.assertEqual(properties["online-mode"], "false")
            actor = root / "actor/game/options.txt"
            observer = root / "observer/game/options.txt"
            self.assertNotEqual(actor.resolve(), observer.resolve())
            self.assertEqual(actor.read_bytes(), observer.read_bytes())
            actor.write_text("changed")
            self.assertNotEqual(actor.read_bytes(), observer.read_bytes())

    def test_existing_world_is_never_replaced(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            game = root / "server/game"
            game.mkdir(parents=True)
            sentinel = game / "level.dat"
            sentinel.write_bytes(b"existing world")
            with self.assertRaises(FileExistsError):
                prepare_fixture(root, 25592)
            self.assertEqual(sentinel.read_bytes(), b"existing world")
            self.assertFalse((game / "server.properties").exists())

    def test_main_does_not_overwrite_an_existing_run(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, run = root / "source", root / "run"
            source.mkdir()
            run.mkdir()
            result = run / "result.json"
            result.write_text('existing evidence')
            with patch('sys.argv', ['multiplayer_job', '--source', str(source), '--run', str(run)]), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(multiplayer_job.main(), 1)
            self.assertEqual(result.read_text(), 'existing evidence')

    def test_preparation_cleanup_is_required(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'source'
            source.mkdir()
            prepared = {'status': 'passed', 'cleanup_confirmed': False}
            with patch.object(multiplayer_job, 'supervise', return_value=prepared), patch.object(multiplayer_job, 'run_session') as session:
                result = multiplayer_job.execute(source, root / 'run')
            self.assertEqual(result['status'], 'infrastructure_failure')
            self.assertFalse(result['cleanup_confirmed'])
            session.assert_not_called()

    def test_fixture_failure_after_bind_preserves_cleanup_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'source'
            (source / 'build/morph-lab').mkdir(parents=True)
            (source / 'build/morph-lab/launch.json').write_text('{}')
            prepared = {'status': 'passed', 'cleanup_confirmed': True}
            with patch.object(multiplayer_job, 'supervise', return_value=prepared), patch.object(multiplayer_job, 'prepare_fixture', side_effect=OSError('fixture rejected')):
                result = multiplayer_job.execute(source, root / 'run')
            self.assertEqual(result['status'], 'infrastructure_failure')
            self.assertTrue(result['cleanup_confirmed'])
            self.assertEqual(result['preparation'], prepared)

    def test_exact_argfile_and_java_hashes_and_owned_cwd(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory)
            launch_file, launch, argument_file = self.make_launch(source)
            hashes = multiplayer_job.launch_provenance(source, launch_file, launch)
            self.assertIn(str(launch_file), hashes)
            self.assertIn(str(source / 'java.exe'), hashes)
            self.assertEqual(hashes[str(argument_file)], hashlib.sha256(argument_file.read_bytes()).hexdigest())
            launch['roles']['client']['cwd'] = str(source)
            launch_file.write_text(json.dumps(launch))
            with self.assertRaisesRegex(ValueError, 'owned'):
                multiplayer_job.launch_provenance(source, launch_file, launch)

    def test_nested_argfile_is_not_silently_unhashed(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory)
            launch_file, launch, argument_file = self.make_launch(source)
            argument_file.write_text('@nested.args\n')
            with self.assertRaisesRegex(ValueError, 'Nested'):
                multiplayer_job.launch_provenance(source, launch_file, launch)

    def test_launcher_mutation_invalidates_session_and_keeps_cleanup(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'source'
            launch_file, launch, argument_file = self.make_launch(source)
            prepared = {'status': 'passed', 'cleanup_confirmed': True}
            def session(spec, run, source):
                self.assertEqual(spec['min_free_memory_mb'], 384)
                argument_file.write_text('changed classpath')
                return {'status': 'passed', 'cleanup_confirmed': True}
            with patch.object(multiplayer_job, 'supervise', return_value=prepared), patch.object(multiplayer_job, 'run_session', side_effect=session):
                result = multiplayer_job.execute(source, root / 'run')
            self.assertEqual(result['status'], 'infrastructure_failure')
            self.assertTrue(result['cleanup_confirmed'])
            self.assertIn(str(argument_file), result['launch_provenance']['changed_after_session'])
            self.assertEqual(json.loads((root / 'run/result.json').read_text())['status'], 'infra_error')

    def test_unexpected_session_error_withholds_cleanup(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'source'
            self.make_launch(source)
            prepared = {'status': 'passed', 'cleanup_confirmed': True}
            with patch.object(multiplayer_job, 'supervise', return_value=prepared), patch.object(multiplayer_job, 'run_session', side_effect=RuntimeError('unexpected')):
                result = multiplayer_job.execute(source, root / 'run')
            self.assertFalse(result['cleanup_confirmed'])
            self.assertEqual(result['status'], 'infrastructure_failure')
