(function () {
  var meta = document.getElementById('test-alarm-meta');
  if (!meta) return;

  var textarea = document.getElementById('testAlarmJson');
  var defaultSample = '';
  if (textarea) {
    defaultSample = textarea.value;
  }

  function getCsrfToken() {
    var fromMeta = meta.getAttribute('data-csrf-token');
    if (fromMeta) return fromMeta;
    var match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : '';
  }

  function notify(msg, type) {
    if (typeof window.toast === 'function') {
      window.toast(msg, type || 'success');
    }
  }

  document.getElementById('btn-test-alarm-sample')?.addEventListener('click', function () {
    if (!textarea) return;
    var sample = defaultSample;
    if (sample) {
      textarea.value = sample.trim();
      notify('Beispiel-JSON geladen', 'success');
    }
  });

  document.getElementById('btn-test-alarm-send')?.addEventListener('click', function () {
    if (!textarea) return;
    var unitId = meta.getAttribute('data-unit-id');
    var payload = textarea.value.trim();
    if (!unitId) {
      notify('Keine Einheit gewählt', 'error');
      return;
    }
    if (!payload) {
      notify('Bitte JSON eintragen', 'warning');
      return;
    }
    var btn = document.getElementById('btn-test-alarm-send');
    if (btn) btn.disabled = true;
    var body = new URLSearchParams();
    body.set('unit', unitId);
    body.set('payload', payload);
    fetch('/test-alarm/send', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'X-XSRF-TOKEN': getCsrfToken(),
        Accept: 'application/json',
      },
      credentials: 'same-origin',
      body: body.toString(),
    })
      .then(function (r) {
        return r.json().then(function (data) {
          return { httpOk: r.ok, data: data };
        });
      })
      .then(function (res) {
        var ok = res.data && res.data.ok;
        notify(res.data && res.data.message ? res.data.message : 'Fertig', ok ? 'success' : 'error');
        var result = document.getElementById('test-alarm-result');
        if (result) {
          result.hidden = false;
          result.textContent = res.data && res.data.message ? res.data.message : '';
          result.className = ok ? 'hint text-success' : 'hint text-danger';
        }
        if (ok) {
          window.setTimeout(function () {
            window.location.reload();
          }, 500);
        }
      })
      .catch(function () {
        notify('Anfrage fehlgeschlagen', 'error');
      })
      .finally(function () {
        if (btn) btn.disabled = false;
      });
  });

  function closeAlarm(testRecordId, triggerBtn) {
    var unitId = meta.getAttribute('data-unit-id');
    if (!unitId || !testRecordId) return;
    if (triggerBtn) triggerBtn.disabled = true;
    var body = new URLSearchParams();
    body.set('unit', unitId);
    body.set('id', String(testRecordId));
    fetch('/test-alarm/close', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'X-XSRF-TOKEN': getCsrfToken(),
        Accept: 'application/json',
      },
      credentials: 'same-origin',
      body: body.toString(),
    })
      .then(function (r) {
        return r.json();
      })
      .then(function (data) {
        var ok = data && data.ok;
        notify(data && data.message ? data.message : 'Fertig', ok ? 'success' : 'error');
        if (ok) {
          var row = triggerBtn && triggerBtn.closest('.list-row');
          if (row) row.remove();
          var card = document.getElementById('test-alarm-active-card');
          if (card && !card.querySelector('.list-row')) {
            card.remove();
          }
        }
      })
      .catch(function () {
        notify('Anfrage fehlgeschlagen', 'error');
      })
      .finally(function () {
        if (triggerBtn) triggerBtn.disabled = false;
      });
  }

  document.querySelectorAll('.btn-test-alarm-close').forEach(function (btn) {
    btn.addEventListener('click', function () {
      closeAlarm(btn.getAttribute('data-alarm-id'), btn);
    });
  });
})();
