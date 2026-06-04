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

  function notify(msg, type) {
    if (typeof window.toast === 'function') {
      window.toast(msg, type || 'success');
    } else {
      window.alert(msg);
    }
  }

  function buildWebhookUrl(base, unitId, secret) {
    var root = base && String(base).trim() ? String(base).replace(/\/+$/, '') : window.location.origin;
    var url = root + '/api/webhook/divera?unit=' + encodeURIComponent(unitId);
    if (!secret) {
      return url + '&secret=<DEIN_SECRET>';
    }
    return url + '&secret=' + encodeURIComponent(secret);
  }

  function resolveWebhookUrlForCopy() {
    var el = document.getElementById('diveraWebhookUrl');
    var meta = document.getElementById('integrations-meta');
    if (!el || !meta) return '';
    var unitId = meta.getAttribute('data-unit-id');
    if (!unitId) return el.value.trim();
    var secretEl = document.getElementById('webhookSecret');
    var typedSecret = secretEl && secretEl.value.trim();
    if (typedSecret) {
      var built = buildWebhookUrl(meta.getAttribute('data-app-base') || '', unitId, typedSecret);
      el.value = built;
      return built;
    }
    return el.value.trim();
  }

  function copyText(text) {
    if (!text) {
      return Promise.reject(new Error('empty'));
    }
    if (navigator.clipboard && window.isSecureContext) {
      return navigator.clipboard.writeText(text);
    }
    return new Promise(function (resolve, reject) {
      var ta = document.createElement('textarea');
      ta.value = text;
      ta.setAttribute('readonly', '');
      ta.style.position = 'fixed';
      ta.style.left = '-9999px';
      document.body.appendChild(ta);
      ta.focus();
      ta.select();
      ta.setSelectionRange(0, text.length);
      try {
        var ok = document.execCommand('copy');
        document.body.removeChild(ta);
        if (ok) {
          resolve();
        } else {
          reject(new Error('execCommand failed'));
        }
      } catch (err) {
        document.body.removeChild(ta);
        reject(err);
      }
    });
  }

  document.getElementById('btn-copy-divera-url')?.addEventListener('click', function (ev) {
    ev.preventDefault();
    var el = document.getElementById('diveraWebhookUrl');
    var text = resolveWebhookUrlForCopy();
    if (!text || text.indexOf('<DEIN_SECRET>') >= 0) {
      notify(
        'Bitte Webhook-Secret eintragen und speichern (und ggf. App-URL unter Global → Konfiguration).',
        'warning'
      );
      if (el) {
        el.focus();
        el.select();
      }
      return;
    }
    copyText(text)
      .then(function () {
        notify('Webhook-URL kopiert', 'success');
      })
      .catch(function () {
        if (el) {
          el.focus();
          el.select();
        }
        notify('URL markiert — bitte Strg+C (Cmd+C) zum Kopieren verwenden.', 'warning');
      });
  });

  document.getElementById('webhookSecret')?.addEventListener('input', function () {
    var meta = document.getElementById('integrations-meta');
    var el = document.getElementById('diveraWebhookUrl');
    if (!meta || !el) return;
    var unitId = meta.getAttribute('data-unit-id');
    if (!unitId) return;
    var secret = this.value.trim();
    if (secret) {
      el.value = buildWebhookUrl(meta.getAttribute('data-app-base') || '', unitId, secret);
    }
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
