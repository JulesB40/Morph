import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from morph_lab.session import run_session


FAKE = r'''
import json, pathlib, sqlite3, sys, time
role, control, world, mode = sys.argv[1:]
control = pathlib.Path(control)
db = sqlite3.connect(world, timeout=3)
db.execute("CREATE TABLE IF NOT EXISTS players (role TEXT PRIMARY KEY, form TEXT, shown INTEGER, connected INTEGER, z REAL)")
db.commit()
if role != 'server':
    db.execute("INSERT OR REPLACE INTO players VALUES (?, 'minecraft:player', 1, 1, 0)", (role,))
    db.commit()
def emit(event, ident=None, detail=None):
    row = dict(event=event, role=role, detail=detail or state())
    if ident is not None: row['id'] = ident
    with (control/'events.ndjson').open('a') as out: out.write(json.dumps(row)+'\n')
def state():
    players = [dict(uuid=r, name='Morph'+r.title(), form=f, showNameTag=bool(s),position=[0,80,z])
               for r,f,s,c,z in db.execute('SELECT * FROM players') if c]
    return dict(players=players, uuid=role, eyePosition=[6,81.62,0],
                connected=any(p['uuid']==role for p in players))
if mode == 'no-ready' and role == 'observer':
    time.sleep(30)
emit('ready')
seen=set()
pending_movement=None
while True:
    if pending_movement is not None and time.monotonic() >= pending_movement:
        db.execute('UPDATE players SET z=z+1 WHERE role=?',(role,))
        db.commit()
        pending_movement=None
    for path in sorted((control/'requests').glob('*.json')):
        if path.name in seen: continue
        seen.add(path.name)
        req=json.loads(path.read_text()); op=req['op']; detail=None
        if op=='probe':
            if req['name']=='capture-failure':
                expected=mode!='missing-capture-failure'
                emit('failed' if expected else 'completed',req['id'],
                    {'probe':'capture-failure','injected':expected,'recoverable':True})
            else:
                passed=mode!='failed-component'
                emit('completed' if passed else 'failed',req['id'],{'passed':passed,'recoverable':True})
            continue
        if op=='command' and role!='server':
            if req['command'].startswith('morph select') and mode!='reject-select':
                db.execute("UPDATE players SET form='minecraft:bat' WHERE role=?",(role,))
            if req['command']=='morph nametag off':
                db.execute('UPDATE players SET shown=0 WHERE role=?',(role,))
        if op in ('disconnect','reconnect'):
            db.execute('UPDATE players SET connected=? WHERE role=?',(int(op=='reconnect'),role))
        if op=='input' and req.get('keys'):
            if mode=='delayed-movement': pending_movement=time.monotonic()+.25
            elif mode!='lost-input': db.execute('UPDATE players SET z=z+1 WHERE role=?',(role,))
        if op=='capture':
            folder=control/'captures'; folder.mkdir(exist_ok=True)
            (folder/(req['id']+'.png')).write_bytes(b'\x89PNG\r\n\x1a\n'+b'0'*32)
            detail={'png':'captures/'+req['id']+'.png'}
        db.commit()
        emit('completed',req['id'],detail)
        if op in ('stop','exit'): sys.exit(0)
    time.sleep(.005)
'''


class SessionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.script = self.root / 'bridge.py'
        self.script.write_text(FAKE)

    def tearDown(self): self.temp.cleanup()

    def spec(self, mode='normal'):
        return dict(port=25599, timeout=12, step_timeout=2,
            roles={role:dict(argv=[sys.executable,str(self.script),'{role}','{control}',
                                  str(self.root/'world.db'),mode],cwd='{game}')
                   for role in ('server','actor','observer')})

    def test_full_fake_process_lifecycle_and_restart(self):
        result = run_session(self.spec(), self.root/'run', self.root)
        self.assertEqual(result['status'],'passed', result)
        self.assertTrue(result['cleanup_confirmed'])
        self.assertEqual(len(result['processes']),4)
        self.assertEqual(len(result['captures']),2)
        self.assertNotEqual(result['restart']['before']['pid'],result['restart']['after']['pid'])
        self.assertTrue((self.root/'run'/'session-result.json').is_file())

    def test_successful_command_ack_cannot_certify_rejected_form(self):
        spec = self.spec('reject-select')
        spec['step_timeout'] = .4
        result = run_session(spec, self.root/'run', self.root)
        self.assertEqual(result['status'],'timeout',result)
        self.assertTrue(result['cleanup_confirmed'])
        self.assertNotIn('authoritative_and_two_client_appearance',result['checks'])

    def test_input_ack_can_precede_authoritative_movement(self):
        result = run_session(self.spec('delayed-movement'), self.root/'run', self.root)
        self.assertEqual(result['status'],'passed',result)
        self.assertEqual(result['movement']['horizontal_distance'],{'actor':1.0,'observer':0.0})

    def test_input_ack_without_movement_never_passes(self):
        spec = self.spec('lost-input')
        spec['step_timeout'] = .4
        result = run_session(spec,self.root/'run',self.root)
        self.assertEqual(result['status'],'timeout',result)
        self.assertTrue(result['cleanup_confirmed'])
        self.assertNotIn('actor_input_observer_stationary',result['checks'])

    def test_timeout_cleans_all_owned_processes(self):
        spec = self.spec('no-ready')
        spec['step_timeout'] = .4
        result = run_session(spec, self.root/'run', self.root)
        self.assertEqual(result['status'],'timeout',result)
        self.assertTrue(result['cleanup_confirmed'])
        self.assertEqual(len(result['processes']),3)

    def test_optional_probes_and_capture_recovery(self):
        spec=self.spec()
        spec.update(component_probes=['wither-heads','sniffer-middle-legs'],capture_recovery=True,frame_scene=True)
        result=run_session(spec,self.root/'run',self.root)
        self.assertEqual(result['status'],'passed',result)
        self.assertEqual(len(result['component_probes']),2)
        self.assertEqual(result['capture_recovery']['injection']['event'],'failed')
        self.assertIn('injected_capture_failure_then_state_and_png',result['checks'])
        self.assertAlmostEqual(result['frame_scene']['yaw'],80.53767779)
        self.assertGreater(result['frame_scene']['pitch'],0)
        actor_requests = [json.loads(path.read_text()) for path in
                          (self.root/'run'/'actor'/'control-1'/'requests').glob('*.json')]
        self.assertTrue(any(row.get('op')=='view' and row.get('perspective')=='third_person_front'
                            and row.get('hideGui') is True for row in actor_requests))
        self.assertTrue(any(row.get('op')=='input' and row.get('keys')==[] and row.get('ticks')==120
                            for row in actor_requests))

    def test_missing_injected_failure_cannot_pass(self):
        spec=self.spec('missing-capture-failure')
        spec['capture_recovery']=True
        result=run_session(spec,self.root/'run',self.root)
        self.assertEqual(result['status'],'failed',result)
        self.assertTrue(result['cleanup_confirmed'])
        self.assertNotIn('injected_capture_failure_then_state_and_png',result['checks'])

    def test_failed_component_is_retained_while_recovery_runs(self):
        spec=self.spec('failed-component')
        spec.update(component_probes=['sniffer-middle-legs'],capture_recovery=True)
        result=run_session(spec,self.root/'run',self.root)
        self.assertEqual(result['status'],'failed',result)
        self.assertFalse(result['component_probes'][0]['passed'])
        self.assertIn('injected_capture_failure_then_state_and_png',result['checks'])
        self.assertTrue(result['cleanup_confirmed'])

    def test_invalid_spec_never_launches(self):
        spec = self.spec()
        spec['roles']['actor']['argv'] = 'not argv'
        with self.assertRaises(ValueError): run_session(spec,self.root/'run',self.root)
        self.assertFalse((self.root/'run').exists())


if __name__ == '__main__': unittest.main()
