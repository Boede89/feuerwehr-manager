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

  function csrfHeaders() {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (!token || !header) {
      return {};
    }
    var h = {};
    h[header.content] = token.content;
    return h;
  }

  function openModal(kind, id, name) {
    if (!modal) {
      return;
    }
    document.getElementById('reservierung-kind').value = kind;
    document.getElementById('reservierung-resource-id').value = String(id);
    document.getElementById('reservierung-modal-title').textContent =
      (kind === 'vehicle' ? 'Fahrzeug' : 'Raum') + ' reservieren: ' + name;
    modal.hidden = false;
    modal.setAttribute('aria-hidden', 'false');
    modal.style.display = 'flex';
    document.body.classList.add('modal-open');
  }

  function closeModal() {
    if (!modal) {
      return;
    }
    modal.hidden = true;
    modal.setAttribute('aria-hidden', 'true');
    modal.style.display = 'none';
    document.body.classList.remove('modal-open');
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

  document.getElementById('reservierung-modal-close')?.addEventListener('click', closeModal);
  document.getElementById('reservierung-modal-cancel')?.addEventListener('click', closeModal);
  modal?.querySelector('.modal__backdrop')?.addEventListener('click', closeModal);

  form?.addEventListener('submit', function (event) {
    event.preventDefault();
    var kind = document.getElementById('reservierung-kind').value;
    var resourceId = Number(document.getElementById('reservierung-resource-id').value);
    var payload = {
      resourceId: resourceId,
      requesterName: document.getElementById('reservierung-name').value.trim(),
      requesterEmail: document.getElementById('reservierung-email').value.trim(),
      reason: document.getElementById('reservierung-reason').value.trim(),
      location: document.getElementById('reservierung-location').value.trim(),
      startAt: localDateTimeToIso(document.getElementById('reservierung-start').value),
      endAt: localDateTimeToIso(document.getElementById('reservierung-end').value),
      forceAvailabilityOverride: false
    };
    var url = kind === 'vehicle'
      ? '/reservierungen/api/fahrzeuge?unit=' + encodeURIComponent(unitId)
      : '/reservierungen/api/raeume?unit=' + encodeURIComponent(unitId);
    fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      headers: Object.assign({ 'Content-Type': 'application/json' }, csrfHeaders()),
      body: JSON.stringify(payload)
    })
      .then(function (res) { return res.json(); })
      .then(function (data) {
        if (!data.success) {
          if (window.showToast) {
            window.showToast(data.message || 'Fehler beim Einreichen.', 'error');
          } else {
            alert(data.message || 'Fehler beim Einreichen.');
          }
          return;
        }
        closeModal();
        if (window.showToast) {
          window.showToast(data.message || 'Antrag eingereicht.', 'success');
        }
        window.location.href = '/reservierungen?unit=' + encodeURIComponent(unitId) + '&tab=meine';
      })
      .catch(function () {
        if (window.showToast) {
          window.showToast('Antrag konnte nicht gesendet werden.', 'error');
        }
      });
  });

  function processReservation(kind, id, action, reason) {
    var url = (kind === 'VEHICLE' ? '/reservierungen/api/fahrzeuge/' : '/reservierungen/api/raeume/')
      + id + '/process?unit=' + encodeURIComponent(unitId);
    return fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      headers: Object.assign({ 'Content-Type': 'application/json' }, csrfHeaders()),
      body: JSON.stringify({
        action: action,
        reason: reason || '',
        forceAvailabilityOverride: false,
        conflictIds: [],
        diveraGroupIds: []
      })
    }).then(function (res) { return res.json(); });
  }

  function deleteReservation(kind, id) {
    var url = (kind === 'VEHICLE' ? '/reservierungen/api/fahrzeuge/' : '/reservierungen/api/raeume/')
      + id + '?unit=' + encodeURIComponent(unitId);
    return fetch(url, {
      method: 'DELETE',
      credentials: 'same-origin',
      headers: csrfHeaders()
    }).then(function (res) { return res.json(); });
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
      processReservation(kind, id, 'approve').then(function (data) {
        if (data.success) {
          window.location.reload();
        } else if (window.showToast) {
          window.showToast(data.message || 'Genehmigung fehlgeschlagen.', 'error');
        } else {
          alert(data.message || 'Genehmigung fehlgeschlagen.');
        }
      });
      return;
    }
    if (action === 'reject') {
      var reason = window.prompt('Begründung für die Ablehnung (optional):', '') || '';
      processReservation(kind, id, 'reject', reason).then(function (data) {
        if (data.success) {
          window.location.reload();
        } else if (window.showToast) {
          window.showToast(data.message || 'Ablehnung fehlgeschlagen.', 'error');
        }
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
      deleteReservation(row.dataset.kind, row.dataset.id).then(function (data) {
        if (data.success) {
          window.location.reload();
        } else if (window.showToast) {
          window.showToast(data.message || 'Löschen fehlgeschlagen.', 'error');
        }
      });
    });
  });
})();
