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

  document.querySelectorAll('[data-action="smtp-test-unit"]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var unitId = getUnitId();
      if (!unitId) return;
      var smtpId = btn.getAttribute('data-smtp-id');
      var params = { unit: String(unitId) };
      if (smtpId) params.smtpAccountId = smtpId;
      btn.disabled = true;
      postJson('/admin/rest/unit/smtp/test', params)
        .then(function (res) {
          showResult(res.data);
        })
        .catch(function () {
          showResult({ ok: false, message: 'SMTP-Test fehlgeschlagen' });
        })
        .finally(function () {
          btn.disabled = false;
        });
    });
  });

  function reopenModalIfRequested(openModalKey, modalId) {
    var urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('openModal') !== openModalKey) return;
    var modal = document.getElementById(modalId);
    if (modal) {
      modal.classList.add('active');
      document.body.classList.add('modal-open');
    }
    urlParams.delete('openModal');
    var qs = urlParams.toString();
    var next = window.location.pathname + (qs ? '?' + qs : '');
    window.history.replaceState({}, '', next);
  }

  reopenModalIfRequested('divera-recipient-groups', 'modal-divera-recipient-groups');
  reopenModalIfRequested('divera-status-ids', 'modal-divera-status-ids');

  function getJson(url) {
    return fetch(url, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
        'X-XSRF-TOKEN': getCsrfToken(),
      },
      credentials: 'same-origin',
    }).then(function (r) {
      return r.json().then(function (data) {
        return { ok: r.ok, data: data };
      });
    });
  }

  function syncPrintModeUi() {
    var cupsSection = document.getElementById('print-cups-section');
    var cupsRadio = document.querySelector('input[name="printMode"][value="CUPS"]');
    if (!cupsSection) return;
    cupsSection.style.display = cupsRadio && cupsRadio.checked ? '' : 'none';
  }

  document.querySelectorAll('input[name="printMode"]').forEach(function (radio) {
    radio.addEventListener('change', syncPrintModeUi);
  });
  syncPrintModeUi();

  document.getElementById('cupsPrinterManual')?.addEventListener('input', function () {
    var sel = document.getElementById('cupsPrinterName');
    if (!sel) return;
    var manual = this.value.trim();
    if (manual) {
      var found = false;
      for (var i = 0; i < sel.options.length; i++) {
        if (sel.options[i].value === manual) {
          sel.selectedIndex = i;
          found = true;
          break;
        }
      }
      if (!found) {
        var opt = document.createElement('option');
        opt.value = manual;
        opt.textContent = manual;
        opt.selected = true;
        sel.appendChild(opt);
      }
    }
  });

  document.getElementById('form-print-settings')?.addEventListener('submit', function () {
    var manual = document.getElementById('cupsPrinterManual');
    var sel = document.getElementById('cupsPrinterName');
    if (manual && sel && manual.value.trim()) {
      sel.value = manual.value.trim();
    }
  });

  document.getElementById('btn-load-cups-printers')?.addEventListener('click', function () {
    var unitId = getUnitId();
    if (!unitId) return;
    var btn = document.getElementById('btn-load-cups-printers');
    var sel = document.getElementById('cupsPrinterName');
    if (!sel) return;
    if (btn) btn.disabled = true;
    var server = document.getElementById('cupsServer');
    var url = '/admin/rest/unit/print/printers?unit=' + encodeURIComponent(unitId);
    if (server && server.value.trim()) {
      url += '&cupsServer=' + encodeURIComponent(server.value.trim());
    }
    getJson(url)
      .then(function (res) {
        var data = res.data || {};
        if (!data.ok) {
          showResult({ ok: false, message: data.message || 'Druckerliste konnte nicht geladen werden.' });
          return;
        }
        if (!data.cupsAvailable && data.message) {
          showResult({ ok: false, message: data.message });
          return;
        }
        var current = sel.value || (document.getElementById('cupsPrinterManual')?.value || '').trim();
        sel.innerHTML = '<option value="">— Drucker wählen —</option>';
        (data.printers || []).forEach(function (p) {
          var opt = document.createElement('option');
          opt.value = p.name;
          opt.textContent = p.display || p.name;
          if (current && p.name === current) opt.selected = true;
          sel.appendChild(opt);
        });
        if (current && !Array.from(sel.options).some(function (o) { return o.value === current; })) {
          var custom = document.createElement('option');
          custom.value = current;
          custom.textContent = current;
          custom.selected = true;
          sel.appendChild(custom);
        }
        var count = (data.printers || []).length;
        showResult({
          ok: count > 0,
          message:
            data.message ||
            (count === 0
              ? 'Keine Drucker gefunden — CUPS-Server und Warteschlange prüfen.'
              : count + ' Drucker geladen.'),
        });
      })
      .catch(function () {
        showResult({ ok: false, message: 'Druckerliste konnte nicht geladen werden.' });
      })
      .finally(function () {
        if (btn) btn.disabled = false;
      });
  });

  document.getElementById('btn-test-print')?.addEventListener('click', function () {
    var unitId = getUnitId();
    if (!unitId) return;
    var btn = document.getElementById('btn-test-print');
    if (btn) btn.disabled = true;
    postJson('/admin/rest/unit/print/test', { unit: String(unitId) })
      .then(function (res) {
        showResult(res.data);
      })
      .catch(function () {
        showResult({ ok: false, message: 'Testdruck fehlgeschlagen' });
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
