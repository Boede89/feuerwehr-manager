(function () {
  'use strict';

  function meta() {
    var el = document.getElementById('schnittstellen-meta');
    if (!el) {
      return null;
    }
    return {
      unitId: el.getAttribute('data-unit-id'),
    };
  }

  function setStatus(text, isError) {
    var status = document.getElementById('schnittstellen-status');
    if (!status) {
      return;
    }
    status.textContent = text || '';
    status.classList.toggle('hint--warn', !!isError);
    status.hidden = !text;
  }

  function refresh() {
    var m = meta();
    if (!m || !m.unitId) {
      return;
    }
    var btn = document.getElementById('btn-schnittstellen-refresh');
    if (btn) {
      btn.disabled = true;
      btn.textContent = 'Lade …';
    }
    setStatus('Lade DIVERA-API …', false);
    fetch('/test-schnittstellen/api/raw?unit=' + encodeURIComponent(m.unitId), {
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
    })
      .then(function (res) {
        if (!res.ok) {
          throw new Error('HTTP ' + res.status);
        }
        return res.json();
      })
      .then(function (data) {
        var alarmsEl = document.getElementById('schnittstellen-alarms-json');
        var usersEl = document.getElementById('schnittstellen-users-json');
        if (alarmsEl) {
          alarmsEl.value = data.alarmsJson || '';
        }
        if (usersEl) {
          usersEl.value = data.usersJson || '';
        }
        setStatus(data.ok ? data.message || 'Aktualisiert' : data.message || 'Fehler', !data.ok);
        if (typeof window.showToast === 'function') {
          window.showToast(data.ok ? 'DIVERA-Daten aktualisiert' : (data.message || 'Fehler'), data.ok ? 'success' : 'error');
        }
      })
      .catch(function (err) {
        setStatus('Abruf fehlgeschlagen: ' + (err.message || err), true);
        if (typeof window.showToast === 'function') {
          window.showToast('DIVERA-Abruf fehlgeschlagen', 'error');
        }
      })
      .finally(function () {
        if (btn) {
          btn.disabled = false;
          btn.textContent = 'Von DIVERA aktualisieren';
        }
      });
  }

  document.addEventListener('DOMContentLoaded', function () {
    var btn = document.getElementById('btn-schnittstellen-refresh');
    if (btn) {
      btn.addEventListener('click', refresh);
    }
  });
})();
