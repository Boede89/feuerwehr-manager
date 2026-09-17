(function () {
  function readMinutes() {
    var form = document.getElementById('idle-logout-form');
    var raw = (form && form.getAttribute('data-idle-logout-minutes'))
      || (document.body && document.body.getAttribute('data-idle-logout-minutes'))
      || '0';
    var minutes = Number(raw);
    if (!Number.isFinite(minutes) || minutes <= 0) {
      return 0;
    }
    return minutes;
  }

  var minutes = readMinutes();
  if (minutes <= 0) {
    return;
  }

  var timeoutMs = minutes * 60 * 1000;
  var warnMs = Math.min(20 * 1000, Math.max(10 * 1000, Math.floor(timeoutMs / 4)));
  var lastActivity = Date.now();
  var lastKeepalive = 0;
  var loggingOut = false;
  var warningVisible = false;
  var KEEPALIVE_EVERY_MS = 20000;

  var warning = document.getElementById('idle-logout-warning');
  var warningText = document.getElementById('idle-logout-warning-text');
  var stayButton = document.getElementById('idle-logout-stay');

  function logout() {
    if (loggingOut) {
      return;
    }
    loggingOut = true;
    var form = document.getElementById('idle-logout-form');
    if (form) {
      if (typeof form.requestSubmit === 'function') {
        form.requestSubmit();
      } else {
        form.submit();
      }
      return;
    }
    window.location.href = '/login?expired=1';
  }

  function remainingMs() {
    return timeoutMs - (Date.now() - lastActivity);
  }

  function hideWarning() {
    if (!warning || !warningVisible) {
      return;
    }
    warning.hidden = true;
    warningVisible = false;
  }

  function showWarning(secondsLeft) {
    if (!warning || !warningText) {
      return;
    }
    var seconds = Math.max(1, secondsLeft);
    warningText.textContent = 'Keine Aktivität. Sie werden in ' + seconds
      + (seconds === 1 ? ' Sekunde' : ' Sekunden') + ' automatisch abgemeldet.';
    warning.hidden = false;
    warningVisible = true;
  }

  function sendKeepalive() {
    var now = Date.now();
    if (now - lastKeepalive < KEEPALIVE_EVERY_MS) {
      return;
    }
    lastKeepalive = now;
    var tokenMeta = document.querySelector('meta[name="csrf-token"]');
    var headerMeta = document.querySelector('meta[name="csrf-header"]');
    var headers = { Accept: 'application/json' };
    if (tokenMeta && headerMeta) {
      headers[headerMeta.getAttribute('content')] = tokenMeta.getAttribute('content');
    }
    fetch('/session/keepalive', {
      method: 'POST',
      headers: headers,
      credentials: 'same-origin'
    }).then(function (res) {
      if (res.status === 401) {
        logout();
      }
    }).catch(function () {
      /* lokale Zeit läuft weiter */
    });
  }

  function markActivity() {
    if (loggingOut) {
      return;
    }
    lastActivity = Date.now();
    hideWarning();
    sendKeepalive();
  }

  function tick() {
    if (loggingOut) {
      return;
    }
    var left = remainingMs();
    if (left <= 0) {
      logout();
      return;
    }
    if (left <= warnMs) {
      showWarning(Math.ceil(left / 1000));
    } else {
      hideWarning();
    }
  }

  ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart', 'wheel', 'click'].forEach(function (evt) {
    document.addEventListener(evt, markActivity, { passive: true, capture: true });
  });
  document.addEventListener('visibilitychange', tick);
  if (stayButton) {
    stayButton.addEventListener('click', function (event) {
      event.preventDefault();
      markActivity();
    });
  }

  window.setInterval(tick, 1000);
  tick();
})();
