(function () {
  'use strict';

  var panel = document.getElementById('termine-sonderdienst-panel');
  if (!panel) {
    return;
  }

  var unitId = panel.getAttribute('data-unit-id');
  var canWrite = panel.getAttribute('data-can-write') === 'true';
  var CUSTOM_VALUE = '__custom__';
  var zeitFilter = 'bevorstehend';
  var ausbilderPickerSnapshot = null;
  var audiencePicker = window.createTermineAudiencePicker({ prefix: 'sonderdienst' });

  function getBeschreibungSelect() {
    return document.getElementById('sonderdienst-termin-beschreibung');
  }

  function getBeschreibungCustomInput() {
    return document.getElementById('sonderdienst-termin-beschreibung-custom');
  }

  function syncBeschreibungCustomVisibility() {
    var select = getBeschreibungSelect();
    var custom = getBeschreibungCustomInput();
    if (!select || !custom) {
      return;
    }
    var customMode = select.value === CUSTOM_VALUE;
    custom.hidden = !customMode;
    custom.required = customMode;
    select.required = !customMode;
  }

  function getBeschreibungValue() {
    var select = getBeschreibungSelect();
    if (!select) {
      return '';
    }
    if (select.value === CUSTOM_VALUE) {
      var custom = getBeschreibungCustomInput();
      return custom ? custom.value.trim() : '';
    }
    return select.value.trim();
  }

  function setBeschreibungValue(value) {
    var select = getBeschreibungSelect();
    var custom = getBeschreibungCustomInput();
    if (!select) {
      return;
    }
    var normalized = (value || '').trim();
    if (!normalized) {
      select.value = '';
      if (custom) {
        custom.value = '';
      }
      syncBeschreibungCustomVisibility();
      return;
    }
    var matched = false;
    Array.from(select.options).forEach(function (option) {
      if (!option.value || option.value === CUSTOM_VALUE) {
        return;
      }
      if (option.value.localeCompare(normalized, 'de', { sensitivity: 'accent' }) === 0) {
        select.value = option.value;
        matched = true;
      }
    });
    if (!matched) {
      select.value = CUSTOM_VALUE;
      if (custom) {
        custom.value = normalized;
      }
    } else if (custom) {
      custom.value = '';
    }
    syncBeschreibungCustomVisibility();
  }

  function getCsrfToken() {
    var meta = document.querySelector('meta[name="csrf-token"]');
    return meta ? meta.getAttribute('content') : '';
  }

  function getCsrfHeader() {
    var meta = document.querySelector('meta[name="csrf-header"]');
    return meta ? meta.getAttribute('content') : 'X-XSRF-TOKEN';
  }

  function apiFetch(url, options) {
    options = options || {};
    options.headers = options.headers || {};
    options.headers['Content-Type'] = 'application/json';
    options.headers[getCsrfHeader()] = getCsrfToken();
    return fetch(url, options).then(function (res) {
      return res.json();
    });
  }

  function notifyResult(result) {
    if (typeof toast === 'function') {
      toast(result.message || (result.ok ? 'Gespeichert.' : 'Fehler.'), result.ok ? 'success' : 'error');
    }
    if (result.ok) {
      window.setTimeout(function () {
        window.location.reload();
      }, 400);
    }
  }

  function openModal(id) {
    var overlay = document.getElementById(id);
    if (!overlay) {
      return;
    }
    overlay.classList.add('active');
    document.body.classList.add('modal-open');
  }

  function closeModal(overlay) {
    if (!overlay) {
      return;
    }
    if (overlay.id === 'modal-sonderdienst-termin') {
      cancelAusbilderPicker();
      audiencePicker.cancelAllPickers();
    }
    overlay.classList.remove('active');
    if (!document.querySelector('.modal-overlay.active')) {
      document.body.classList.remove('modal-open');
    }
  }

  function closeTopmostModal() {
    var active = document.querySelectorAll('.modal-overlay.active');
    if (!active.length) {
      return;
    }
    closeModal(active[active.length - 1]);
  }

  function setModalTitle(text) {
    var title = document.getElementById('sonderdienst-termin-modal-title');
    if (title) {
      title.textContent = text;
    }
  }

  function getEditingTerminId() {
    var hidden = document.getElementById('sonderdienst-termin-id');
    if (!hidden || !hidden.value) {
      return null;
    }
    var id = Number(hidden.value);
    return Number.isFinite(id) && id > 0 ? id : null;
  }

  function setEditingTerminId(id) {
    var hidden = document.getElementById('sonderdienst-termin-id');
    if (hidden) {
      hidden.value = id ? String(id) : '';
    }
  }

  function resetAusbilderCheckboxes() {
    document.querySelectorAll('.sonderdienst-ausbilder-cb').forEach(function (cb) {
      cb.checked = false;
    });
    syncAusbilderSummary();
  }

  function setAusbilderCheckboxes(idsCsv) {
    document.querySelectorAll('.sonderdienst-ausbilder-cb').forEach(function (cb) {
      cb.checked = false;
    });
    if (idsCsv) {
      idsCsv.split(',').forEach(function (rawId) {
        var id = rawId.trim();
        if (!id) {
          return;
        }
        var cb = document.querySelector('.sonderdienst-ausbilder-cb[value="' + id + '"]');
        if (cb) {
          cb.checked = true;
        }
      });
    }
    syncAusbilderSummary();
  }

  function snapshotAusbilderSelection() {
    return Array.from(document.querySelectorAll('.sonderdienst-ausbilder-cb:checked')).map(function (cb) {
      return cb.value;
    });
  }

  function restoreAusbilderSelection(ids) {
    document.querySelectorAll('.sonderdienst-ausbilder-cb').forEach(function (cb) {
      cb.checked = false;
    });
    (ids || []).forEach(function (id) {
      var cb = document.querySelector('.sonderdienst-ausbilder-cb[value="' + id + '"]');
      if (cb) {
        cb.checked = true;
      }
    });
    syncAusbilderSummary();
  }

  function ausbilderDisplayName(cb) {
    if (!cb) {
      return '';
    }
    var fromAttr = cb.getAttribute('data-display-name');
    if (fromAttr) {
      return fromAttr.trim();
    }
    var label = cb.closest('.user-picker__item');
    if (!label) {
      return '';
    }
    var nameEl = label.querySelector('.user-picker__name');
    return nameEl ? nameEl.textContent.trim() : '';
  }

  function syncAusbilderSummary() {
    var summary = document.getElementById('sonderdienst-ausbilder-summary');
    var empty = document.getElementById('sonderdienst-ausbilder-empty');
    var count = document.getElementById('sonderdienst-ausbilder-count');
    var checked = document.querySelectorAll('.sonderdienst-ausbilder-cb:checked');
    var names = [];
    checked.forEach(function (cb) {
      var name = ausbilderDisplayName(cb);
      if (name) {
        names.push(name);
      }
    });
    if (count) {
      count.textContent = String(names.length);
    }
    if (summary) {
      summary.innerHTML = '';
      names.forEach(function (name) {
        var chip = document.createElement('span');
        chip.className = 'termine-ausbilder-chip';
        chip.textContent = name;
        summary.appendChild(chip);
      });
      summary.hidden = names.length === 0;
    }
    if (empty) {
      empty.hidden = names.length > 0;
    }
  }

  function resetAusbilderSearch() {
    var search = document.getElementById('sonderdienst-ausbilder-search');
    var emptyHint = document.getElementById('sonderdienst-ausbilder-search-empty');
    var picker = document.getElementById('sonderdienst-ausbilder-picker');
    if (!search || !picker) {
      return;
    }
    search.value = '';
    picker.querySelectorAll('.user-picker__item').forEach(function (item) {
      item.style.display = '';
    });
    if (emptyHint) {
      emptyHint.hidden = true;
    }
  }

  function openAusbilderPickerModal() {
    var overlay = document.getElementById('modal-sonderdienst-ausbilder');
    if (!overlay) {
      return;
    }
    ausbilderPickerSnapshot = snapshotAusbilderSelection();
    resetAusbilderSearch();
    overlay.classList.add('active');
    document.body.classList.add('modal-open');
    var search = document.getElementById('sonderdienst-ausbilder-search');
    if (search) {
      window.setTimeout(function () {
        search.focus();
      }, 50);
    }
  }

  function closeAusbilderPickerModal() {
    var overlay = document.getElementById('modal-sonderdienst-ausbilder');
    if (!overlay) {
      return;
    }
    overlay.classList.remove('active');
    if (!document.querySelector('.modal-overlay.active')) {
      document.body.classList.remove('modal-open');
    }
  }

  function applyAusbilderPicker() {
    ausbilderPickerSnapshot = null;
    syncAusbilderSummary();
    closeAusbilderPickerModal();
  }

  function cancelAusbilderPicker() {
    if (ausbilderPickerSnapshot) {
      restoreAusbilderSelection(ausbilderPickerSnapshot);
      ausbilderPickerSnapshot = null;
    }
    closeAusbilderPickerModal();
  }

  function selectedAusbilderIds() {
    return Array.from(document.querySelectorAll('.sonderdienst-ausbilder-cb:checked'))
      .map(function (cb) {
        return Number(cb.value);
      })
      .filter(function (id) {
        return Number.isFinite(id) && id > 0;
      });
  }

  function resetSonderdienstModal() {
    setEditingTerminId(null);
    setModalTitle('Neuer Termin');
    var datum = document.getElementById('sonderdienst-termin-datum');
    var beginn = document.getElementById('sonderdienst-termin-beginn');
    var ende = document.getElementById('sonderdienst-termin-ende');
    if (datum) {
      datum.value = '';
    }
    setBeschreibungValue('');
    if (beginn) {
      beginn.value = '19:00';
    }
    if (ende) {
      ende.value = '22:00';
    }
    resetAusbilderCheckboxes();
    var audienceAll = document.getElementById('sonderdienst-audience-all');
    if (audienceAll) {
      audienceAll.checked = true;
    }
    audiencePicker.reset();
    syncAudiencePickVisibility();
  }

  function syncAudiencePickVisibility() {
    var audienceAll = document.getElementById('sonderdienst-audience-all');
    var pick = document.getElementById('sonderdienst-audience-pick');
    if (!audienceAll || !pick) {
      return;
    }
    pick.hidden = audienceAll.checked;
  }

  function parseTerminEnd(row) {
    var datum = row.getAttribute('data-datum');
    var ende = row.getAttribute('data-ende') || row.getAttribute('data-beginn') || '23:59';
    if (!datum) {
      return null;
    }
    var dateParts = datum.split('-');
    var timeParts = ende.split(':');
    if (dateParts.length !== 3) {
      return null;
    }
    return new Date(
      Number(dateParts[0]),
      Number(dateParts[1]) - 1,
      Number(dateParts[2]),
      Number(timeParts[0] || 0),
      Number(timeParts[1] || 0)
    );
  }

  function isTerminVergangen(row) {
    var endAt = parseTerminEnd(row);
    if (!endAt) {
      return false;
    }
    return endAt.getTime() < Date.now();
  }

  function rowMatchesZeitFilter(row) {
    if (zeitFilter === 'alle') {
      return true;
    }
    var vergangen = isTerminVergangen(row);
    if (zeitFilter === 'vergangen') {
      return vergangen;
    }
    return !vergangen;
  }

  function applyZeitFilter() {
    var rows = document.querySelectorAll('.sonderdienst-termin-row');
    var visibleCount = 0;
    rows.forEach(function (row) {
      var show = rowMatchesZeitFilter(row);
      row.hidden = !show;
      if (show) {
        visibleCount++;
      }
    });
    var emptyHint = document.getElementById('sonderdienst-filter-empty');
    var tableWrap = document.getElementById('sonderdienst-table-wrap');
    if (emptyHint) {
      emptyHint.hidden = visibleCount > 0;
    }
    if (tableWrap) {
      tableWrap.hidden = visibleCount === 0;
    }
  }

  function bindZeitFilter() {
    var select = document.getElementById('sonderdienst-filter-zeit');
    if (!select) {
      return;
    }
    zeitFilter = select.value || 'bevorstehend';
    select.addEventListener('change', function () {
      zeitFilter = select.value || 'alle';
      applyZeitFilter();
    });
    applyZeitFilter();
  }

  function bindAusbilderPicker() {
    var openBtn = document.getElementById('sonderdienst-ausbilder-open-btn');
    var applyBtn = document.getElementById('sonderdienst-ausbilder-apply');
    var overlay = document.getElementById('modal-sonderdienst-ausbilder');
    var search = document.getElementById('sonderdienst-ausbilder-search');
    var picker = document.getElementById('sonderdienst-ausbilder-picker');
    var emptyHint = document.getElementById('sonderdienst-ausbilder-search-empty');

    if (openBtn) {
      openBtn.addEventListener('click', openAusbilderPickerModal);
    }
    if (applyBtn) {
      applyBtn.addEventListener('click', applyAusbilderPicker);
    }
    document.querySelectorAll('[data-close-sonderdienst-ausbilder-modal]').forEach(function (btn) {
      btn.addEventListener('click', cancelAusbilderPicker);
    });
    if (overlay) {
      overlay.addEventListener('click', function (e) {
        if (e.target === overlay) {
          cancelAusbilderPicker();
        }
      });
    }
    if (search && picker) {
      search.addEventListener('input', function () {
        var query = search.value.trim().toLowerCase();
        var visible = 0;
        picker.querySelectorAll('.user-picker__item').forEach(function (item) {
          var haystack = (item.getAttribute('data-search') || '').toLowerCase();
          var match = !query || haystack.indexOf(query) !== -1;
          item.style.display = match ? '' : 'none';
          if (match) {
            visible++;
          }
        });
        if (emptyHint) {
          emptyHint.hidden = visible > 0;
        }
      });
    }
    syncAusbilderSummary();
  }

  function bindBeschreibungSelect() {
    var select = getBeschreibungSelect();
    var custom = getBeschreibungCustomInput();
    if (!select) {
      return;
    }
    select.addEventListener('change', function () {
      syncBeschreibungCustomVisibility();
      if (select.value === CUSTOM_VALUE && custom) {
        custom.focus();
      }
    });
    syncBeschreibungCustomVisibility();
  }

  function openCreateModal() {
    resetSonderdienstModal();
    openModal('modal-sonderdienst-termin');
    var select = getBeschreibungSelect();
    if (select) {
      select.focus();
    }
  }

  function openEditModal(row) {
    if (!row) {
      return;
    }
    resetSonderdienstModal();
    setEditingTerminId(row.getAttribute('data-id'));
    setModalTitle('Termin bearbeiten');

    var datum = document.getElementById('sonderdienst-termin-datum');
    var beginn = document.getElementById('sonderdienst-termin-beginn');
    var ende = document.getElementById('sonderdienst-termin-ende');
    var audienceAll = document.getElementById('sonderdienst-audience-all');
    if (datum) {
      datum.value = row.getAttribute('data-datum') || '';
    }
    setBeschreibungValue(row.getAttribute('data-beschreibung') || '');
    if (beginn) {
      beginn.value = row.getAttribute('data-beginn') || '19:00';
    }
    if (ende) {
      ende.value = row.getAttribute('data-ende') || '22:00';
    }
    if (audienceAll) {
      audienceAll.checked = row.getAttribute('data-audience-all') === 'true';
    }
    audiencePicker.setGroupsFromCsv(row.getAttribute('data-group-ids'));
    audiencePicker.setPersonsFromCsv(row.getAttribute('data-person-ids'));
    setAusbilderCheckboxes(row.getAttribute('data-instructor-ids'));
    syncAudiencePickVisibility();
    openModal('modal-sonderdienst-termin');
    var select = getBeschreibungSelect();
    if (select) {
      select.focus();
    }
  }

  function buildSaveBody() {
    var datum = document.getElementById('sonderdienst-termin-datum');
    var beginn = document.getElementById('sonderdienst-termin-beginn');
    var ende = document.getElementById('sonderdienst-termin-ende');
    var audienceAll = document.getElementById('sonderdienst-audience-all');
    var beschreibungValue = getBeschreibungValue();
    if (!datum || !beginn || !ende) {
      return null;
    }
    var appliesToAll = !audienceAll || audienceAll.checked;
    var groupIds = appliesToAll ? [] : audiencePicker.getGroupIds();
    var personIds = appliesToAll ? [] : audiencePicker.getPersonIds();
    if (!appliesToAll && groupIds.length === 0 && personIds.length === 0) {
      if (typeof toast === 'function') {
        toast('Bitte mindestens eine Gruppe oder Person auswÃ¤hlen, oder â€žAlleâ€œ aktiv lassen.', 'warning');
      }
      return null;
    }
    var body = {
      terminDatum: datum.value,
      thema: beschreibungValue,
      dienstBeginn: beginn.value,
      dienstEnde: ende.value,
      instructorPersonIds: selectedAusbilderIds(),
      audienceAll: appliesToAll,
      groupIds: groupIds,
      personIds: personIds
    };
    if (!body.terminDatum || !body.thema || !body.dienstBeginn || !body.dienstEnde) {
      if (typeof toast === 'function') {
        toast('Bitte Datum, Beschreibung, Beginn und Ende ausfÃ¼llen.', 'warning');
      }
      return null;
    }
    return body;
  }

  function saveSonderdienstTermin() {
    var body = buildSaveBody();
    if (!body) {
      return;
    }
    var terminId = getEditingTerminId();
    var url = '/termine/api/sonderdienst?unit=' + encodeURIComponent(unitId);
    var method = 'POST';
    if (terminId) {
      url = '/termine/api/sonderdienst/' + encodeURIComponent(terminId) + '?unit=' + encodeURIComponent(unitId);
      method = 'PUT';
    }
    apiFetch(url, {
      method: method,
      body: JSON.stringify(body)
    }).then(notifyResult);
  }

  function deleteSonderdienstTermin(terminId) {
    if (!terminId) {
      return;
    }
    if (!window.confirm('Termin wirklich lÃ¶schen?')) {
      return;
    }
    apiFetch('/termine/api/sonderdienst/' + encodeURIComponent(terminId) + '?unit=' + encodeURIComponent(unitId), {
      method: 'DELETE'
    }).then(notifyResult);
  }

  document.querySelectorAll('[data-close-modal]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      closeModal(btn.closest('.modal-overlay'));
    });
  });

  document.querySelectorAll('.modal-overlay').forEach(function (overlay) {
    overlay.addEventListener('click', function (e) {
      if (e.target === overlay) {
        closeModal(overlay);
      }
    });
  });

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      closeTopmostModal();
    }
  });

  var audienceAllCheckbox = document.getElementById('sonderdienst-audience-all');
  if (audienceAllCheckbox) {
    audienceAllCheckbox.addEventListener('change', syncAudiencePickVisibility);
    syncAudiencePickVisibility();
  }

  audiencePicker.bind();
  bindAusbilderPicker();
  bindBeschreibungSelect();
  bindZeitFilter();

  if (canWrite) {
    var newBtn = document.getElementById('termine-new-sonderdienst-btn');
    var emptyBtn = document.getElementById('termine-new-sonderdienst-empty-btn');
    var saveBtn = document.getElementById('sonderdienst-save-termin');
    if (newBtn) {
      newBtn.addEventListener('click', openCreateModal);
    }
    if (emptyBtn) {
      emptyBtn.addEventListener('click', openCreateModal);
    }
    if (saveBtn) {
      saveBtn.addEventListener('click', saveSonderdienstTermin);
    }
    document.querySelectorAll('[data-edit-sonderdienst-termin]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        openEditModal(btn.closest('.sonderdienst-termin-row'));
      });
    });
    document.querySelectorAll('[data-delete-sonderdienst-termin]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var row = btn.closest('.sonderdienst-termin-row');
        deleteSonderdienstTermin(row ? row.getAttribute('data-id') : null);
      });
    });
  }
})();
