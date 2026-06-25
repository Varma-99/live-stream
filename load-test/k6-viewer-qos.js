/**
 * k6 load test: fake viewers + presence + likes + QoS for LiveStream.
 *
 * Viewer APIs exercised: join, heartbeat, like, leave, viewer-stats, viewer-event, room snapshot.
 * Broadcaster actions (coupon, degrade, pause) are not simulated — use the broadcast UI for those.
 *
 * Env:
 *   TARGET_VUS=200     peak virtual viewers (default 200)
 *   STREAM_ID=1        optional; auto-picks live stream if unset
 *   BASE_URL=...       default http://127.0.0.1:8080
 *   RAMP_UP_SEC=60     ramp-up duration
 *   HOLD_SEC=90        hold at peak VUs
 *   RAMP_DOWN_SEC=40   ramp-down duration
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const STREAM_ID_ENV = __ENV.STREAM_ID || '';
const TARGET_VUS = parseInt(__ENV.TARGET_VUS || '200', 10);
const RAMP_UP_SEC = parseInt(__ENV.RAMP_UP_SEC || '60', 10);
const HOLD_SEC = parseInt(__ENV.HOLD_SEC || '90', 10);
const RAMP_DOWN_SEC = parseInt(__ENV.RAMP_DOWN_SEC || '40', 10);
const HEARTBEAT_ROUNDS = parseInt(__ENV.HEARTBEAT_ROUNDS || '20', 10);
const HEARTBEAT_INTERVAL_SEC = parseFloat(__ENV.HEARTBEAT_INTERVAL_SEC || '2');
const LIKE_CHANCE = parseFloat(__ENV.LIKE_CHANCE || '0.08');
const ROOM_POLL_EVERY = parseInt(__ENV.ROOM_POLL_EVERY || '8', 10);
const THRESHOLD_HTTP_FAIL = parseFloat(__ENV.THRESHOLD_HTTP_FAIL || '0.08');
const THRESHOLD_JOIN_OK = parseFloat(__ENV.THRESHOLD_JOIN_OK || '0.92');
const THRESHOLD_QOS_OK = parseFloat(__ENV.THRESHOLD_QOS_OK || '0.92');
const THRESHOLD_P95_MS = parseFloat(__ENV.THRESHOLD_P95_MS || '2500');

const qosStatsOk = new Rate('qos_stats_ok');
const qosEventOk = new Rate('qos_event_ok');
const joinOk = new Rate('join_ok');
const likeOk = new Rate('like_ok');
const joinDuration = new Trend('join_duration', true);

const QUALITIES = ['720p', '480p', '360p'];
/** Mix of delivery + viewer QoS signals (parsed by StreamQoSResource). */
const VIEWER_EVENTS = [
  'STALL',
  'ABR_DOWN',
  'ABR_UP',
  'TTFF',
  'RECONNECT',
  'RECOVERED',
  'FATAL',
];

export const options = {
  scenarios: {
    viewers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP_UP_SEC + 's', target: TARGET_VUS },
        { duration: HOLD_SEC + 's', target: TARGET_VUS },
        { duration: RAMP_DOWN_SEC + 's', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: [`rate<${THRESHOLD_HTTP_FAIL}`],
    http_req_duration: [`p(95)<${THRESHOLD_P95_MS}`],
    join_ok: [`rate>${THRESHOLD_JOIN_OK}`],
    qos_stats_ok: [`rate>${THRESHOLD_QOS_OK}`],
  },
};

export function setup() {
  const maxAttempts = STREAM_ID_ENV ? 30 : 1;
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const res = http.get(`${BASE_URL}/streams`);
    if (res.status !== 200) {
      throw new Error(`GET /streams failed (${res.status}). Is the app running?`);
    }
    const live = JSON.parse(res.body);
    if (STREAM_ID_ENV) {
      const match = live.find((s) => String(s.id) === STREAM_ID_ENV);
      if (match) {
        console.log(`k6 stream id=${STREAM_ID_ENV} (${match.title}), peak VUs=${TARGET_VUS}`);
        return { streamId: STREAM_ID_ENV };
      }
      if (attempt + 1 < maxAttempts) {
        sleep(2);
        continue;
      }
      const ids = live.map((s) => s.id);
      throw new Error(
        `STREAM_ID=${STREAM_ID_ENV} is not live. Live now: [${ids.join(', ')}].`
      );
    }
    if (live.length) {
      const pick = live[0];
      console.log(`k6 stream id=${pick.id} (${pick.title}), peak VUs=${TARGET_VUS}`);
      return { streamId: String(pick.id) };
    }
    if (attempt + 1 < maxAttempts) {
      sleep(2);
      continue;
    }
    throw new Error(
      'No LIVE stream. Start ONE stream on the broadcast page, then run k6.'
    );
  }
  throw new Error('setup failed');
}

