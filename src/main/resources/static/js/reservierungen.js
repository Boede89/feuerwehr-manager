(function () {
  'use strict';

  var root = document.querySelector('.reservierungen-page');
  if (!root) {
    return;
  }

  var unitId = root.dataset.unitId;
  var canWrite = root.dataset.canWrite === 'true';
  var modal = document.getElementById('reservierung-modal');
  var form = document.getElementById('reservierung-form');
  var submitBtn = document.getElementById('reservierung-submit');

  function notify(msg, type) {
    if (typeof window.toast === 'function') {
      window.toast(msg, type || 'success');
      return;
    }
    window.alert(msg);
  }

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

  function csrfHeaders() {
    var headers = {};
    var token = getCsrfToken();
    if (token) {
      headers[getCsrfHeader()] = token;
    }
    return headers;
  }

  function parseJsonResponse(res) {
    return res.text().then(function (text) {
      var data = {};
      if (text) {
        try {
          data = JSON.parse(text);
        } catch (e) {
          data = { ok: false, message: res.ok ? 'Unerwartete Antwort.' : 'Anfrage fehlgeschlagen (' + res.status + ').' };
        }
      }
      if (!res.ok && data.ok !== false) {
        data.ok = false;
        data.message = data.message || 'Anfrage fehlgeschlagen (' + res.status + ').';
      }
      return data;
    });
  }

  function openModal(kind, id, name) {
    if (!modal || !form) {
      return;
    }
    document.getElementById('reservierung-kind').value = kind;
    document.getElementById('reservierung-resource-id').value = String(id);
    document.getElementById('reservierung-modal-title').textContent =
      (kind === 'vehicle' ? 'Fahrzeug' : 'Raum') + ' reservieren: ' + name;
    form.reset();
    var nameEl = document.getElementById('reservierung-name');
    var emailEl = document.getElementById('reservierung-email');
    if (nameEl && root.dataset.requesterName) {
      nameEl.value = root.dataset.requesterName;
    }
    if (emailEl && root.dataset.requesterEmail) {
      emailEl.value = root.dataset.requesterEmail;
    }
    modal.classList.add('active');
    document.body.classList.add('modal-open');
    document.getElementById('reservierung-reason')?.focus();
  }

  function closeModal() {
    if (!modal) {
      return;
    }
    modal.classList.remove('active');
    if (!document.querySelector('.modal-overlay.active')) {
      document.body.classList.remove('modal-open');
    }
  }

  function localDateTimeToIso(value) {
    if (!value) {
      return null;
    }
    var d = new Date(value);
    return isNaN(d.getTime()) ? null : d.toISOString();
  }

  document.querySelectorAll('.reservierung-open-btn').forEach(function (btn) {
    btn.addEventListener('click', function () {
      openModal(btn.dataset.kind, btn.dataset.id, btn.dataset.name || '');
    });
  });

  document.querySelectorAll('[data-close-reservierung-modal]').forEach(function (btn) {
    btn.addEventListener('click', function (ev) {
      ev.preventDefault();
      closeModal();
    });
  });

  modal?.addEventListener('click', function (ev) {
    if (ev.target === modal) {
      closeModal();
    }
  });

  form?.addEventListener('submit', function (event) {
    event.preventDefault();
    if (!form.reportValidity()) {
      return;
    }
    var kind = document.getElementById('reservierung-kind').value;
    var resourceId = Number(document.getElementById('reservierung-resource-id').value);
    var startAt = localDateTimeToIso(document.getElementById('reservierung-start').value);
    var endAt = localDateTimeToIso(document.getElementById('reservierung-end').value);
    if (!startAt || !endAt) {
      notify('Bitte Beginn und Ende angeben.', 'error');
      return;
    }
    if (new Date(endAt).getTime() <= new Date(startAt).getTime()) {
      notify('Das Ende muss nach dem Beginn liegen.', 'error');
      return;
    }
    var payload = {
      resourceId: resourceId,
      requesterName: document.getElementById('reservierung-name').value.trim(),
      requesterEmail: document.getElementById('reservierung-email').value.trim(),
      reason: document.getElementById('reservierung-reason').value.trim(),
      location: document.getElementById('reservierung-location').value.trim(),
      startAt: startAt,
      endAt: endAt,
      forceAvailabilityOverride: false
    };
    var url = kind === 'vehicle'
      ? '/reservierungen/api/fahrzeuge?unit=' + encodeURIComponent(unitId)
      : '/reservierungen/api/raeume?unit=' + encodeURIComponent(unitId);
    if (submitBtn) {
      submitBtn.disabled = true;
    }
    fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      headers: Object.assign({ 'Content-Type': 'application/json', Accept: 'application/json' }, csrfHeaders()),
      body: JSON.stringify(payload)
    })
      .then(parseJsonResponse)
      .then(function (data) {
        if (!data.ok) {
          notify(data.message || 'Fehler beim Einreichen.', 'error');
          return;
        }
        closeModal();
        notify(data.message || 'Antrag eingereicht.', 'success');
        window.location.href = '/reservierungen?unit=' + encodeURIComponent(unitId) + '&tab=meine';
      })
      .catch(function () {
        notify('Antrag konnte nicht gesendet werden.', 'error');
      })
      .finally(function () {
        if (submitBtn) {
          submitBtn.disabled = false;
        }
      });
  });

  function processReservation(kind, id, action, reason) {
    var url = (kind === 'VEHICLE' ? '/reservierungen/api/fahrzeuge/' : '/reservierungen/api/raeume/')
      + id + '/process?unit=' + encodeURIComponent(unitId);
    return fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      headers: Object.assign({ 'Content-Type': 'application/json', Accept: 'application/json' }, csrfHeaders()),
      body: JSON.stringify({
        action: action,
        reason: reason || '',
        forceAvailabilityOverride: false,
        conflictIds: [],
        diveraGroupIds: []
      })
    }).then(parseJsonResponse);
  }

  function deleteReservation(kind, id) {
    var url = (kind === 'VEHICLE' ? '/reservierungen/api/fahrzeuge/' : '/reservierungen/api/raeume/')
      + id + '?unit=' + encodeURIComponent(unitId);
    return fetch(url, {
      method: 'DELETE',
      credentials: 'same-origin',
      headers: Object.assign({ Accept: 'application/json' }, csrfHeaders())
    }).then(parseJsonResponse);
  }

  document.getElementById('pending-reservations-table')?.addEventListener('click', function (event) {
    var btn = event.target.closest('[data-action]');
    if (!btn) {
      return;
    }
    var row = btn.closest('tr');
    if (!row) {
      return;
    }
    var kind = row.dataset.kind;
    var id = row.dataset.id;
    var action = btn.dataset.action;
    if (action === 'approve') {
      btn.disabled = true;
      processReservation(kind, id, 'approve').then(function (data) {
        if (data.ok) {
          window.location.reload();
        } else {
          notify(data.message || 'Genehmigung fehlgeschlagen.', 'error');
          btn.disabled = false;
        }
      }).catch(function () {
        notify('Genehmigung fehlgeschlagen.', 'error');
        btn.disabled = false;
      });
      return;
    }
    if (action === 'reject') {
      var reason = window.prompt('Begründung für die Ablehnung (optional):', '') || '';
      btn.disabled = true;
      processReservation(kind, id, 'reject', reason).then(function (data) {
        if (data.ok) {
          window.location.reload();
        } else {
          notify(data.message || 'Ablehnung fehlgeschlagen.', 'error');
          btn.disabled = false;
        }
      }).catch(function () {
        notify('Ablehnung fehlgeschlagen.', 'error');
        btn.disabled = false;
      });
    }
  });

  document.querySelectorAll('[data-action="delete"]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var row = btn.closest('tr');
      if (!row || !canWrite) {
        return;
      }
      if (!window.confirm('Reservierung wirklich löschen?')) {
        return;
      }
      btn.disabled = true;
      deleteReservation(row.dataset.kind, row.dataset.id).then(function (data) {
        if (data.ok) {
          window.location.reload();
        } else {
          notify(data.message || 'Löschen fehlgeschlagen.', 'error');
          btn.disabled = false;
        }
      }).catch(function () {
        notify('Löschen fehlgeschlagen.', 'error');
        btn.disabled = false;
      });
    });
  });
})();
