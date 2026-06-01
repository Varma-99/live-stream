/**
 * Viewer presence via HTTP (join + heartbeat). Works across multiple tabs.
 */
window.Presence = (function () {
  const HEARTBEAT_MS = 2000;
  const BROADCASTER_HEARTBEAT_MS = 5000;

  function join(streamId) {
    return fetch('/streams/' + streamId + '/join', { method: 'POST' }).then(r => {
      if (!r.ok) return r.json().then(d => Promise.reject(new Error(d.error || 'Join failed')));
      return r.json();
    });
  }

  function heartbeat(streamId, presenceId) {
    return fetch('/streams/' + streamId + '/heartbeat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ presenceId }),
    }).then(r => {
      if (!r.ok) return r.json().then(d => Promise.reject(new Error(d.error || 'Heartbeat failed')));
      return r.json();
    });
  }

  function leave(streamId, presenceId) {
    if (!presenceId) return Promise.resolve();
    return fetch('/streams/' + streamId + '/leave', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ presenceId }),
      keepalive: true,
    }).catch(() => {});
  }

  function pollRoom(streamId) {
    return fetch('/streams/' + streamId + '/room').then(r => r.json());
  }

  function startHeartbeat(streamId, presenceId, onUpdate) {
    const tick = () => {
      heartbeat(streamId, presenceId)
        .then(onUpdate)
        .catch(() => {});
    };
    tick();
    const id = setInterval(tick, HEARTBEAT_MS);
    return () => clearInterval(id);
  }

  function broadcasterHeartbeat(streamId) {
    return fetch('/streams/' + streamId + '/broadcaster-heartbeat', { method: 'POST' }).then(r => {
      if (!r.ok && r.status !== 204) {
        return r.json().then(d => Promise.reject(new Error(d.error || 'Broadcaster heartbeat failed')));
      }
    });
  }

  function startBroadcasterPoll(streamId, onUpdate) {
    const tick = () => {
      pollRoom(streamId)
        .then(onUpdate)
        .catch(() => {});
    };
    tick();
    const id = setInterval(tick, HEARTBEAT_MS);
    return () => clearInterval(id);
  }

  function startBroadcasterHeartbeat(streamId) {
    const tick = () => {
      broadcasterHeartbeat(streamId).catch(() => {});
    };
    tick();
    const id = setInterval(tick, BROADCASTER_HEARTBEAT_MS);
    return () => clearInterval(id);
  }

  return {
    join,
    heartbeat,
    leave,
    pollRoom,
    startHeartbeat,
    startBroadcasterPoll,
    broadcasterHeartbeat,
    startBroadcasterHeartbeat,
  };
})();
