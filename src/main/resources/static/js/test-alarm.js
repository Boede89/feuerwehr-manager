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

  function escapeHtml(s) {
    if (!s) return '';
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function renderSamplesPageList(items) {
    var list = document.getElementById('test-alarm-samples-page-list');
    var empty = document.getElementById('test-alarm-samples-empty');
    if (!list) return;
    list.innerHTML = '';
    if (!items || items.length === 0) {
      if (empty) {
        empty.hidden = false;
        empty.classList.remove('hidden');
      }
      return;
    }
    if (empty) {
      empty.hidden = true;
      empty.classList.add('hidden');
    }
    items.forEach(function (item) {
      var row = document.createElement('div');
      row.className = 'list-row test-alarm-sample-row';
      row.setAttribute('data-sample-id', String(item.id));

      var label = document.createElement('span');
      var title = item.title || 'Einsatz ' + item.alarmId;
      var addr = item.address ? ' · ' + item.address : '';
      var metaLine = 'DIVERA #' + item.alarmId + ' · ' + (item.capturedAt || '');
      var runningBadge = item.running
        ? ' <span class="badge badge-warning" style="margin-left:0.35rem">läuft</span>'
        : '';
      label.innerHTML =
        '<strong>' +
        escapeHtml(title) +
        '</strong>' +
        escapeHtml(addr) +
        '<br><span class="hint hint--inline">' +
        escapeHtml(metaLine) +
        '</span>' +
        runningBadge;

      var actions = document.createElement('span');
      actions.className = 'btn-group btn-group--inline';

      var startBtn = document.createElement('button');
      startBtn.type = 'button';
      startBtn.className = 'btn btn--primary btn--sm btn-test-sample-start';
      startBtn.textContent = 'Einsatz starten';
      startBtn.setAttribute('data-sample-id', String(item.id));
      if (item.running) {
        startBtn.disabled = true;
      }

      var loadBtn = document.createElement('button');
      loadBtn.type = 'button';
      loadBtn.className = 'btn btn--outline btn--sm btn-test-sample-load';
      loadBtn.textContent = 'In JSON laden';
      loadBtn.setAttribute('data-sample-id', String(item.id));

      var deleteBtn = document.createElement('button');
      deleteBtn.type = 'button';
      deleteBtn.className = 'btn btn--outline btn--sm btn-test-sample-delete';
      deleteBtn.textContent = 'Löschen';
      deleteBtn.setAttribute('data-sample-id', String(item.id));

      actions.appendChild(startBtn);
      actions.appendChild(loadBtn);
      actions.appendChild(deleteBtn);
      row.appendChild(label);
      row.appendChild(actions);
      list.appendChild(row);
    });
  }

  var samplesListEl = document.getElementById('test-alarm-samples-page-list');
  if (samplesListEl) {
    samplesListEl.addEventListener('click', function (e) {
      var startBtn = e.target.closest('.btn-test-sample-start');
      if (startBtn) {
        startSample(startBtn.getAttribute('data-sample-id'), startBtn);
        return;
      }
      var loadBtn = e.target.closest('.btn-test-sample-load');
      if (loadBtn) {
        loadSamplePayload(loadBtn.getAttribute('data-sample-id'), loadBtn);
        return;
      }
      var deleteBtn = e.target.closest('.btn-test-sample-delete');
      if (deleteBtn) {
        deleteSample(deleteBtn.getAttribute('data-sample-id'), deleteBtn);
      }
    });
  }

  function reloadSoon() {
    window.setTimeout(function () {
      window.location.reload();
    }, 400);
  }

  function confirmSendPush() {
    return window.confirm('Push-Benachrichtigung an registrierte Einsatz-App-Geräte senden?');
  }

  function startSample(sampleId, triggerBtn) {
    var unitId = meta.getAttribute('data-unit-id');
    if (!unitId || !sampleId) return;
    if (!window.confirm('Einsatz auf der Startseite starten?')) {
      return;
    }
    var sendPush = confirmSendPush();
    if (triggerBtn) triggerBtn.disabled = true;
    var body = new URLSearchParams();
    body.set('unit', unitId);
    body.set('sendPush', sendPush ? 'true' : 'false');
    fetch('/test-alarm/samples/' + sampleId + '/start', {
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
          reloadSoon();
        }
      })
      .catch(function () {
        notify('Anfrage fehlgeschlagen', 'error');
      })
      .finally(function () {
        if (triggerBtn) triggerBtn.disabled = false;
      });
  }

  function loadSamplePayload(sampleId, triggerBtn) {
    var unitId = meta.getAttribute('data-unit-id');
    if (!unitId || !textarea || !sampleId) return;
    if (triggerBtn) triggerBtn.disabled = true;
    fetch('/test-alarm/samples/' + sampleId + '/payload?unit=' + encodeURIComponent(unitId), {
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
    })
      .then(function (r) {
        return r.json();
      })
      .then(function (data) {
        if (data && data.ok && data.payload) {
          textarea.value = data.payload;
          notify('Beispiel in JSON-Feld geladen', 'success');
          textarea.focus();
        } else {
          notify((data && data.message) || 'Laden fehlgeschlagen', 'error');
        }
      })
      .catch(function () {
        notify('Anfrage fehlgeschlagen', 'error');
      })
      .finally(function () {
        if (triggerBtn) triggerBtn.disabled = false;
      });
  }

  function deleteSample(sampleId, triggerBtn) {
    var unitId = meta.getAttribute('data-unit-id');
    if (!unitId || !sampleId) return;
    if (triggerBtn) triggerBtn.disabled = true;
    var body = new URLSearchParams();
    body.set('unit', unitId);
    fetch('/test-alarm/samples/' + sampleId + '/delete', {
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
          var row = document.querySelector(
            '#test-alarm-samples-page-list [data-sample-id="' + sampleId + '"]'
          );
          if (row) row.remove();
          var list = document.getElementById('test-alarm-samples-page-list');
          if (list && !list.querySelector('.test-alarm-sample-row')) {
            var empty = document.getElementById('test-alarm-samples-empty');
            if (empty) {
              empty.hidden = false;
              empty.classList.remove('hidden');
            }
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

  function refreshSamplesFromDivera(showToast) {
    var unitId = meta.getAttribute('data-unit-id');
    if (!unitId) {
      notify('Keine Einheit gewählt', 'error');
      return;
    }
    var loading = document.getElementById('test-alarm-samples-loading');
    var refreshBtn = document.getElementById('btn-test-alarm-refresh-samples');
    if (loading) loading.hidden = false;
    if (refreshBtn) refreshBtn.disabled = true;
    fetch('/test-alarm/samples?unit=' + encodeURIComponent(unitId) + '&sync=true', {
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
    })
      .then(function (r) {
        return r.json();
      })
      .then(function (items) {
        renderSamplesPageList(Array.isArray(items) ? items : []);
        if (showToast) {
          var n = Array.isArray(items) ? items.length : 0;
          notify(
            n === 0
              ? 'Keine gespeicherten Beispiele — DIVERA lieferte nichts Neues'
              : n + ' gespeicherte Beispiel-Einsatz/Einsätze (inkl. ältere aus der Datenbank)',
            n === 0 ? 'warning' : 'success'
          );
        }
      })
      .catch(function () {
        notify('DIVERA-Abruf fehlgeschlagen', 'error');
      })
      .finally(function () {
        if (loading) loading.hidden = true;
        if (refreshBtn) refreshBtn.disabled = false;
      });
  }

  function loadDefaultSample() {
    if (!textarea) return;
    if (defaultSample) {
      textarea.value = defaultSample.trim();
      notify('Vordefiniertes Beispiel geladen', 'success');
      textarea.focus();
    }
  }

  document.getElementById('btn-test-alarm-refresh-samples')?.addEventListener('click', function () {
    refreshSamplesFromDivera(true);
  });

  document.getElementById('btn-test-alarm-default-sample')?.addEventListener('click', loadDefaultSample);

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
    var sendPush = confirmSendPush();
    var btn = document.getElementById('btn-test-alarm-send');
    if (btn) btn.disabled = true;
    var body = new URLSearchParams();
    body.set('unit', unitId);
    body.set('payload', payload);
    body.set('sendPush', sendPush ? 'true' : 'false');
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
          reloadSoon();
        }
      })
      .catch(function () {
        notify('Anfrage fehlgeschlagen', 'error');
      })
      .finally(function () {
        if (btn) btn.disabled = false;
      });
  });

  var activeListEl = document.getElementById('test-alarm-active-list');
  if (activeListEl) {
    activeListEl.addEventListener('click', function (e) {
      var closeBtn = e.target.closest('.btn-test-alarm-close');
      if (closeBtn) {
        closeAlarm(closeBtn.getAttribute('data-alarm-id'), closeBtn);
      }
    });
  }

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
          reloadSoon();
        }
      })
      .catch(function () {
        notify('Anfrage fehlgeschlagen', 'error');
      })
      .finally(function () {
        if (triggerBtn) triggerBtn.disabled = false;
      });
  }
})();
