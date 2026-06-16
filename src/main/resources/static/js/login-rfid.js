(function () {
  'use strict';

  var root = document.getElementById('login-rfid');
  if (!root) {
    return;
  }

  var DEFAULT_BAUD = Number(root.dataset.serialBaud || 9600);
  var statusEl = document.getElementById('login-rfid-status');
  var hintEl = document.getElementById('login-rfid-hint');
  var connectBtn = document.getElementById('login-rfid-connect');
  var unknownModal = document.getElementById('modal-rfid-register-unknown');
  var unknownForm = document.getElementById('form-rfid-register-unknown');
  var unknownChipEl = document.getElementById('unknown-rfid-chip');
  var unknownErrorEl = document.getElementById('unknown-rfid-error');
  var unknownCloseBtn = document.getElementById('modal-rfid-register-close');
  var unknownCancelBtn = document.getElementById('modal-rfid-register-cancel');
  var busy = false;
  var lastHandledUid = '';
  var lastHandledAt = 0;
  var serialPort = null;
  var readLoopActive = false;
  var pendingUnknownChipUid = '';

  function getCsrfToken() {
    var meta = document.querySelector('meta[name="csrf-token"]');
    if (meta && meta.getAttribute('content')) {
      return meta.getAttribute('content');
    }
    var match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : '';
  }

  function getCsrfHeader() {
    var meta = document.querySelector('meta[name="csrf-header"]');
    return meta && meta.getAttribute('content') ? meta.getAttribute('content') : 'X-XSRF-TOKEN';
  }

  function setStatus(text, kind) {
    if (!statusEl) {
      return;
    }
    statusEl.textContent = text;
    statusEl.className = 'login-rfid__status' + (kind ? ' login-rfid__status--' + kind : '');
  }

  function setHint(text) {
    if (hintEl) {
      hintEl.textContent = text;
    }
  }

  function normalizeUid(raw) {
    var cleaned = String(raw).trim().replace(/[\s:\-]/g, '').toUpperCase();
    if (/^[0-9A-F]{4,128}$/.test(cleaned)) {
      return cleaned;
    }
    var match = cleaned.match(/[0-9A-F]{4,128}/);
    return match ? match[0] : null;
  }

  function shouldHandle(uid) {
    if (!uid) {
      return false;
    }
    var now = Date.now();
    return !(uid === lastHandledUid && now - lastHandledAt < 8000);
  }

  function markHandled(uid) {
    lastHandledUid = uid;
    lastHandledAt = Date.now();
  }

  function openUnknownChipModal(cardUid) {
    pendingUnknownChipUid = cardUid;
    if (unknownChipEl) {
      unknownChipEl.textContent = 'Chip: ' + cardUid;
    }
    if (unknownErrorEl) {
      unknownErrorEl.hidden = true;
      unknownErrorEl.textContent = '';
    }
    if (unknownModal) {
      unknownModal.classList.add('active');
      document.body.classList.add('modal-open');
    }
  }

  function closeUnknownChipModal() {
    if (unknownModal) {
      unknownModal.classList.remove('active');
      document.body.classList.remove('modal-open');
    }
  }

  function showUnknownError(message) {
    if (!unknownErrorEl) {
      return;
    }
    if (!message) {
      unknownErrorEl.hidden = true;
      unknownErrorEl.textContent = '';
      return;
    }
    unknownErrorEl.textContent = message;
    unknownErrorEl.hidden = false;
  }

  function loginWithUid(cardUid) {
    if (busy || !shouldHandle(cardUid)) {
      return;
    }
    busy = true;
    setStatus('Chip erkannt – Anmeldung …', 'busy');

    var headers = { 'Content-Type': 'application/json' };
    var csrf = getCsrfToken();
    if (csrf) {
      headers[getCsrfHeader()] = csrf;
    }

    fetch('/api/v1/auth/rfid', {
      method: 'POST',
      credentials: 'same-origin',
      headers: headers,
      body: JSON.stringify({ cardUid: cardUid })
    })
      .then(function (res) {
        return res.json().then(function (body) {
          return { ok: res.ok, body: body };
        });
      })
      .then(function (result) {
        if (!result.ok || !result.body || !result.body.success) {
          markHandled(cardUid);
          var code = result.body && result.body.errorCode;
          var msg = (result.body && result.body.message) || 'Anmeldung fehlgeschlagen';
          if (code === 'unknown_chip') {
            setStatus('Unbekannter Chip – Registrierung erforderlich', 'warn');
            setHint('Bitte im eingeblendeten Fenster Benutzername und Passwort eingeben.');
            openUnknownChipModal(cardUid);
            if (typeof window.toast === 'function') {
              window.toast('Unbekannter Chip erkannt. Bitte im Fenster bestätigen.', 'warning');
            }
            busy = false;
            return;
          }
          setStatus(msg, 'error');
          if (typeof window.toast === 'function') {
            window.toast(msg, 'error');
          }
          busy = false;
          return;
        }
        markHandled(cardUid);
        if (result.body.totpRequired) {
          setStatus('Zweiter Faktor erforderlich …', 'ok');
        } else {
          setStatus('Erfolgreich – weiterleiten …', 'ok');
        }
        window.location.href = result.body.redirectUrl || '/';
      })
      .catch(function () {
        setStatus('Verbindung zum Server fehlgeschlagen', 'error');
        busy = false;
      });
  }

  function registerUnknownChipWithCredentials(username, password, cardUid) {
    var headers = { 'Content-Type': 'application/json' };
    var csrf = getCsrfToken();
    if (csrf) {
      headers[getCsrfHeader()] = csrf;
    }
    return fetch('/api/v1/auth/rfid/register-unknown', {
      method: 'POST',
      credentials: 'same-origin',
      headers: headers,
      body: JSON.stringify({
        cardUid: cardUid,
        username: username,
        password: password
      })
    }).then(function (res) {
      return res.json().then(function (body) {
        return { ok: res.ok, body: body };
      });
    });
  }

  function processChunk(buffer, chunk) {
    var combined = buffer + new TextDecoder().decode(chunk);
    var parts = combined.split(/\r\n|\n|\r/);
    var rest = parts.pop() || '';
    parts.forEach(function (line) {
      var uid = normalizeUid(line);
      if (uid) {
        loginWithUid(uid);
      }
    });
    return rest;
  }

  async function readSerialLoop() {
    if (!serialPort || !serialPort.readable || readLoopActive) {
      return;
    }
    readLoopActive = true;
    var buffer = '';
    try {
      while (serialPort.readable) {
        var reader = serialPort.readable.getReader();
        try {
          while (true) {
            var result = await reader.read();
            if (result.done) {
              break;
            }
            buffer = processChunk(buffer, result.value);
          }
        } finally {
          reader.releaseLock();
        }
      }
    } catch (err) {
      if (serialPort) {
        setStatus('Lesefehler – bitte neu verbinden', 'error');
        setHint(String(err && err.message ? err.message : err));
      }
    } finally {
      readLoopActive = false;
    }
  }

  async function openPort(port) {
    if (!port) {
      return;
    }
    if (serialPort && serialPort !== port) {
      try {
        await serialPort.close();
      } catch (e) {
        /* ignore */
      }
    }
    serialPort = port;
    if (!serialPort.readable) {
      await serialPort.open({ baudRate: DEFAULT_BAUD });
    }
    setStatus('Bereit – Chip auflegen', 'ok');
    setHint('Lesegerät verbunden. Chip auf den Reader legen.');
    if (connectBtn) {
      connectBtn.textContent = 'Lesegerät verbunden';
      connectBtn.disabled = true;
    }
    readSerialLoop();
  }

  async function connectReader() {
    if (!('serial' in navigator)) {
      setStatus('Web Serial nicht verfügbar', 'error');
      setHint('Bitte Chrome oder Brave nutzen und die Seite über HTTPS öffnen.');
      return;
    }
    try {
      setStatus('Warte auf Gerätewahl …', 'busy');
      var port = await navigator.serial.requestPort();
      await openPort(port);
    } catch (err) {
      setStatus('Verbindung abgebrochen', 'warn');
      setHint('Erneut auf „Lesegerät verbinden“ klicken und den COM-Port wählen.');
    }
  }

  async function tryAutoConnect() {
    if (!('serial' in navigator)) {
      setStatus('Web Serial nicht verfügbar', 'error');
      setHint('RFID-Login erfordert HTTPS sowie Chrome oder Brave.');
      if (connectBtn) {
        connectBtn.disabled = true;
      }
      return;
    }
    if (!window.isSecureContext) {
      setStatus('HTTPS erforderlich', 'error');
      setHint('Bitte https://… in der Adresszeile nutzen (nicht http://).');
      return;
    }
    try {
      var ports = await navigator.serial.getPorts();
      if (ports.length > 0) {
        setStatus('Verbinde Lesegerät …', 'busy');
        await openPort(ports[0]);
        return;
      }
    } catch (err) {
      /* getPorts failed – manual connect */
    }
    setStatus('Lesegerät noch nicht verbunden', 'warn');
    setHint('Einmal auf „Lesegerät verbinden“ klicken und den COM-Port erlauben.');
  }

  if (connectBtn) {
    connectBtn.addEventListener('click', connectReader);
  }

  if (unknownForm) {
    unknownForm.addEventListener('submit', function (e) {
      e.preventDefault();
      if (!pendingUnknownChipUid) {
        showUnknownError('Keine Chip-ID vorhanden.');
        return;
      }
      var usernameInput = document.getElementById('unknown-rfid-username');
      var passwordInput = document.getElementById('unknown-rfid-password');
      var username = usernameInput ? usernameInput.value.trim() : '';
      var password = passwordInput ? passwordInput.value : '';
      if (!username || !password) {
        showUnknownError('Bitte Benutzername und Passwort eingeben.');
        return;
      }
      showUnknownError('');
      registerUnknownChipWithCredentials(username, password, pendingUnknownChipUid)
        .then(function (result) {
          if (!result.ok || !result.body || !result.body.success) {
            showUnknownError((result.body && result.body.message) || 'Anmeldung fehlgeschlagen.');
            return;
          }
          closeUnknownChipModal();
          setStatus('Chip registriert – weiterleiten …', 'ok');
          window.location.href = result.body.redirectUrl || '/';
        })
        .catch(function () {
          showUnknownError('Verbindung fehlgeschlagen.');
        });
    });
  }

  if (unknownCloseBtn) {
    unknownCloseBtn.addEventListener('click', closeUnknownChipModal);
  }
  if (unknownCancelBtn) {
    unknownCancelBtn.addEventListener('click', closeUnknownChipModal);
  }

  window.addEventListener('beforeunload', function () {
    if (serialPort) {
      serialPort.close().catch(function () {
        /* ignore */
      });
    }
  });

  tryAutoConnect();
})();
