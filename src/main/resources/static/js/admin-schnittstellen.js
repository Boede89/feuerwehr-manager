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
        'Bitte zuerst ein Webhook-Secret hinterlegen (und ggf. App-URL unter Global → Konfiguration).',
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

  document.querySelectorAll('[data-action="calendar-test-unit"]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var unitId = getUnitId();
      if (!unitId) return;
      var calendarId = btn.getAttribute('data-calendar-id');
      if (!calendarId) return;
      btn.disabled = true;
      postJson('/admin/rest/unit/calendar/test', {
        unit: String(unitId),
        calendarAccountId: calendarId,
      })
        .then(function (res) {
          showResult(res.data);
        })
        .catch(function () {
          showResult({ ok: false, message: 'Kalender-Test fehlgeschlagen' });
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
  reopenModalIfRequested('leitstellen-mail', 'modal-leitstellen-mail');
  reopenModalIfRequested('print-settings', 'modal-print-settings');

  document.querySelectorAll('[data-open-modal="modal-divera-access-key"], [data-open-modal="modal-divera-webhook-secret"]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var modalId = btn.getAttribute('data-open-modal');
      var modal = document.getElementById(modalId);
      if (!modal) return;
      var input = modal.querySelector('input[type="password"]');
      if (input) {
        input.value = '';
      }
    });
  });

  if (window.location.hash) {
    var anchor = document.querySelector(window.location.hash);
    if (anchor) {
      setTimeout(function () {
        anchor.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }, 80);
    }
  }

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

  function initGlobalSmtpCopy() {
    var unitSelect = document.getElementById('smtpCopyUnitId');
    var accountWrap = document.getElementById('smtp-copy-account-wrap');
    var accountSelect = document.getElementById('smtpCopyAccountId');
    if (!unitSelect || !accountSelect) {
      return;
    }

    function resetAccounts() {
      accountSelect.innerHTML = '<option value="">Erstes konfiguriertes Konto</option>';
      if (accountWrap) {
        accountWrap.hidden = true;
      }
    }

    unitSelect.addEventListener('change', function () {
      resetAccounts();
      var unitId = unitSelect.value;
      if (!unitId) {
        return;
      }
      fetch('/admin/rest/global/smtp/unit-accounts?unit=' + encodeURIComponent(unitId), {
        credentials: 'same-origin',
        headers: { Accept: 'application/json', 'X-XSRF-TOKEN': getCsrfToken() },
      })
        .then(function (res) {
          if (!res.ok) {
            throw new Error('load failed');
          }
          return res.json();
        })
        .then(function (accounts) {
          if (!Array.isArray(accounts) || accounts.length <= 1) {
            return;
          }
          accountSelect.innerHTML = '';
          accounts.forEach(function (account, index) {
            var opt = document.createElement('option');
            opt.value = String(account.id);
            opt.textContent = account.label || ('SMTP #' + account.id);
            if (index === 0) {
              opt.selected = true;
            }
            accountSelect.appendChild(opt);
          });
          if (accountWrap) {
            accountWrap.hidden = false;
          }
        })
        .catch(function () {
          resetAccounts();
        });
    });
  }

  initGlobalSmtpCopy();

  function escHtml(text) {
    var div = document.createElement('div');
    div.textContent = text == null ? '' : String(text);
    return div.innerHTML;
  }

  function openLeitstellenPollModal(data) {
    var summary = document.getElementById('leitstellen-poll-summary');
    var tbody = document.getElementById('leitstellen-poll-tbody');
    var tableWrap = document.getElementById('leitstellen-poll-table-wrap');
    var empty = document.getElementById('leitstellen-poll-empty');
    var modal = document.getElementById('modal-leitstellen-poll-result');
    if (!summary || !tbody || !modal) {
      showResult(data);
      return;
    }
    summary.textContent = data.message || 'Abruf abgeschlossen.';
    var imports = Array.isArray(data.imports) ? data.imports : [];
    tbody.innerHTML = '';
    var unitId = getUnitId();
    imports.forEach(function (item) {
      var reportLabel = item.incidentNumber || '—';
      var href =
        unitId && item.reportId
          ? '/berichte/einsatzberichte/' + encodeURIComponent(item.reportId) + '/bearbeiten?unit=' + encodeURIComponent(unitId)
          : '';
      var tr = document.createElement('tr');
      tr.innerHTML =
        '<td>' + escHtml(reportLabel) + '</td>' +
        '<td>' + escHtml(item.incidentDate || '—') + '</td>' +
        '<td>' + escHtml(item.stichwort || '—') + '</td>' +
        '<td>' + escHtml(item.kind || '—') + '</td>' +
        '<td title="' + escHtml(item.sourceFilename || '') + '">' + escHtml(item.storedFilename || '—') + '</td>' +
        '<td>' +
        (href
          ? '<a class="btn btn--outline btn--sm" href="' + href + '" target="_blank" rel="noopener">Öffnen</a>'
          : '') +
        '</td>';
      tbody.appendChild(tr);
    });
    if (tableWrap) tableWrap.hidden = imports.length === 0;
    if (empty) empty.hidden = imports.length > 0;
    modal.classList.add('active');
    document.body.classList.add('modal-open');
    if (typeof window.toast === 'function') {
      var toastMsg =
        data.ok === false
          ? data.message || 'Abruf fehlgeschlagen'
          : imports.length
            ? imports.length + ' Datei(en) angehängt'
            : data.message || 'Abruf abgeschlossen';
      window.toast(toastMsg, data.ok === false ? 'error' : 'success');
    }
  }

  document.getElementById('btn-leitstellen-poll')?.addEventListener('click', function () {
    var unitId = getUnitId();
    if (!unitId) return;
    var btn = document.getElementById('btn-leitstellen-poll');
    if (btn) {
      btn.disabled = true;
      btn.textContent = 'Abruf läuft…';
    }
    postJson('/admin/rest/unit/leitstellen-mail/poll', { unit: String(unitId) })
      .then(function (res) {
        openLeitstellenPollModal(res.data || { ok: false, message: 'Keine Antwort', imports: [] });
      })
      .catch(function () {
        openLeitstellenPollModal({ ok: false, message: 'Abruf fehlgeschlagen', imports: [] });
      })
      .finally(function () {
        if (btn) {
          btn.disabled = false;
          btn.textContent = 'Jetzt abrufen';
        }
      });
  });
})();
