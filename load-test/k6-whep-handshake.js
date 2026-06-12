/**
 * k6 WHEP signaling load — POST minimal SDP offers to app WHEP proxy (SRS upstream).
 * Does not establish full WebRTC media; stresses HTTP signaling path.
 *
 * Env: BASE_URL, STREAM_KEY (required), TARGET_VUS, RAMP_UP_SEC, HOLD_SEC
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const STREAM_KEY = __ENV.STREAM_KEY || '';
const QUALITY = __ENV.WHEP_QUALITY || '_high';
const TARGET_VUS = parseInt(__ENV.TARGET_VUS || '50', 10);
const RAMP_UP_SEC = parseInt(__ENV.RAMP_UP_SEC || '30', 10);
const HOLD_SEC = parseInt(__ENV.HOLD_SEC || '60', 10);
const RAMP_DOWN_SEC = parseInt(__ENV.RAMP_DOWN_SEC || '20', 10);

const whepOk = new Rate('whep_handshake_ok');
const whepDuration = new Trend('whep_handshake_duration', true);

const MIN_OFFER = [
  'v=0',
  'o=- 0 0 IN IP4 127.0.0.1',
  's=-',
  't=0 0',
  'a=group:BUNDLE 0 1',
  'm=audio 9 UDP/TLS/RTP/SAVPF 111',
  'c=IN IP4 0.0.0.0',
  'a=rtcp-mux',
  'a=recvonly',
  'a=mid:0',
  'a=ice-ufrag:k6aa',
  'a=ice-pwd:k6passwordk6passwordk6',
  'a=fingerprint:sha-256 00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00',
  'a=rtpmap:111 opus/48000/2',
  'm=video 9 UDP/TLS/RTP/SAVPF 96',
  'c=IN IP4 0.0.0.0',
  'a=rtcp-mux',
  'a=recvonly',
  'a=mid:1',
  'a=ice-ufrag:k6vv',
  'a=ice-pwd:k6passwordk6passwordk6',
  'a=fingerprint:sha-256 00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00',
  'a=rtpmap:96 H264/90000',
].join('\r\n');

export const options = {
  scenarios: {
    whep: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: `${RAMP_UP_SEC}s`, target: TARGET_VUS },
        { duration: `${HOLD_SEC}s`, target: TARGET_VUS },
        { duration: `${RAMP_DOWN_SEC}s`, target: 0 },
      ],
      gracefulRampDown: '15s',
    },
  },
  thresholds: {
    whep_handshake_ok: ['rate>0.85'],
    http_req_duration: ['p(95)<5000'],
  },
};

export function setup() {
  if (!STREAM_KEY) {
    const res = http.get(`${BASE_URL}/streams`);
    if (res.status !== 200) {
      throw new Error('GET /streams failed — is the app running?');
    }
    const live = JSON.parse(res.body);
    if (!live.length) {
      throw new Error('No LIVE stream. Start broadcast first.');
    }
    const key = live[0].streamKey;
    if (!key) {
      throw new Error('Live stream has no streamKey');
    }
    console.log(`WHEP load: streamKey=${key}, VUs=${TARGET_VUS}`);
    return { streamKey: key };
  }
  return { streamKey: STREAM_KEY };
}

export default function (data) {
  const path = `${BASE_URL}/whep/live/${data.streamKey}${QUALITY}/whep`;
  const res = http.post(path, MIN_OFFER, {
    headers: { 'Content-Type': 'application/sdp' },
    tags: { name: 'whep-handshake' },
    timeout: '15s',
  });
  whepDuration.add(res.timings.duration);
  const ok = check(res, {
    'whep 200/201': (r) => r.status === 200 || r.status === 201,
    'whep has sdp': (r) => r.body && r.body.indexOf('v=0') >= 0,
  });
  whepOk.add(ok);
  sleep(1);
}
