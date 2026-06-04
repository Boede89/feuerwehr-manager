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

  function openModal(overlay) {
    if (!overlay) return;
    overlay.classList.add('active');
    document.body.classList.add('modal-open');
  }

  function closeModal(overlay) {
    if (!overlay) return;
    overlay.classList.remove('active');
    if (!document.querySelector('.modal-overlay.active')) {
      document.body.classList.remove('modal-open');
    }
  }

  document.querySelectorAll('[data-close-modal]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      closeModal(btn.closest('.modal-overlay'));
    });
  });

  var samplesOverlay = document.getElementById('modal-test-alarm-samples');
  if (samplesOverlay) {
    samplesOverlay.addEventListener('click', function (e) {
      if (e.target === samplesOverlay) {
        closeModal(samplesOverlay);
      }
    });
  }

  function loadDefaultSample() {
    if (!textarea) return;
    var sample = defaultSample;
    if (sample) {
      textarea.value = sample.trim();
      notify('Vordefiniertes Beispiel geladen', 'success');
      closeModal(samplesOverlay);
    }
  }

  function renderSamplesList(items) {
    var list = document.getElementById('test-alarm-samples-list');
    var empty = document.getElementById('test-alarm-samples-empty');
    if (!list) return;
    list.innerHTML = '';
    if (!items || items.length === 0) {
      if (empty) empty.hidden = false;
      return;
    }
    if (empty) empty.hidden = true;
    items.forEach(function (item) {
      var row = document.createElement('div');
      row.className = 'list-row test-alarm-sample-row';
      var label = document.createElement('span');
      var title = item.title || 'Einsatz ' + item.alarmId;
      var addr = item.address ? ' · ' + item.address : '';
      var metaLine = 'DIVERA #' + item.alarmId + ' · ' + (item.capturedAt || '');
      label.innerHTML =
        '<strong>' +
        escapeHtml(title) +
        '</strong>' +
        escapeHtml(addr) +
        '<br><span class="hint hint--inline">' +
        escapeHtml(metaLine) +
        '</span>';
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'btn btn--outline btn--sm';
      btn.textContent = 'Laden';
      btn.setAttribute('data-sample-id', String(item.id));
      btn.addEventListener('click', function () {
        loadSamplePayload(item.id, btn);
      });
      row.appendChild(label);
      row.appendChild(btn);
      list.appendChild(row);
    });
  }

  function escapeHtml(s) {
    if (!s) return '';
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function loadSamplePayload(sampleId, triggerBtn) {
    var unitId = meta.getAttribute('data-unit-id');
    if (!unitId || !textarea) return;
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
          notify('Beispiel-Einsatz geladen', 'success');
          closeModal(samplesOverlay);
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

  function openSamplesModal() {
    var unitId = meta.getAttribute('data-unit-id');
    if (!unitId) {
      notify('Keine Einheit gewählt', 'error');
      return;
    }
    var loading = document.getElementById('test-alarm-samples-loading');
    var empty = document.getElementById('test-alarm-samples-empty');
    var list = document.getElementById('test-alarm-samples-list');
    if (list) list.innerHTML = '';
    if (empty) empty.hidden = true;
    if (loading) loading.hidden = false;
    openModal(samplesOverlay);
    fetch('/test-alarm/samples?unit=' + encodeURIComponent(unitId), {
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
    })
      .then(function (r) {
        return r.json();
      })
      .then(function (items) {
        if (loading) loading.hidden = true;
        renderSamplesList(Array.isArray(items) ? items : []);
      })
      .catch(function () {
        if (loading) loading.hidden = true;
        notify('Einsätze konnten nicht geladen werden', 'error');
      });
  }

  document.getElementById('btn-test-alarm-sample')?.addEventListener('click', openSamplesModal);
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
