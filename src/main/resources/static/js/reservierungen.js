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
  var approveConflictModal = document.getElementById('reservierung-approve-conflict-modal');
  var loeschModal = document.getElementById('reservierung-loesch-modal');
  var importModal = document.getElementById('reservierung-import-modal');
  var importOptionsModal = document.getElementById('reservierung-import-options-modal');
  var pendingApproveRetry = null;
  var form = document.getElementById('reservierung-form');
  var importForm = document.getElementById('reservierung-import-form');
  var submitBtn = document.getElementById('reservierung-submit');
  var slotsBox = document.getElementById('reservierung-slots');
  var chipsBox = document.getElementById('reservierung-selected-chips');
  var importChipsBox = document.getElementById('import-selected-chips');
  var resourceCatalog = loadResourceCatalog();
  var pendingCreateFlags = { forceConflict: false, forceLoesch: false, testModeEmailDelivery: null };
  var selectedResources = [];
  var importExtraResources = [];
  var pickContext = 'create'; // create | import
  var pendingImportPayload = null;

  function loadResourceCatalog() {
    function fromSelect(id) {
      var select = document.getElementById(id);
      var list = [];
      if (!select) return list;
      Array.prototype.forEach.call(select.options, function (opt) {
        if (!opt.value) return;
        list.push({ id: Number(opt.value), name: opt.textContent || opt.label || opt.value });
      });
      return list;
    }
    return {
      vehicles: fromSelect('reservierungen-vehicles-template'),
      rooms: fromSelect('reservierungen-rooms-template')
    };
  }

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
      return '- ' + (c.resourceName || 'Ressource')
        + ' · Grund: ' + (c.reason || '—')
        + ' · ' + (c.requesterName || '—')
        + ' · ' + formatConflictTime(c.startAt) + ' – ' + formatConflictTime(c.endAt);
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
    renderChips(importChipsBox, importExtraResources, 'vehicle', function (id) {
      importExtraResources = importExtraResources.filter(function (r) { return String(r.id) !== String(id); });
      renderImportChips();
    });
  }

  function fillSelectOptions(selectEl, kind, selectedId) {
    if (!selectEl) return;
    var current = selectedId != null ? String(selectedId) : selectEl.value;
    selectEl.innerHTML = '';
    var placeholder = document.createElement('option');
    placeholder.value = '';
    placeholder.textContent = '— bitte wählen —';
    selectEl.appendChild(placeholder);
    resourceOptionsFor(kind).forEach(function (opt) {
      var option = document.createElement('option');
      option.value = String(opt.id);
      option.textContent = opt.name;
      if (current && String(opt.id) === current) {
        option.selected = true;
      }
      selectEl.appendChild(option);
    });
  }

  function applyEndDateFromStart(startInput, endDateInput) {
    if (!startInput || !endDateInput || !startInput.value) {
      return;
    }
    var startDate = String(startInput.value).slice(0, 10);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(startDate)) {
      return;
    }
    endDateInput.value = startDate;
  }

  function wireStartToEndDate(startInput, endDateInput) {
    if (!startInput || !endDateInput || startInput.dataset.dateSyncBound) {
      return;
    }
    startInput.dataset.dateSyncBound = '1';
    startInput.addEventListener('change', function () {
      applyEndDateFromStart(startInput, endDateInput);
    });
  }

  function combineDateAndTime(dateValue, timeValue) {
    if (!dateValue || !timeValue) {
      return null;
    }
    return localDateTimeToIso(String(dateValue).slice(0, 10) + 'T' + String(timeValue).slice(0, 5));
  }

  function formatDurationLabel(totalMinutes) {
    if (totalMinutes < 60) {
      return totalMinutes + ' Min.';
    }
    var hours = Math.floor(totalMinutes / 60);
    var mins = totalMinutes % 60;
    if (mins === 0) {
      return hours + ' Std.';
    }
    return hours + ':' + String(mins).padStart(2, '0') + ' Std.';
  }

  function parseTimeToMinutes(timeValue) {
    if (!timeValue) return null;
    var m = /^(\d{1,2}):(\d{2})/.exec(String(timeValue).trim());
    if (!m) return null;
    return Number(m[1]) * 60 + Number(m[2]);
  }

  function minutesToTimeValue(totalMinutes) {
    var normalized = ((totalMinutes % (24 * 60)) + 24 * 60) % (24 * 60);
    var h = Math.floor(normalized / 60);
    var m = normalized % 60;
    return String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0');
  }

  function addDaysToDateValue(dateValue, days) {
    var parts = String(dateValue).slice(0, 10).split('-');
    if (parts.length !== 3) return null;
    var d = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
    d.setDate(d.getDate() + days);
    return (
      d.getFullYear() +
      '-' +
      String(d.getMonth() + 1).padStart(2, '0') +
      '-' +
      String(d.getDate()).padStart(2, '0')
    );
  }

  function applySlotDuration(row) {
    if (!row) return;
    var fromInput = row.querySelector('.reservierung-slot-from');
    var toInput = row.querySelector('.reservierung-slot-to');
    var durationSelect = row.querySelector('.reservierung-slot-duration');
    if (!fromInput || !toInput || !durationSelect || !durationSelect.value) {
      return;
    }
    var fromMinutes = parseTimeToMinutes(fromInput.value);
    if (fromMinutes == null) {
      return;
    }
    toInput.value = minutesToTimeValue(fromMinutes + Number(durationSelect.value));
    toInput.dataset.autoFilled = '1';
  }

  function createSlotField(labelText, control) {
    var wrap = document.createElement('label');
    wrap.className = 'reservierungen-slot-field';
    var caption = document.createElement('span');
    caption.className = 'reservierungen-slot-field__label';
    caption.textContent = labelText;
    wrap.appendChild(caption);
    wrap.appendChild(control);
    return wrap;
  }

  function openPickModal(context) {
    pickContext = context;
    var kind = context === 'import'
      ? (document.getElementById('import-kind')?.value || 'vehicle')
      : currentKind();
    var already = {};
    if (context === 'import') {
      var primary = document.getElementById('import-primary')?.value;
      if (primary) already[String(primary)] = true;
      importExtraResources.forEach(function (r) { already[String(r.id)] = true; });
    } else {
      selectedResources.forEach(function (r) { already[String(r.id)] = true; });
    }
    var title = document.getElementById('reservierung-pick-title');
    if (title) {
      title.textContent = kind === 'vehicle' ? 'Weitere Fahrzeuge wählen' : 'Weiteren Raum wählen';
    }
    var list = document.getElementById('reservierung-pick-list');
    if (!list) return;
    list.innerHTML = '';
    var options = resourceOptionsFor(kind).filter(function (opt) { return !already[String(opt.id)]; });
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
    if (pickContext === 'import') {
      var seen = {};
      var primary = document.getElementById('import-primary')?.value;
      if (primary) seen[String(primary)] = true;
      importExtraResources.forEach(function (r) { seen[String(r.id)] = true; });
      checks.forEach(function (input) {
        if (seen[input.value]) return;
        seen[input.value] = true;
        importExtraResources.push({
          id: Number(input.value),
          name: input.dataset.name || findResource(kind, input.value)?.name || input.value
        });
      });
      renderImportChips();
    } else {
      var seenCreate = {};
      selectedResources.forEach(function (r) { seenCreate[String(r.id)] = true; });
      checks.forEach(function (input) {
        if (seenCreate[input.value]) return;
        seenCreate[input.value] = true;
        selectedResources.push({
          id: Number(input.value),
          name: input.dataset.name || findResource(kind, input.value)?.name || input.value
        });
      });
      renderSelectedChips();
    }
    closeOverlay(pickModal);
  }

  function addSlotRow(canRemove) {
    if (!slotsBox) return;
    var row = document.createElement('div');
    row.className = 'reservierungen-multi-row reservierungen-multi-row--slot';

    var dateInput = document.createElement('input');
    dateInput.type = 'date';
    dateInput.className = 'field reservierung-slot-date';
    dateInput.required = true;

    var fromInput = document.createElement('input');
    fromInput.type = 'time';
    fromInput.className = 'field reservierung-slot-from';
    fromInput.required = true;
    fromInput.step = '60';

    var toInput = document.createElement('input');
    toInput.type = 'time';
    toInput.className = 'field reservierung-slot-to';
    toInput.required = true;
    toInput.step = '60';

    var durationSelect = document.createElement('select');
    durationSelect.className = 'field reservierung-slot-duration';
    var emptyOpt = document.createElement('option');
    emptyOpt.value = '';
    emptyOpt.textContent = 'Dauer…';
    durationSelect.appendChild(emptyOpt);
    for (var mins = 30; mins <= 600; mins += 30) {
      var opt = document.createElement('option');
      opt.value = String(mins);
      opt.textContent = formatDurationLabel(mins);
      durationSelect.appendChild(opt);
    }

    fromInput.addEventListener('change', function () {
      applySlotDuration(row);
    });
    durationSelect.addEventListener('change', function () {
      applySlotDuration(row);
    });
    toInput.addEventListener('input', function () {
      if (toInput.dataset.autoFilled === '1') {
        toInput.dataset.autoFilled = '0';
        return;
      }
      durationSelect.value = '';
    });

    row.appendChild(createSlotField('Datum', dateInput));
    row.appendChild(createSlotField('Von', fromInput));
    row.appendChild(createSlotField('Bis', toInput));
    row.appendChild(createSlotField('Zeitraum', durationSelect));

    if (canRemove) {
      var removeBtn = document.createElement('button');
      removeBtn.type = 'button';
      removeBtn.className = 'btn btn--outline btn--sm reservierungen-slot-remove';
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
    pendingCreateFlags = { forceConflict: false, forceLoesch: false, testModeEmailDelivery: null };
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
    var rows = document.querySelectorAll('#reservierung-slots .reservierungen-multi-row--slot');
    for (var i = 0; i < rows.length; i++) {
      var row = rows[i];
      var dateValue = row.querySelector('.reservierung-slot-date')?.value;
      var fromValue = row.querySelector('.reservierung-slot-from')?.value;
      var toValue = row.querySelector('.reservierung-slot-to')?.value;
      if (!dateValue) {
        notify('Termin ' + (i + 1) + ': Bitte Datum angeben.', 'error');
        return null;
      }
      if (!fromValue) {
        notify('Termin ' + (i + 1) + ': Bitte Von-Zeit angeben.', 'error');
        return null;
      }
      if (!toValue) {
        notify('Termin ' + (i + 1) + ': Bitte Bis-Zeit angeben.', 'error');
        return null;
      }
      var startAt = combineDateAndTime(dateValue, fromValue);
      var endDateValue = dateValue;
      var fromMinutes = parseTimeToMinutes(fromValue);
      var toMinutes = parseTimeToMinutes(toValue);
      if (fromMinutes != null && toMinutes != null && toMinutes <= fromMinutes) {
        endDateValue = addDaysToDateValue(dateValue, 1);
      }
      var endAt = combineDateAndTime(endDateValue, toValue);
      if (!startAt) {
        notify('Termin ' + (i + 1) + ': Beginn ungültig.', 'error');
        return null;
      }
      if (!endAt) {
        notify('Termin ' + (i + 1) + ': Ende ungültig.', 'error');
        return null;
      }
      if (new Date(endAt).getTime() <= new Date(startAt).getTime()) {
        notify('Termin ' + (i + 1) + ': Das Ende muss nach dem Beginn liegen.', 'error');
        return null;
      }
      slots.push({ startAt: startAt, endAt: endAt });
    }
    return slots;
  }

  function fillConflictList(listEl, conflicts) {
    if (!listEl) return;
    listEl.innerHTML = '';
    (conflicts || []).forEach(function (c) {
      var li = document.createElement('li');
      li.className = 'reservierungen-conflict-item';
      li.innerHTML =
        '<strong>' + escapeHtml(c.resourceName || 'Ressource') + '</strong>'
        + '<span class="reservierungen-conflict-item__meta">'
        + '<span>Grund: ' + escapeHtml(c.reason || '—') + '</span>'
        + '<span>Antragsteller: ' + escapeHtml(c.requesterName || '—') + '</span>'
        + '<span>Zeitraum: ' + escapeHtml(formatConflictTime(c.startAt))
        + ' – ' + escapeHtml(formatConflictTime(c.endAt)) + '</span>'
        + '</span>';
      listEl.appendChild(li);
    });
  }

  function showConflictModal(data) {
    var msg = document.getElementById('reservierung-conflict-message');
    if (msg) {
      msg.textContent = data.message || 'Mindestens eine Ressource ist bereits vergeben.';
    }
    fillConflictList(document.getElementById('reservierung-conflict-list'), data.conflicts);
    openOverlay(conflictModal);
  }

  function showApproveConflictModal(data, retry) {
    pendingApproveRetry = {
      mode: 'conflict',
      retry: retry,
      conflicts: data.conflicts || []
    };
    var msg = document.getElementById('reservierung-approve-conflict-message');
    if (msg) {
      msg.textContent = data.message || 'Fahrzeug/Raum ist in diesem Zeitraum bereits belegt.';
    }
    fillConflictList(document.getElementById('reservierung-approve-conflict-list'), data.conflicts);
    openOverlay(approveConflictModal);
  }

  function showLoeschModal(data, approveRetry) {
    var msg = document.getElementById('reservierung-loesch-message');
    var forceBtn = document.getElementById('reservierung-loesch-force');
    if (msg) {
      msg.textContent = data.message || 'Löschfahrzeug-Warnung.';
    }
    if (approveRetry) {
      pendingApproveRetry = { mode: 'loesch', retry: approveRetry, conflicts: [] };
      if (forceBtn) forceBtn.textContent = 'Trotzdem genehmigen';
    } else {
      if (forceBtn) forceBtn.textContent = 'Trotzdem senden';
    }
    openOverlay(loeschModal);
  }

  document.querySelectorAll('.reservierungen-resource-row--clickable').forEach(function (row) {
    function openFromRow() {
      openModal(row.dataset.kind, row.dataset.id, row.dataset.name || '');
    }
    row.addEventListener('click', openFromRow);
    row.addEventListener('keydown', function (ev) {
      if (ev.key === 'Enter' || ev.key === ' ') {
        ev.preventDefault();
        openFromRow();
      }
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
  document.querySelectorAll('[data-close-approve-conflict-modal]').forEach(function (btn) {
    btn.addEventListener('click', function (ev) {
      ev.preventDefault();
      pendingApproveRetry = null;
      closeOverlay(approveConflictModal);
    });
  });
  document.querySelectorAll('[data-close-loesch-modal]').forEach(function (btn) {
    btn.addEventListener('click', function (ev) {
      ev.preventDefault();
      if (pendingApproveRetry && pendingApproveRetry.mode === 'loesch') {
        pendingApproveRetry = null;
      }
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
  document.querySelectorAll('[data-close-details-modal]').forEach(function (btn) {
    btn.addEventListener('click', function (ev) {
      ev.preventDefault();
      var overlay = btn.closest('.modal-overlay');
      if (overlay) closeOverlay(overlay);
    });
  });

  function formatBerlinDateTime(iso) {
    if (!iso) return '—';
    var d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '—';
    return new Intl.DateTimeFormat('de-DE', {
      timeZone: 'Europe/Berlin',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    }).format(d);
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function fetchConflicts(kind, id) {
    var url = (kind === 'VEHICLE' ? '/reservierungen/api/fahrzeuge/' : '/reservierungen/api/raeume/')
      + id + '/conflicts?unit=' + encodeURIComponent(unitId);
    return fetch(url, { credentials: 'same-origin', headers: { Accept: 'application/json' } })
      .then(parseJsonResponse)
      .then(function (data) {
        return Array.isArray(data.conflicts) ? data.conflicts : [];
      })
      .catch(function () {
        return [];
      });
  }

  function renderDetailsConflicts(overlay, conflicts) {
    if (!overlay) return;
    var statusEl = overlay.querySelector('[data-details-status]');
    var box = overlay.querySelector('[data-details-conflicts]');
    var list = overlay.querySelector('[data-details-conflicts-list]');
    if (!statusEl || !box || !list) return;

    var statusBadge = statusEl.querySelector('.badge');
    var statusHtml = statusBadge ? statusBadge.outerHTML : statusEl.innerHTML;
    var conflictBadge = overlay.querySelector('[data-conflict-badge]');
    if (conflictBadge) conflictBadge.remove();

    if (conflicts.length > 0) {
      statusEl.innerHTML = statusHtml
        + ' <span class="badge badge--danger" data-conflict-badge>'
        + conflicts.length + ' Konflikt' + (conflicts.length > 1 ? 'e' : '')
        + '</span>';
      list.innerHTML = conflicts.map(function (c) {
        return '<div class="reservierung-details-conflict-item">'
          + '<strong>' + escapeHtml(c.resourceName || 'Ressource') + '</strong><br>'
          + '<small class="text-muted">'
          + escapeHtml(formatBerlinDateTime(c.startAt)) + ' – ' + escapeHtml(formatBerlinDateTime(c.endAt))
          + '<br>Antragsteller: ' + escapeHtml(c.requesterName || '—')
          + '<br>Grund: ' + escapeHtml(c.reason || '—')
          + '</small></div>';
      }).join('');
      box.hidden = false;
    } else {
      statusEl.innerHTML = statusHtml
        + ' <span class="badge badge-success" data-conflict-badge>Kein Konflikt</span>';
      list.innerHTML = '';
      box.hidden = true;
    }
  }

  function openDetailsOverlay(overlay) {
    if (!overlay) return;
    openOverlay(overlay);
    var kind = overlay.dataset.kind;
    var id = overlay.dataset.id;
    if (!kind || !id) return;
    var box = overlay.querySelector('[data-details-conflicts]');
    var list = overlay.querySelector('[data-details-conflicts-list]');
    if (list) list.innerHTML = '<p class="hint text-sm">Überschneidungen werden geprüft…</p>';
    if (box) box.hidden = false;
    fetchConflicts(kind, id).then(function (conflicts) {
      renderDetailsConflicts(overlay, conflicts);
    });
  }

  document.querySelectorAll('[data-open-details]').forEach(function (el) {
    el.addEventListener('click', function (ev) {
      if (ev.target.closest('[data-action]')) return;
      ev.preventDefault();
      ev.stopPropagation();
      var id = el.getAttribute('data-open-details');
      var overlay = id ? document.getElementById(id) : null;
      openDetailsOverlay(overlay);
    });
  });

  // reservierung-modal: kein Schließen per Klick auf den Overlay-Hintergrund

  pickModal?.addEventListener('click', function (ev) {
    if (ev.target === pickModal) closeOverlay(pickModal);
  });
  conflictModal?.addEventListener('click', function (ev) {
    if (ev.target === conflictModal) closeOverlay(conflictModal);
  });
  approveConflictModal?.addEventListener('click', function (ev) {
    if (ev.target === approveConflictModal) {
      pendingApproveRetry = null;
      closeOverlay(approveConflictModal);
    }
  });
  loeschModal?.addEventListener('click', function (ev) {
    if (ev.target === loeschModal) {
      if (pendingApproveRetry && pendingApproveRetry.mode === 'loesch') {
        pendingApproveRetry = null;
      }
      closeOverlay(loeschModal);
    }
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
  document.querySelectorAll('[id^="reservierung-details-"]').forEach(function (overlay) {
    overlay.addEventListener('click', function (ev) {
      if (ev.target === overlay) closeOverlay(overlay);
    });
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
  document.getElementById('reservierung-approve-conflict-force')?.addEventListener('click', function () {
    var pending = pendingApproveRetry;
    pendingApproveRetry = null;
    closeOverlay(approveConflictModal);
    if (!pending || typeof pending.retry !== 'function') {
      return;
    }
    var ids = (pending.conflicts || []).map(function (c) { return c.id; });
    pending.retry('approve_with_conflict_resolution', false, ids);
  });
  document.getElementById('reservierung-loesch-force')?.addEventListener('click', function () {
    closeOverlay(loeschModal);
    if (pendingApproveRetry && pendingApproveRetry.mode === 'loesch') {
      var retry = pendingApproveRetry.retry;
      pendingApproveRetry = null;
      if (typeof retry === 'function') {
        retry('approve', true, []);
      }
      return;
    }
    pendingCreateFlags.forceLoesch = true;
    submitCreate();
  });

  function appendTestModeEmailParam(url, delivery) {
    if (!delivery) return url;
    var sep = url.indexOf('?') >= 0 ? '&' : '?';
    return url + sep + 'testModeEmailDelivery=' + encodeURIComponent(delivery);
  }

  function askTestModeEmailDelivery(cached) {
    if (cached) {
      return Promise.resolve(cached);
    }
    if (window.FwConfirm && typeof window.FwConfirm.askTestModeEmail === 'function') {
      return window.FwConfirm.askTestModeEmail().then(function (result) {
        if (result === false || (result && result.ok === false)) {
          return null;
        }
        if (result === true) {
          return 'NONE';
        }
        return (result && result.testModeEmailDelivery) || 'NONE';
      });
    }
    return Promise.resolve('NONE');
  }

  function submitCreate() {
    var kind = currentKind();
    var resourceIds = selectedResources.map(function (r) { return Number(r.id); }).filter(Boolean);
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
    var slots = collectSlots();
    if (!slots) {
      return;
    }
    if (!slots.length) {
      notify('Bitte mindestens einen Termin angeben.', 'error');
      return;
    }

    askTestModeEmailDelivery(pendingCreateFlags.testModeEmailDelivery).then(function (delivery) {
      if (delivery == null) {
        return;
      }
      pendingCreateFlags.testModeEmailDelivery = delivery;

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
      url = appendTestModeEmailParam(url, delivery);
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
          pendingCreateFlags = { forceConflict: false, forceLoesch: false, testModeEmailDelivery: null };
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
    });
  }

  form?.addEventListener('submit', function (event) {
    event.preventDefault();
    if (!form.reportValidity()) {
      notify('Bitte alle Pflichtfelder ausfüllen.', 'error');
      return;
    }
    pendingCreateFlags = { forceConflict: false, forceLoesch: false, testModeEmailDelivery: null };
    submitCreate();
  });

  function syncImportUiForKind() {
    var kind = document.getElementById('import-kind')?.value || 'vehicle';
    var label = document.getElementById('import-primary-label');
    var extraBlock = document.getElementById('import-extra-vehicles-block');
    if (label) {
      label.innerHTML = (kind === 'vehicle' ? 'Fahrzeug' : 'Raum') + ' <span class="req">*</span>';
    }
    if (extraBlock) {
      extraBlock.hidden = kind !== 'vehicle';
    }
    importExtraResources = [];
    fillSelectOptions(document.getElementById('import-primary'), kind, null);
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
    if ((document.getElementById('import-kind')?.value || 'vehicle') !== 'vehicle') {
      return;
    }
    openPickModal('import');
  });

  wireStartToEndDate(document.getElementById('import-start'), document.getElementById('import-end-date'));

  importForm?.addEventListener('submit', function (event) {
    event.preventDefault();
    if (!importForm.reportValidity()) {
      notify('Bitte alle Pflichtfelder ausfüllen.', 'error');
      return;
    }
    var kind = document.getElementById('import-kind').value;
    var primaryId = Number(document.getElementById('import-primary')?.value || 0);
    if (!primaryId) {
      notify(kind === 'vehicle' ? 'Bitte ein Fahrzeug wählen.' : 'Bitte einen Raum wählen.', 'error');
      return;
    }
    var resourceIds = [primaryId];
    if (kind === 'vehicle') {
      importExtraResources.forEach(function (r) {
        var id = Number(r.id);
        if (id && resourceIds.indexOf(id) < 0) {
          resourceIds.push(id);
        }
      });
    }
    var startAt = localDateTimeToIso(document.getElementById('import-start').value);
    var endDate = document.getElementById('import-end-date')?.value;
    var endTime = document.getElementById('import-end-time')?.value;
    if (!startAt) {
      notify('Bitte Beginn angeben.', 'error');
      return;
    }
    if (!endDate) {
      notify('Bitte Endedatum angeben.', 'error');
      return;
    }
    if (!endTime) {
      notify('Bitte Enduhrzeit manuell eintragen.', 'error');
      return;
    }
    var endAt = combineDateAndTime(endDate, endTime);
    if (!endAt) {
      notify('Ende ungültig.', 'error');
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

  function processReservation(kind, id, action, reason, forceLoesch, conflictIds, testModeEmailDelivery) {
    var url = (kind === 'VEHICLE' ? '/reservierungen/api/fahrzeuge/' : '/reservierungen/api/raeume/')
      + id + '/process?unit=' + encodeURIComponent(unitId);
    url = appendTestModeEmailParam(url, testModeEmailDelivery);
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
      showApproveConflictModal(data, retry);
      return false;
    }
    if (data.code === 'LOESCH_WARNING') {
      showLoeschModal(data, retry);
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

  function deleteReservation(kind, id, testModeEmailDelivery, deletionReason) {
    var url = (kind === 'VEHICLE' ? '/reservierungen/api/fahrzeuge/' : '/reservierungen/api/raeume/')
      + id + '?unit=' + encodeURIComponent(unitId);
    url = appendTestModeEmailParam(url, testModeEmailDelivery);
    if (deletionReason) {
      url += '&deletionReason=' + encodeURIComponent(deletionReason);
    }
    return fetch(url, {
      method: 'DELETE',
      credentials: 'same-origin',
      headers: Object.assign({ Accept: 'application/json' }, csrfHeaders())
    }).then(parseJsonResponse);
  }

  function isFutureStartAt(startAtIso) {
    if (!startAtIso) return false;
    var startMs = Date.parse(startAtIso);
    return !Number.isNaN(startMs) && startMs > Date.now();
  }

  function runProcessAction(kind, id, action, sourceBtn) {
    var processEmailDelivery = null;

    function runApprove(approveAction, forceLoesch, conflictIds) {
      if (sourceBtn) sourceBtn.disabled = true;
      processReservation(kind, id, approveAction, '', forceLoesch, conflictIds, processEmailDelivery)
        .then(function (data) {
          if (handleProcessResult(data, function (nextAction, nextForce, nextIds) {
            runApprove(nextAction, nextForce, nextIds);
          })) {
            window.location.reload();
          } else if (sourceBtn) {
            sourceBtn.disabled = false;
          }
        })
        .catch(function () {
          notify('Genehmigung fehlgeschlagen.', 'error');
          if (sourceBtn) sourceBtn.disabled = false;
        });
    }

    if (action === 'approve') {
      askTestModeEmailDelivery(null).then(function (delivery) {
        if (delivery == null) return;
        processEmailDelivery = delivery;
        runApprove('approve', false, []);
      });
      return;
    }
    if (action === 'reject') {
      var reason = window.prompt('Begründung für die Ablehnung (optional):', '') || '';
      askTestModeEmailDelivery(null).then(function (delivery) {
        if (delivery == null) return;
        if (sourceBtn) sourceBtn.disabled = true;
        processReservation(kind, id, 'reject', reason, false, [], delivery)
          .then(function (data) {
            if (data.ok) {
              window.location.reload();
            } else {
              notify(data.message || 'Ablehnung fehlgeschlagen.', 'error');
              if (sourceBtn) sourceBtn.disabled = false;
            }
          })
          .catch(function () {
            notify('Ablehnung fehlgeschlagen.', 'error');
            if (sourceBtn) sourceBtn.disabled = false;
          });
      });
    }
  }

  document.getElementById('pending-reservations-table')?.addEventListener('click', function (event) {
    var btn = event.target.closest('[data-action]');
    if (!btn) return;
    event.stopPropagation();
    var row = btn.closest('tr');
    if (!row) return;
    runProcessAction(row.dataset.kind, row.dataset.id, btn.dataset.action, btn);
  });

  document.querySelectorAll('.reservierung-details-modal [data-action]').forEach(function (btn) {
    btn.addEventListener('click', function (ev) {
      ev.preventDefault();
      ev.stopPropagation();
      runProcessAction(btn.dataset.kind, btn.dataset.id, btn.dataset.action, btn);
    });
  });

  document.querySelectorAll('[data-action="delete"]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var row = btn.closest('tr');
      if (!row || !canWrite) return;
      var inTestMode = window.FwConfirm && typeof window.FwConfirm.isTestMode === 'function'
        ? window.FwConfirm.isTestMode()
        : false;
      var future = isFutureStartAt(row.dataset.startAt);
      var message =
        'Soll diese Reservierung wirklich gelöscht werden?\n\n' +
        'Falls vorhanden, wird der Termin auch aus DIVERA und dem Google-Kalender entfernt.';
      if (future) {
        message +=
          '\n\nDer Antragsteller erhält eine Stornierungs-E-Mail (optional mit Begründung).';
      }
      var confirmOpts = {
        title: 'Reservierung löschen?',
        message: message,
        confirmLabel: 'Löschen',
        cancelLabel: 'Abbrechen',
        variant: 'danger',
        emailSelect: inTestMode,
      };
      if (future) {
        confirmOpts.textInputLabel = 'Stornierungsgrund (optional)';
        confirmOpts.textInputPlaceholder = 'Wird dem Antragsteller in der E-Mail mitgeteilt';
      }
      var ask =
        window.FwConfirm && typeof window.FwConfirm.show === 'function'
          ? window.FwConfirm.show(confirmOpts)
          : Promise.resolve(window.confirm(message));
      ask.then(function (result) {
        var ok = result === true || (result && result.ok);
        if (!ok) return;
        var delivery = 'NONE';
        if (window.FwConfirm && window.FwConfirm.applyTestModeEmailExtra) {
          delivery = window.FwConfirm.applyTestModeEmailExtra({}, result).testModeEmailDelivery || 'NONE';
        }
        var deletionReason = future && result && result.textValue ? result.textValue : '';
        btn.disabled = true;
        deleteReservation(row.dataset.kind, row.dataset.id, delivery, deletionReason)
          .then(function (data) {
            if (data.ok) {
              notify(data.message || 'Reservierung gelöscht.', 'success');
              window.location.reload();
            } else {
              notify(data.message || 'Löschen fehlgeschlagen.', 'error');
              btn.disabled = false;
            }
          })
          .catch(function () {
            notify('Löschen fehlgeschlagen.', 'error');
            btn.disabled = false;
          });
      });
    });
  });
})();
