/**
 * k6 HLS segment load test — fetches .m3u8 playlists and .ts segments.
 *
 * Env: BASE_URL, STREAM_ID (optional), SEGMENT_LOOP_SEC (default 2)
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const STREAM_ID_ENV = __ENV.STREAM_ID || '';
const SEGMENT_LOOP_SEC = parseFloat(__ENV.SEGMENT_LOOP_SEC || '2');

const manifestDuration = new Trend('hls_manifest_fetch_duration', true);
const segmentDuration = new Trend('hls_segment_fetch_duration', true);
const segmentSize = new Trend('hls_segment_size');
const bitrateEstimate = new Trend('hls_bitrate_estimate');
const errorRate = new Rate('hls_error_rate');
const stallDetected = new Counter('hls_stall_detected');

export const options = {
  scenarios: {
    hls_viewers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: parseInt(__ENV.TARGET_VUS || '20', 10) },
        { duration: '120s', target: parseInt(__ENV.TARGET_VUS || '20', 10) },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    hls_error_rate: ['rate<0.05'],
  },
};

function parseSegments(body) {
  const lines = body.split('\n');
  return lines.filter((l) => l.trim().endsWith('.ts')).map((l) => l.trim());
}

export function setup() {
  const res = http.get(`${BASE_URL}/streams`);
  if (res.status !== 200) throw new Error('GET /streams failed');
  const live = JSON.parse(res.body).filter((s) => s.delivery === 'hls');
  if (!live.length) throw new Error('No HLS stream. Start with delivery=hls.');
  let pick = live[0];
  if (STREAM_ID_ENV) {
    pick = live.find((s) => String(s.id) === STREAM_ID_ENV) || pick;
  }
  const playlistUrl = pick.playbackUrl
    ? `${BASE_URL}${pick.playbackUrl}`
    : `${BASE_URL}/hls/${pick.id}/index.m3u8`;
  return { streamId: String(pick.id), playlistUrl };
}

export default function (data) {
  const manifestRes = http.get(data.playlistUrl, { tags: { name: 'm3u8' } });
  manifestDuration.add(manifestRes.timings.duration);
  const ok = check(manifestRes, { 'manifest 200': (r) => r.status === 200 });
  errorRate.add(!ok);
  if (!ok) {
    sleep(SEGMENT_LOOP_SEC);
    return;
  }

  const base = data.playlistUrl.replace(/\/[^/]+$/, '/');
  const segments = parseSegments(manifestRes.body);
  const segDur = 2.0;

  for (const seg of segments.slice(-3)) {
    const url = seg.startsWith('http') ? seg : base + seg;
    const segRes = http.get(url, { tags: { name: 'segment' } });
    segmentDuration.add(segRes.timings.duration);
    const segOk = check(segRes, { 'segment 200': (r) => r.status === 200 });
    errorRate.add(!segOk);
    if (segOk && segRes.body) {
      const bytes = segRes.body.length;
      segmentSize.add(bytes);
      const bps = (bytes * 8) / segDur;
      bitrateEstimate.add(bps / 1000);
      if (segRes.timings.duration > segDur * 2000) {
        stallDetected.add(1);
      }
    }
  }

  sleep(SEGMENT_LOOP_SEC);
}