function jsonHeaders() {
  return { headers: { 'Content-Type': 'application/json' } };
}

function randBetween(min, max) {
  return min + Math.random() * (max - min);
}

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function streamPath(streamId, suffix) {
  return `${BASE_URL}/streams/${streamId}${suffix}`;
}

function postQoSEvent(streamId, presenceId, eventType) {
  let detail;
  if (eventType === 'TTFF') {
    detail = String(Math.round(randBetween(500, 8000)));
  } else if (eventType === 'RECOVERED') {
    detail = String(Math.round(randBetween(1000, 15000)));
  }
  const eventBody = JSON.stringify({
    presenceId,
    eventType,
    message: `k6 synthetic ${eventType}`,
    detail,
  });
  const eventRes = http.post(streamPath(streamId, '/qos/viewer-event'), eventBody, {
    ...jsonHeaders(),
    tags: { name: 'viewer-event' },
  });
  const ok = check(eventRes, { 'viewer-event 204': (r) => r.status === 204 });
  qosEventOk.add(ok);
}

export default function (data) {
  const streamId = data.streamId;

  const joinRes = http.post(streamPath(streamId, '/join'), null, {
    ...jsonHeaders(),
    tags: { name: 'join' },
  });
  joinDuration.add(joinRes.timings.duration);
  const joined = check(joinRes, {
    'join status 200': (r) => r.status === 200,
    'join has presenceId': (r) => {
      try {
        return !!JSON.parse(r.body).presenceId;
      } catch (e) {
        return false;
      }
    },
  });
  joinOk.add(joined);
  if (!joined) {
    return;
  }

  const presenceId = JSON.parse(joinRes.body).presenceId;

  for (let i = 0; i < HEARTBEAT_ROUNDS; i++) {
    const hbRes = http.post(
      streamPath(streamId, '/heartbeat'),
      JSON.stringify({ presenceId }),
      { ...jsonHeaders(), tags: { name: 'heartbeat' } }
    );
    check(hbRes, { 'heartbeat 200': (r) => r.status === 200 });

    if (i > 0 && i % ROOM_POLL_EVERY === 0) {
      const roomRes = http.get(streamPath(streamId, '/room'), { tags: { name: 'room' } });
      check(roomRes, { 'room 200': (r) => r.status === 200 });
    }

    if (Math.random() < LIKE_CHANCE) {
      const likeRes = http.post(
        streamPath(streamId, '/like'),
        JSON.stringify({ presenceId }),
        { ...jsonHeaders(), tags: { name: 'like' } }
      );
      const liked = check(likeRes, { 'like 200': (r) => r.status === 200 });
      likeOk.add(liked);
    }

    const statsBody = JSON.stringify({
      presenceId,
      qualityLabel: pick(QUALITIES),
      packetLossPct: randBetween(0, 8),
      rttMs: Math.round(randBetween(15, 350)),
      jitterMs: randBetween(0, 80),
      downloadKbps: Math.round(randBetween(150, 5500)),
    });
    const statsRes = http.post(streamPath(streamId, '/qos/viewer-stats'), statsBody, {
      ...jsonHeaders(),
      tags: { name: 'viewer-stats' },
    });
    const statsPassed = check(statsRes, { 'viewer-stats 204': (r) => r.status === 204 });
    qosStatsOk.add(statsPassed);

    if (i % 4 === 0) {
      let eventType = pick(VIEWER_EVENTS);
      if (eventType === 'FATAL' && Math.random() > 0.15) {
        eventType = pick(['STALL', 'RECONNECT', 'ABR_DOWN']);
      }
      postQoSEvent(streamId, presenceId, eventType);
    }

    sleep(HEARTBEAT_INTERVAL_SEC);
  }

  const leaveRes = http.post(
    streamPath(streamId, '/leave'),
    JSON.stringify({ presenceId }),
    { ...jsonHeaders(), tags: { name: 'leave' } }
  );
  check(leaveRes, { 'leave 204': (r) => r.status === 204 });
}
