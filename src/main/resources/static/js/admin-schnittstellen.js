(function () {
  function getCsrfToken() {
    var meta = document.getElementById('integrations-meta');
    if (meta) {
      var fromMeta = meta.getAttribute('data-csrf-token');
      if (fromMeta) return fromMeta;
    }
    var match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : '';
  }

  function getUnitId() {
    var meta = document.getElementById('integrations-meta');
    if (!meta) return null;
    var id = meta.getAttribute('data-unit-id');
    return id ? parseInt(id, 10) : null;
  }

  function postJson(url, params) {
    var body = new URLSearchParams(params || {});
    return fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'X-XSRF-TOKEN': getCsrfToken(),
        Accept: 'application/json',
      },
      credentials: 'same-origin',
      body: body.toString(),
    }).then(function (r) {
      return r.json().then(function (data) {
        return { ok: r.ok, data: data };
      });
    });
  }

  function showResult(data) {
    if (typeof window.toast === 'function') {
      window.toast(data.message || (data.ok ? 'OK' : 'Fehler'), data.ok ? 'success' : 'error');
    }
  }

  document.getElementById('btn-copy-divera-url')?.addEventListener('click', function () {
    var el = document.getElementById('diveraWebhookUrl');
    if (!el || !el.value) return;
    navigator.clipboard.writeText(el.value).then(function () {
      if (typeof window.toast === 'function') {
        window.toast('Webhook-URL kopiert', 'success');
      }
    });
  });

  document.getElementById('btn-test-divera')?.addEventListener('click', function () {
    var unitId = getUnitId();
    if (!unitId) return;
    var btn = document.getElementById('btn-test-divera');
    if (btn) btn.disabled = true;
    postJson('/admin/rest/unit/divera/test', { unit: String(unitId) })
      .then(function (res) {
        showResult(res.data);
      })
      .catch(function () {
        showResult({ ok: false, message: 'Anfrage fehlgeschlagen' });
      })
      .finally(function () {
        if (btn) btn.disabled = false;
      });
  });

  document.getElementById('btn-import-divera')?.addEventListener('click', function () {
    var unitId = getUnitId();
    if (!unitId) return;
    var btn = document.getElementById('btn-import-divera');
    if (btn) btn.disabled = true;
    postJson('/admin/rest/unit/divera/import', { unit: String(unitId) })
      .then(function (res) {
        showResult(res.data);
      })
      .catch(function () {
        showResult({ ok: false, message: 'Import fehlgeschlagen' });
      })
      .finally(function () {
        if (btn) btn.disabled = false;
      });
  });

  document.getElementById('btn-test-unit-smtp')?.addEventListener('click', function () {
    var unitId = getUnitId();
    if (!unitId) return;
    var btn = document.getElementById('btn-test-unit-smtp');
    if (btn) btn.disabled = true;
    postJson('/admin/rest/unit/smtp/test', { unit: String(unitId) })
      .then(function (res) {
        showResult(res.data);
      })
      .catch(function () {
        showResult({ ok: false, message: 'SMTP-Test fehlgeschlagen' });
      })
      .finally(function () {
        if (btn) btn.disabled = false;
      });
  });

  document.getElementById('btn-test-global-smtp')?.addEventListener('click', function () {
    var btn = document.getElementById('btn-test-global-smtp');
    if (btn) btn.disabled = true;
    postJson('/admin/rest/global/smtp/test', {})
      .then(function (res) {
        showResult(res.data);
      })
      .catch(function () {
        showResult({ ok: false, message: 'SMTP-Test fehlgeschlagen' });
      })
      .finally(function () {
        if (btn) btn.disabled = false;
      });
  });
})();
