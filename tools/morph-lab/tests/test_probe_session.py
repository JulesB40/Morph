import sys
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from morph_lab.probe_session import run_probe_session
from test_session import FAKE


class ProbeSessionTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.root=Path(self.temp.name)
        (self.root/'bridge.py').write_text(FAKE)

    def tearDown(self): self.temp.cleanup()

    def spec(self,mode='normal'):
        return dict(port=25599,timeout=12,step_timeout=2,fixture_form='minecraft:bat',frame_scene=True,
            roles={role:dict(argv=[sys.executable,str(self.root/'bridge.py'),'{role}','{control}',
                                  str(self.root/'world.db'),mode],cwd='{game}') for role in ('server','actor')})

    def test_two_owned_processes_complete_all_probes_and_recovery(self):
        result=run_probe_session(self.spec(),self.root/'run',self.root)
        self.assertEqual(result['status'],'passed',result)
        self.assertEqual(result['lane'],'single-client-components')
        self.assertEqual(len(result['processes']),2)
        self.assertTrue(result['cleanup_confirmed'])
        self.assertEqual(len(result['component_probes']),4)
        self.assertEqual(len(result['captures']),1)
        self.assertNotIn('restart',result)
        self.assertFalse((self.root/'run'/'observer').exists())

    def test_component_failure_stays_failed_after_recovery(self):
        result=run_probe_session(self.spec('failed-component'),self.root/'run',self.root)
        self.assertEqual(result['status'],'failed',result)
        self.assertTrue(result['cleanup_confirmed'])
        self.assertIn('injected_capture_failure_then_state_and_png',result['checks'])

    def test_missing_injected_failure_rejected_and_cleaned(self):
        result=run_probe_session(self.spec('missing-capture-failure'),self.root/'run',self.root)
        self.assertEqual(result['status'],'failed',result)
        self.assertTrue(result['cleanup_confirmed'])
        self.assertEqual(result['captures'],[])

    def test_memory_floor_blocks_all_launches(self):
        spec=self.spec()
        spec['min_free_memory_mb']=512
        with patch('morph_lab.cli.available_memory_mb',return_value=128):
            result=run_probe_session(spec,self.root/'run',self.root)
        self.assertEqual(result['status'],'infrastructure_failure',result)
        self.assertTrue(result['cleanup_confirmed'])
        self.assertEqual(result['processes'],[])

    def test_extra_observer_role_rejected(self):
        spec=self.spec()
        spec['roles']['observer']=spec['roles']['actor']
        with self.assertRaises(ValueError): run_probe_session(spec,self.root/'run',self.root)
        self.assertFalse((self.root/'run').exists())

    def test_actor_readiness_timeout_cleans_both_processes(self):
        (self.root/'bridge.py').write_text(FAKE.replace("role == 'observer'", "role == 'actor'"))
        spec=self.spec('no-ready')
        spec['step_timeout']=.4
        result=run_probe_session(spec,self.root/'run',self.root)
        self.assertEqual(result['status'],'timeout',result)
        self.assertTrue(result['cleanup_confirmed'])
        self.assertEqual(len(result['processes']),2)


if __name__=='__main__': unittest.main()
