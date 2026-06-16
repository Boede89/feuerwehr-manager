(function () {
  'use strict';

  function normalizeUid(raw) {
    var cleaned = String(raw || '').trim().replace(/[\s:\-]/g, '').toUpperCase();
    if (/^[0-9A-F]{4,128}$/.test(cleaned)) {
      return cleaned;
    }
    var match = cleaned.match(/[0-9A-F]{4,128}/);
    return match ? match[0] : null;
  }

  function parseLines(buffer, chunkText, onLine) {
    var combined = buffer + chunkText;
    var lines = combined.split(/\r\n|\n|\r/);
    var rest = lines.pop() || '';
    lines.forEach(onLine);
    return rest;
  }

  async function captureOnce(targetInput) {
    if (!window.isSecureContext || !('serial' in navigator)) {
      throw new Error('Web Serial nur über HTTPS in Chrome/Brave verfügbar.');
    }
    var port = await navigator.serial.requestPort();
    await port.open({ baudRate: 9600 });
    var reader = port.readable.getReader();
    var decoder = new TextDecoder();
    var buffer = '';
    try {
      while (true) {
        var result = await reader.read();
        if (result.done) {
          break;
        }
        buffer = parseLines(buffer, decoder.decode(result.value), function (line) {
          var uid = normalizeUid(line);
          if (uid) {
            targetInput.value = uid;
            if (typeof window.toast === 'function') {
              window.toast('Chip gelesen: ' + uid, 'success');
            }
            throw new Error('__RFID_DONE__');
          }
        });
      }
    } catch (err) {
      if (err && err.message !== '__RFID_DONE__') {
        throw err;
      }
    } finally {
      try { reader.releaseLock(); } catch (e) { /* ignore */ }
      try { await port.close(); } catch (e) { /* ignore */ }
    }
  }

  function bindButton(btn) {
    var selector = btn.getAttribute('data-rfid-target');
    if (!selector) {
      return;
    }
    var input = document.querySelector(selector);
    if (!input) {
      return;
    }
    btn.addEventListener('click', async function () {
      var oldText = btn.textContent;
      btn.disabled = true;
      btn.textContent = 'Warte auf Chip …';
      try {
        await captureOnce(input);
      } catch (err) {
        var msg = err && err.message ? err.message : 'Chip konnte nicht gelesen werden.';
        if (typeof window.toast === 'function') {
          window.toast(msg, 'error');
        }
      } finally {
        btn.disabled = false;
        btn.textContent = oldText;
      }
    });
  }

  Array.prototype.forEach.call(document.querySelectorAll('[data-rfid-target]'), bindButton);
})();
