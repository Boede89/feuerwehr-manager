(function () {
  'use strict';

  var root = document.querySelector('.reservierungen-page');
  if (!root) {
    return;
  }

  var unitId = root.dataset.unitId;
  var canWrite = root.dataset.canWrite === 'true';
  var modal = document.getElementById('reservierung-modal');
  var conflictModal = document.getElementById('reservierung-conflict-modal');
  var loeschModal = document.getElementById('reservierung-loesch-modal');
  var form = document.getElementById('reservierung-form');
  var submitBtn = document.getElementById('reservierung-submit');
  var resourcesBox = document.getElementById('reservierung-resources');
  var slotsBox = document.getElementById('reservierung-slots');
  var resourceCatalog = (window.__reservierungenResources || {});
  var pendingCreateFlags = { forceConflict: false, forceLoesch: false };

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

  function formatConflictTime(iso) {
    if (!iso) return '—';
    var d = new Date(iso);
    if (isNaN(d.getTime())) return String(iso);
    var pad = function (n) { return n < 10 ? '0' + n : String(n); };
    return pad(d.getDate()) + '.' + pad(d.getMonth() + 1) + '.' + d.getFullYear()
      + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
  }

  function describeConflicts(conflicts) {
    if (!conflicts || !conflicts.length) {
      return '';
    }
    return conflicts.map(function (c) {
      return '- ' + (c.resourceName || 'Ressource') + ' (' + (c.requesterName || '—') + '): '
        + formatConflictTime(c.startAt) + ' – ' + formatConflictTime(c.endAt);
    }).join('\n');
  }

  function openOverlay(el) {
    if (!el) return;
    el.classList.add('active');
    el.setAttribute('aria-hidden', 'false');
    document.body.classList.add('modal-open');
  }

  function closeOverlay(el) {
    if (!el) return;
    el.classList.remove('active');
    el.setAttribute('aria-hidden', 'true');
    if (!document.querySelector('.modal-overlay.active')) {
      document.body.classList.remove('modal-open');
    }
  }

  function currentKind() {
    return document.getElementById('reservierung-kind').value;
  }

  function resourceOptions() {
    return currentKind() === 'vehicle' ? (resourceCatalog.vehicles || []) : (resourceCatalog.rooms || []);
  }

  function buildResourceSelect(selectedId) {
    var select = document.createElement('select');
    select.className = 'field reservierung-resource-select';
    select.required = true;
    var placeholder = document.createElement('option');
    placeholder.value = '';
    placeholder.textContent = '— bitte wählen —';
    select.appendChild(placeholder);
    resourceOptions().forEach(function (opt) {
      var option = document.createElement('option');
      option.value = String(opt.id);
      option.textContent = opt.name;
      if (selectedId != null && String(opt.id) === String(selectedId)) {
        option.selected = true;
      }
      select.appendChild(option);
    });
    return select;
  }

  function addResourceRow(selectedId) {
    if (!resourcesBox) return;
    var row = document.createElement('div');
    row.className = 'reservierungen-multi-row';
    var select = buildResourceSelect(selectedId);
    var removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'btn btn--outline btn--sm';
    removeBtn.textContent = 'Entfernen';
    removeBtn.addEventListener('click', function () {
      if (resourcesBox.children.length <= 1) {
        notify('Mindestens ein Eintrag ist erforderlich.', 'error');
        return;
      }
      row.remove();
    });
    row.appendChild(select);
    row.appendChild(removeBtn);
    resourcesBox.appendChild(row);
  }

  function addSlotRow() {
    if (!slotsBox) return;
    var row = document.createElement('div');
    row.className = 'reservierungen-multi-row reservierungen-multi-row--slot';
    var start = document.createElement('input');
    start.type = 'datetime-local';
    start.className = 'field reservierung-slot-start';
    start.required = true;
    var end = document.createElement('input');
    end.type = 'datetime-local';
    end.className = 'field reservierung-slot-end';
    end.required = true;
    var removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'btn btn--outline btn--sm';
    removeBtn.textContent = 'Entfernen';
    removeBtn.addEventListener('click', function () {
      if (slotsBox.children.length <= 1) {
        notify('Mindestens ein Termin ist erforderlich.', 'error');
        return;
      }
      row.remove();
    });
    row.appendChild(start);
    row.appendChild(end);
    row.appendChild(removeBtn);
    slotsBox.appendChild(row);
  }

  function resetDynamicFields(kind, primaryId) {
    if (resourcesBox) {
      resourcesBox.innerHTML = '';
      addResourceRow(primaryId);
    }
    if (slotsBox) {
      slotsBox.innerHTML = '';
      addSlotRow();
    }
    var label = document.getElementById('reservierung-resources-label');
    var addBtn = document.getElementById('reservierung-add-resource');
    if (label) {
      label.innerHTML = (kind === 'vehicle' ? 'Fahrzeuge' : 'Räume') + ' <span class="req">*</span>';
    }
    if (addBtn) {
      addBtn.textContent = kind === 'vehicle' ? '+ weiteres Fahrzeug' : '+ weiterer Raum';
    }
  }

  function openModal(kind, id, name) {
    if (!modal || !form) {
      return;
    }
    pendingCreateFlags = { forceConflict: false, forceLoesch: false };
    document.getElementById('reservierung-kind').value = kind;
    document.getElementById('reservierung-modal-title').textContent =
      (kind === 'vehicle' ? 'Fahrzeug' : 'Raum') + ' reservieren' + (name ? ': ' + name : '');
    form.reset();
    resetDynamicFields(kind, id);
    var nameEl = document.getElementById('reservierung-name');
    var emailEl = document.getElementById('reservierung-email');
    if (nameEl && root.dataset.requesterName) {
      nameEl.value = root.dataset.requesterName;
    }
    if (emailEl && root.dataset.requesterEmail) {
      emailEl.value = root.dataset.requesterEmail;
    }
    openOverlay(modal);
    document.getElementById('reservierung-reason')?.focus();
  }

  function closeModal() {
    closeOverlay(modal);
  }

  function localDateTimeToIso(value) {
    if (!value) {
      return null;
    }
    var m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(String(value).trim());
    if (m) {
      var local = new Date(
        Number(m[1]),
        Number(m[2]) - 1,
        Number(m[3]),
        Number(m[4]),
        Number(m[5]),
        Number(m[6] || 0),
        0
      );
      return isNaN(local.getTime()) ? null : local.toISOString();
    }
    var d = new Date(value);
    return isNaN(d.getTime()) ? null : d.toISOString();
  }

  function collectResourceIds() {
    var ids = [];
    var seen = {};
    document.querySelectorAll('.reservierung-resource-select').forEach(function (select) {
      var value = Number(select.value);
      if (!value || seen[value]) {
        return;
      }
      seen[value] = true;
      ids.push(value);
    });
    return ids;
  }

  function collectSlots() {
    var slots = [];
    document.querySelectorAll('#reservierung-slots .reservierungen-multi-row--slot').forEach(function (row) {
      var startAt = localDateTimeToIso(row.querySelector('.reservierung-slot-start')?.value);
      var endAt = localDateTimeToIso(row.querySelector('.reservierung-slot-end')?.value);
      if (startAt && endAt) {
        slots.push({ startAt: startAt, endAt: endAt });
      }
    });
    return slots;
  }

  function showConflictModal(data) {
    var msg = document.getElementById('reservierung-conflict-message');
    var list = document.getElementById('reservierung-conflict-list');
    if (msg) {
      msg.textContent = data.message || 'Mindestens eine Ressource ist bereits vergeben.';
    }
    if (list) {
      list.innerHTML = '';
      (data.conflicts || []).forEach(function (c) {
        var li = document.createElement('li');
        li.textContent = (c.resourceName || 'Ressource') + ' · ' + (c.requesterName || '—') + ' · '
          + formatConflictTime(c.startAt) + ' – ' + formatConflictTime(c.endAt);
        list.appendChild(li);
      });
    }
    openOverlay(conflictModal);
  }

  function showLoeschModal(data) {
    var msg = document.getElementById('reservierung-loesch-message');
    if (msg) {
      msg.textContent = data.message || 'Löschfahrzeug-Warnung.';
    }
    openOverlay(loeschModal);
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
  document.querySelectorAll('[data-close-conflict-modal]').forEach(function (btn) {
    btn.addEventListener('click', function (ev) {
      ev.preventDefault();
      closeOverlay(conflictModal);
    });
  });
  document.querySelectorAll('[data-close-loesch-modal]').forEach(function (btn) {
    btn.addEventListener('click', function (ev) {
      ev.preventDefault();
      closeOverlay(loeschModal);
    });
  });

  modal?.addEventListener('click', function (ev) {
    if (ev.target === modal) {
      closeModal();
    }
  });
  conflictModal?.addEventListener('click', function (ev) {
    if (ev.target === conflictModal) {
      closeOverlay(conflictModal);
    }
  });
  loeschModal?.addEventListener('click', function (ev) {
    if (ev.target === loeschModal) {
      closeOverlay(loeschModal);
    }
  });

  document.getElementById('reservierung-add-resource')?.addEventListener('click', function () {
    addResourceRow(null);
  });
  document.getElementById('reservierung-add-slot')?.addEventListener('click', function () {
    addSlotRow();
  });
  document.getElementById('reservierung-conflict-force')?.addEventListener('click', function () {
    closeOverlay(conflictModal);
    pendingCreateFlags.forceConflict = true;
    submitCreate();
  });
  document.getElementById('reservierung-loesch-force')?.addEventListener('click', function () {
    closeOverlay(loeschModal);
    pendingCreateFlags.forceLoesch = true;
    submitCreate();
  });

  function submitCreate() {
    var kind = currentKind();
    var resourceIds = collectResourceIds();
    var slots = collectSlots();
    var requesterName = document.getElementById('reservierung-name').value.trim();
    var requesterEmail = document.getElementById('reservierung-email').value.trim();
    var reason = document.getElementById('reservierung-reason').value.trim();
    var location = document.getElementById('reservierung-location').value.trim();

    if (!requesterName || !requesterEmail || !reason || !location) {
      notify('Bitte alle Pflichtfelder ausfüllen.', 'error');
      return;
    }
    if (!resourceIds.length) {
      notify(kind === 'vehicle' ? 'Bitte mindestens ein Fahrzeug wählen.' : 'Bitte mindestens einen Raum wählen.', 'error');
      return;
    }
    if (!slots.length) {
      notify('Bitte mindestens einen Termin angeben.', 'error');
      return;
    }
    for (var i = 0; i < slots.length; i++) {
      if (new Date(slots[i].endAt).getTime() <= new Date(slots[i].startAt).getTime()) {
        notify('Termin ' + (i + 1) + ': Das Ende muss nach dem Beginn liegen.', 'error');
        return;
      }
    }

    var payload = {
      resourceIds: resourceIds,
      resourceId: resourceIds[0],
      requesterName: requesterName,
      requesterEmail: requesterEmail,
      reason: reason,
      location: location,
      slots: slots,
      startAt: slots[0].startAt,
      endAt: slots[0].endAt,
      forceAvailabilityOverride: !!pendingCreateFlags.forceLoesch,
      forceConflictOverride: !!pendingCreateFlags.forceConflict
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
        if (data.code === 'LOESCH_WARNING') {
          showLoeschModal(data);
          return;
        }
        if (data.code === 'CONFLICTS') {
          showConflictModal(data);
          return;
        }
        if (!data.ok) {
          notify(data.message || 'Fehler beim Einreichen.', 'error');
          return;
        }
        pendingCreateFlags = { forceConflict: false, forceLoesch: false };
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
  }

  form?.addEventListener('submit', function (event) {
    event.preventDefault();
    if (!form.reportValidity()) {
      notify('Bitte alle Pflichtfelder ausfüllen.', 'error');
      return;
    }
    pendingCreateFlags = { forceConflict: false, forceLoesch: false };
    submitCreate();
  });

  function processReservation(kind, id, action, reason, forceLoesch, conflictIds) {
    var url = (kind === 'VEHICLE' ? '/reservierungen/api/fahrzeuge/' : '/reservierungen/api/raeume/')
      + id + '/process?unit=' + encodeURIComponent(unitId);
    return fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      headers: Object.assign({ 'Content-Type': 'application/json', Accept: 'application/json' }, csrfHeaders()),
      body: JSON.stringify({
        action: action,
        reason: reason || '',
        forceAvailabilityOverride: !!forceLoesch,
        conflictIds: conflictIds || [],
        diveraGroupIds: []
      })
    }).then(parseJsonResponse);
  }

  function handleProcessResult(data, retry) {
    if (data.code === 'CONFLICTS') {
      var list = describeConflicts(data.conflicts);
      var confirmMsg = (data.message || 'Fahrzeug/Raum ist bereits belegt.')
        + '\n\nBestehende genehmigte Reservierungen:\n' + (list || '—')
        + '\n\nKonfliktierende Reservierungen stornieren und trotzdem genehmigen?'
        + '\n(Termine in DIVERA/Google werden dabei ebenfalls entfernt; Antragsteller erhalten eine Storno-Mail.)';
      if (window.confirm(confirmMsg)) {
        var ids = (data.conflicts || []).map(function (c) { return c.id; });
        retry('approve_with_conflict_resolution', false, ids);
      }
      return false;
    }
    if (data.code === 'LOESCH_WARNING') {
      var loeschMsg = (data.message || 'Löschfahrzeug-Warnung.')
        + '\n\nTrotzdem genehmigen?';
      if (window.confirm(loeschMsg)) {
        retry('approve', true, []);
      }
      return false;
    }
    if (!data.ok) {
      notify(data.message || 'Aktion fehlgeschlagen.', 'error');
      return false;
    }
    var notes = (data.syncNotes || []).filter(Boolean);
    var syncFailed = notes.some(function (n) {
      return /konnte nicht|kein Termin|nicht aktiviert|Hinweis:/i.test(n);
    });
    if (notes.length) {
      var text = (data.message || 'OK') + '\n\n' + notes.join('\n');
      if (syncFailed) {
        window.alert(text);
        notify(notes.join(' '), 'error');
      } else {
        notify(notes.join(' '), 'success');
      }
    }
    return true;
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

    function runApprove(approveAction, forceLoesch, conflictIds) {
      btn.disabled = true;
      processReservation(kind, id, approveAction, '', forceLoesch, conflictIds)
        .then(function (data) {
          if (handleProcessResult(data, function (nextAction, nextForce, nextIds) {
            runApprove(nextAction, nextForce, nextIds);
          })) {
            window.location.reload();
          } else {
            btn.disabled = false;
          }
        })
        .catch(function () {
          notify('Genehmigung fehlgeschlagen.', 'error');
          btn.disabled = false;
        });
    }

    if (action === 'approve') {
      runApprove('approve', false, []);
      return;
    }
    if (action === 'reject') {
      var reason = window.prompt('Begründung für die Ablehnung (optional):', '') || '';
      btn.disabled = true;
      processReservation(kind, id, 'reject', reason, false, [])
        .then(function (data) {
          if (data.ok) {
            window.location.reload();
          } else {
            notify(data.message || 'Ablehnung fehlgeschlagen.', 'error');
            btn.disabled = false;
          }
        })
        .catch(function () {
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
      var confirmMsg = 'Reservierung wirklich löschen?\n\n'
        + 'Falls vorhanden, wird der Termin auch aus DIVERA und dem Google-Kalender entfernt.\n'
        + 'Der Antragsteller erhält eine E-Mail, dass die Reservierung storniert wurde.';
      if (!window.confirm(confirmMsg)) {
        return;
      }
      btn.disabled = true;
      deleteReservation(row.dataset.kind, row.dataset.id).then(function (data) {
        if (data.ok) {
          notify(data.message || 'Reservierung gelöscht.', 'success');
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
