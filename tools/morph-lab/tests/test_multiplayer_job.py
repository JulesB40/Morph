import tempfile
import unittest
from pathlib import Path

from multiplayer_job import prepare_fixture


class MultiplayerFixtureTest(unittest.TestCase):
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
