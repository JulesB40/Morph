import hashlib
import json
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zlib

from morph_lab.evidence import STATUSES, encode_video, write_report


def png(path):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 2, 2, 8, 2, 0, 0, 0)) +
                     chunk(b'IDAT', zlib.compress((b'\0' + b'\xff\0\0' * 2) * 2)) + chunk(b'IEND', b''))
    return path


class EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_statuses_and_missing_capture_are_explicit(self):
        for status in STATUSES:
            run = self.root / status
            run.mkdir()
            (run / 'manifest.json').write_text('{}')
            (run / 'result.json').write_text(json.dumps({'status': status, 'reason': 'specific reason'}))
        report = write_report(self.root).read_text()
        for status in STATUSES:
            self.assertIn(f'data-status="{status}"', report)
        self.assertEqual(report.count('No captured PNG/video'), len(STATUSES))

    def test_escape_paths_hashes_and_provenance(self):
        frame = png(self.root / 'frame #1.png')
        (self.root / 'manifest.json').write_text(json.dumps({'evidence': [
            {'path': frame.name, 'role': 'actor baseline <script>alert(1)</script>'},
            {'path': '../secret.txt', 'role': 'observer'}, {'path': 'missing.png'}]}))
        (self.root / 'result.json').write_text(json.dumps({'status': 'fail', 'reason': '<img src=x onerror=alert(1)>'}))
        (self.root / 'events.ndjson').write_text('{"tick":1}\n')
        report = write_report(self.root).read_text()
        self.assertNotIn('<script>alert', report)
        self.assertNotIn('<img src=x', report)
        self.assertIn('&lt;img src=x', report)
        self.assertIn('frame%20%231.png', report)
        self.assertIn(hashlib.sha256(frame.read_bytes()).hexdigest(), report)
        self.assertIn('Refused evidence path', report)
        self.assertIn('Missing evidence: missing.png', report)
        self.assertNotIn('href="../secret', report)
        self.assertIn('events.ndjson</a>', report)

    def test_external_symlink_not_exposed(self):
        with tempfile.TemporaryDirectory() as outside:
            secret = Path(outside) / 'private.txt'
            secret.write_text('private')
            try:
                (self.root / 'escape.txt').symlink_to(secret)
            except OSError:
                self.skipTest('symlink creation unavailable')
            (self.root / 'manifest.json').write_text('{}')
            (self.root / 'result.json').write_text('{}')
            self.assertNotIn('escape.txt</a>', write_report(self.root).read_text())

    def test_empty_invalid_and_missing_frames_never_publish_video(self):
        for index, frames in enumerate(([], [self.root / 'absent.png'], [self.root / 'invalid.png'])):
            (self.root / 'invalid.png').write_text('not png')
            output = self.root / f'{index}.mp4'
            result = encode_video(frames, output)
            self.assertEqual(result['status'], 'infrastructure_failure')
            self.assertFalse(output.exists())
            self.assertTrue(output.with_suffix('.mp4.json').exists())

    def test_argument_validation(self):
        for kwargs in ({'framerate': 0}, {'framerate': float('nan')}, {'timeout': -1}, {'dropped_frames': -1}, {'ticks': [1]}):
            with self.assertRaises(ValueError):
                encode_video([], self.root / 'clip.mp4', **kwargs)

    def test_timeout_is_distinct_and_command_is_argv(self):
        frame = png(self.root / 'capture.png')
        with patch('morph_lab.evidence.shutil.which', return_value=str(frame)), patch('morph_lab.evidence.subprocess.run', side_effect=subprocess.TimeoutExpired('ffmpeg', 1)) as runner:
            result = encode_video([frame], self.root / 'clip.mp4', timeout=1)
        self.assertEqual(result['status'], 'timeout')
        self.assertIsInstance(runner.call_args.args[0], list)
        self.assertFalse(runner.call_args.kwargs['shell'])
        self.assertEqual(runner.call_args.kwargs['timeout'], 1)
        self.assertFalse((self.root / 'clip.mp4').exists())

    @unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'), 'FFmpeg/ffprobe unavailable')
    def test_real_synthetic_clip_is_decodable_with_exact_frame_count(self):
        frame = png(self.root / 'synthetic.png')
        output = self.root / 'synthetic.mp4'
        result = encode_video([frame] * 3, output, ticks=[5, 7, 9], dropped_frames=2, framerate=10)
        self.assertEqual(result['status'], 'pass', result)
        self.assertEqual(result['classification'], 'tick_sampled_replay')
        self.assertFalse(result['real_time_fps_measurement'])
        self.assertEqual(result['ticks'], [5, 7, 9])
        self.assertEqual(result['dropped_frames'], 2)
        probe = subprocess.run(['ffprobe', '-v', 'error', '-count_frames', '-show_entries', 'stream=nb_read_frames,r_frame_rate', '-of', 'json', str(output)], capture_output=True, check=True, timeout=20)
        stream = json.loads(probe.stdout)['streams'][0]
        self.assertEqual(stream['nb_read_frames'], '3')
        self.assertEqual(stream['r_frame_rate'], '10/1')
        self.assertEqual(result['sha256'], hashlib.sha256(output.read_bytes()).hexdigest())
        with self.assertRaises(FileExistsError):
            encode_video([frame], output)


if __name__ == '__main__':
    unittest.main()
