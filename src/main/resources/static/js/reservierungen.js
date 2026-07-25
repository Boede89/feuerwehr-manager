(function () {
  'use strict';

  var root = document.querySelector('.reservierungen-page');
  if (!root) {
    return;
  }

  var unitId = root.dataset.unitId;
  var canWrite = root.dataset.canWrite === 'true';
  var modal = document.getElementById('reservierung-modal');
  var pickModal = document.getElementById('reservierung-pick-modal');
  var conflictModal = document.getElementById('reservierung-conflict-modal');
  var loeschModal = document.getElementById('reservierung-loesch-modal');
  var importModal = document.getElementById('reservierung-import-modal');
  var importOptionsModal = document.getElementById('reservierung-import-options-modal');
  var form = document.getElementById('reservierung-form');
  var importForm = document.getElementById('reservierung-import-form');
  var submitBtn = document.getElementById('reservierung-submit');
  var slotsBox = document.getElementById('reservierung-slots');
  var chipsBox = document.getElementById('reservierung-selected-chips');
  var importChipsBox = document.getElementById('import-selected-chips');
  var resourceCatalog = window.__reservierungenResources || {};
  var pendingCreateFlags = { forceConflict: false, forceLoesch: false };
  var selectedResources = [];
  var importSelectedResources = [];
  var pickContext = 'create'; // create | import
  var pendingImportPayload = null;

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

  function resourceOptionsFor(kind) {
    return kind === 'vehicle' ? (resourceCatalog.vehicles || []) : (resourceCatalog.rooms || []);
  }

  function findResource(kind, id) {
    var list = resourceOptionsFor(kind);
    for (var i = 0; i < list.length; i++) {
      if (String(list[i].id) === String(id)) {
        return list[i];
      }
    }
    return null;
  }

  function renderChips(box, list, kind, onRemove) {
    if (!box) return;
    box.innerHTML = '';
    if (!list.length) {
      var empty = document.createElement('p');
      empty.className = 'hint';
      empty.textContent = kind === 'vehicle' ? 'Noch kein Fahrzeug ausgewählt.' : 'Noch kein Raum ausgewählt.';
      box.appendChild(empty);
      return;
    }
    list.forEach(function (item) {
      var chip = document.createElement('span');
      chip.className = 'reservierungen-chip';
      chip.textContent = item.name;
      var remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'reservierungen-chip__remove';
      remove.setAttribute('aria-label', 'Entfernen');
      remove.textContent = '×';
      remove.addEventListener('click', function () {
        onRemove(item.id);
      });
      chip.appendChild(remove);
      box.appendChild(chip);
    });
  }

  function renderSelectedChips() {
    renderChips(chipsBox, selectedResources, currentKind(), function (id) {
      selectedResources = selectedResources.filter(function (r) { return String(r.id) !== String(id); });
      renderSelectedChips();
    });
  }

  function renderImportChips() {
    var kind = document.getElementById('import-kind')?.value || 'vehicle';
    renderChips(importChipsBox, importSelectedResources, kind, function (id) {
      importSelectedResources = importSelectedResources.filter(function (r) { return String(r.id) !== String(id); });
      renderImportChips();
    });
  }

  function openPickModal(context) {
    pickContext = context;
    var kind = context === 'import'
      ? (document.getElementById('import-kind')?.value || 'vehicle')
      : currentKind();
    var already = context === 'import' ? importSelectedResources : selectedResources;
    var alreadyIds = {};
    already.forEach(function (r) { alreadyIds[String(r.id)] = true; });
    var title = document.getElementById('reservierung-pick-title');
    if (title) {
      title.textContent = kind === 'vehicle' ? 'Weitere Fahrzeuge wählen' : 'Weiteren Raum wählen';
    }
    var list = document.getElementById('reservierung-pick-list');
    if (!list) return;
    list.innerHTML = '';
    var options = resourceOptionsFor(kind).filter(function (opt) { return !alreadyIds[String(opt.id)]; });
    if (!options.length) {
      var empty = document.createElement('p');
      empty.className = 'hint';
      empty.textContent = 'Keine weiteren Einträge verfügbar.';
      list.appendChild(empty);
    } else {
      options.forEach(function (opt) {
        var label = document.createElement('label');
        label.className = 'checkbox-row';
        var input = document.createElement('input');
        input.type = 'checkbox';
        input.value = String(opt.id);
        input.dataset.name = opt.name;
        label.appendChild(input);
        var span = document.createElement('span');
        span.textContent = opt.name;
        label.appendChild(span);
        list.appendChild(label);
      });
    }
    openOverlay(pickModal);
  }

  function applyPickSelection() {
    var kind = pickContext === 'import'
      ? (document.getElementById('import-kind')?.value || 'vehicle')
      : currentKind();
    var checks = document.querySelectorAll('#reservierung-pick-list input[type="checkbox"]:checked');
    if (!checks.length) {
      notify('Bitte mindestens einen Eintrag auswählen.', 'error');
      return;
    }
    var target = pickContext === 'import' ? importSelectedResources : selectedResources;
    var seen = {};
    target.forEach(function (r) { seen[String(r.id)] = true; });
    checks.forEach(function (input) {
      if (seen[input.value]) return;
      seen[input.value] = true;
      target.push({ id: Number(input.value), name: input.dataset.name || findResource(kind, input.value)?.name || input.value });
    });
    if (pickContext === 'import') {
      importSelectedResources = target;
      renderImportChips();
    } else {
      selectedResources = target;
      renderSelectedChips();
    }
    closeOverlay(pickModal);
  }

  function addSlotRow(canRemove) {
    if (!slotsBox) return;
    var row = document.createElement('div');
    row.className = 'reservierungen-multi-row reservierungen-multi-row--slot';
    var start = document.createElement('input');
    start.type = 'datetime-local';
    start.className = 'field reservierung-slot-start';
    start.required = true;
    start.setAttribute('aria-label', 'Beginn');
    var end = document.createElement('input');
    end.type = 'datetime-local';
    end.className = 'field reservierung-slot-end';
    end.required = true;
    end.setAttribute('aria-label', 'Ende');
    row.appendChild(start);
    row.appendChild(end);
    if (canRemove) {
      var removeBtn = document.createElement('button');
      removeBtn.type = 'button';
      removeBtn.className = 'btn btn--outline btn--sm';
      removeBtn.textContent = 'Entfernen';
      removeBtn.addEventListener('click', function () {
        row.remove();
      });
      row.appendChild(removeBtn);
    }
    slotsBox.appendChild(row);
  }

  function resetCreateForm(kind, primaryId, primaryName) {
    selectedResources = [];
    if (primaryId) {
      var found = findResource(kind, primaryId);
      selectedResources.push({
        id: Number(primaryId),
        name: found ? found.name : (primaryName || String(primaryId))
      });
    }
    renderSelectedChips();
    if (slotsBox) {
      slotsBox.innerHTML = '';
      addSlotRow(false);
    }
    var label = document.getElementById('reservierung-resources-label');
    var addBtn = document.getElementById('reservierung-add-resource');
    if (label) {
      label.innerHTML = (kind === 'vehicle' ? 'Ausgewählte Fahrzeuge' : 'Ausgewählte Räume') + ' <span class="req">*</span>';
    }
    if (addBtn) {
      addBtn.textContent = kind === 'vehicle' ? 'Weiteres Fahrzeug hinzufügen' : 'Weiteren Raum hinzufügen';
      addBtn.hidden = kind !== 'vehicle' && kind !== 'room' ? false : false;
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
    resetCreateForm(kind, id, name);
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
  document.querySelectorAll('[data-close-pick-modal]').forEach(function (btn) {
    btn.addEventListener('click', function (ev) {
      ev.preventDefault();
      closeOverlay(pickModal);
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
  document.querySelectorAll('[data-close-import-modal]').forEach(function (btn) {
    btn.addEventListener('click', function (ev) {
      ev.preventDefault();
      closeOverlay(importModal);
    });
  });
  document.querySelectorAll('[data-close-import-options-modal]').forEach(function (btn) {
    btn.addEventListener('click', function (ev) {
      ev.preventDefault();
      closeOverlay(importOptionsModal);
      pendingImportPayload = null;
    });
  });

  modal?.addEventListener('click', function (ev) {
    if (ev.target === modal) closeModal();
  });
  pickModal?.addEventListener('click', function (ev) {
    if (ev.target === pickModal) closeOverlay(pickModal);
  });
  conflictModal?.addEventListener('click', function (ev) {
    if (ev.target === conflictModal) closeOverlay(conflictModal);
  });
  loeschModal?.addEventListener('click', function (ev) {
    if (ev.target === loeschModal) closeOverlay(loeschModal);
  });
  importModal?.addEventListener('click', function (ev) {
    if (ev.target === importModal) closeOverlay(importModal);
  });
  importOptionsModal?.addEventListener('click', function (ev) {
    if (ev.target === importOptionsModal) {
      closeOverlay(importOptionsModal);
      pendingImportPayload = null;
    }
  });

  document.getElementById('reservierung-add-resource')?.addEventListener('click', function () {
    openPickModal('create');
  });
  document.getElementById('reservierung-add-slot')?.addEventListener('click', function () {
    addSlotRow(true);
  });
  document.getElementById('reservierung-pick-apply')?.addEventListener('click', applyPickSelection);
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
    var resourceIds = selectedResources.map(function (r) { return Number(r.id); }).filter(Boolean);
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
    if (submitBtn) submitBtn.disabled = true;
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
        if (submitBtn) submitBtn.disabled = false;
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

  function syncImportUiForKind() {
    var kind = document.getElementById('import-kind')?.value || 'vehicle';
    var label = document.getElementById('import-resources-label');
    var addBtn = document.getElementById('import-add-resource');
    if (label) {
      label.innerHTML = (kind === 'vehicle' ? 'Fahrzeuge' : 'Raum') + ' <span class="req">*</span>';
    }
    if (addBtn) {
      addBtn.textContent = kind === 'vehicle' ? 'Weiteres Fahrzeug hinzufügen' : 'Raum wählen';
      addBtn.hidden = false;
    }
    importSelectedResources = [];
    renderImportChips();
  }

  document.getElementById('reservierung-import-open')?.addEventListener('click', function () {
    if (!importModal || !importForm) return;
    importForm.reset();
    document.getElementById('import-kind').value = 'vehicle';
    syncImportUiForKind();
    openOverlay(importModal);
  });
  document.getElementById('import-kind')?.addEventListener('change', syncImportUiForKind);
  document.getElementById('import-add-resource')?.addEventListener('click', function () {
    openPickModal('import');
  });

  importForm?.addEventListener('submit', function (event) {
    event.preventDefault();
    if (!importForm.reportValidity()) {
      notify('Bitte alle Pflichtfelder ausfüllen.', 'error');
      return;
    }
    var kind = document.getElementById('import-kind').value;
    var resourceIds = importSelectedResources.map(function (r) { return Number(r.id); }).filter(Boolean);
    if (!resourceIds.length) {
      notify(kind === 'vehicle' ? 'Bitte mindestens ein Fahrzeug wählen.' : 'Bitte einen Raum wählen.', 'error');
      return;
    }
    if (kind === 'room' && resourceIds.length > 1) {
      notify('Für Räume bitte genau einen Raum wählen.', 'error');
      return;
    }
    var startAt = localDateTimeToIso(document.getElementById('import-start').value);
    var endAt = localDateTimeToIso(document.getElementById('import-end').value);
    if (!startAt || !endAt) {
      notify('Bitte Beginn und Ende angeben.', 'error');
      return;
    }
    if (new Date(endAt).getTime() <= new Date(startAt).getTime()) {
      notify('Das Ende muss nach dem Beginn liegen.', 'error');
      return;
    }
    pendingImportPayload = {
      kind: kind,
      resourceIds: resourceIds,
      resourceId: resourceIds[0],
      requesterName: document.getElementById('import-name').value.trim(),
      requesterEmail: document.getElementById('import-email').value.trim(),
      reason: document.getElementById('import-reason').value.trim(),
      location: document.getElementById('import-location').value.trim(),
      startAt: startAt,
      endAt: endAt
    };
    var emailOpt = document.getElementById('import-opt-email');
    var calOpt = document.getElementById('import-opt-calendar');
    if (emailOpt) emailOpt.checked = true;
    if (calOpt) calOpt.checked = true;
    openOverlay(importOptionsModal);
  });

  document.getElementById('import-options-confirm')?.addEventListener('click', function () {
    if (!pendingImportPayload) return;
    var payload = Object.assign({}, pendingImportPayload, {
      sendRequesterEmail: !!document.getElementById('import-opt-email')?.checked,
      syncCalendars: !!document.getElementById('import-opt-calendar')?.checked
    });
    var btn = document.getElementById('import-options-confirm');
    if (btn) btn.disabled = true;
    fetch('/reservierungen/api/import?unit=' + encodeURIComponent(unitId), {
      method: 'POST',
      credentials: 'same-origin',
      headers: Object.assign({ 'Content-Type': 'application/json', Accept: 'application/json' }, csrfHeaders()),
      body: JSON.stringify(payload)
    })
      .then(parseJsonResponse)
      .then(function (data) {
        if (!data.ok) {
          notify(data.message || 'Übernahme fehlgeschlagen.', 'error');
          return;
        }
        var notes = (data.syncNotes || []).filter(Boolean);
        notify((data.message || 'Übernommen.') + (notes.length ? ' ' + notes.join(' ') : ''), 'success');
        closeOverlay(importOptionsModal);
        closeOverlay(importModal);
        pendingImportPayload = null;
        window.location.reload();
      })
      .catch(function () {
        notify('Übernahme fehlgeschlagen.', 'error');
      })
      .finally(function () {
        if (btn) btn.disabled = false;
      });
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
    if (!btn) return;
    var row = btn.closest('tr');
    if (!row) return;
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
      if (!row || !canWrite) return;
      var confirmMsg = 'Reservierung wirklich löschen?\n\n'
        + 'Falls vorhanden, wird der Termin auch aus DIVERA und dem Google-Kalender entfernt.\n'
        + 'Der Antragsteller erhält eine E-Mail, dass die Reservierung storniert wurde.';
      if (!window.confirm(confirmMsg)) return;
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
