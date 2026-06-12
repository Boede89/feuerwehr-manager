(function () {
  'use strict';

  var panel = document.getElementById('termine-dienstplan-panel');
  if (!panel) {
    return;
  }

  var unitId = panel.getAttribute('data-unit-id');
  var canWrite = panel.getAttribute('data-can-write') === 'true';
  var instructorGroups = [];
  if (Array.isArray(window.TERMINE_INSTRUCTOR_GROUPS)) {
    instructorGroups = window.TERMINE_INSTRUCTOR_GROUPS;
  }
  var lastAppliedThema = '';
  var zeitFilter = 'bevorstehend';

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
    overlay.classList.remove('active');
    if (!document.querySelector('.modal-overlay.active')) {
      document.body.classList.remove('modal-open');
    }
  }

  function setModalTitle(text) {
    var title = document.getElementById('dienstplan-termin-modal-title');
    if (title) {
      title.textContent = text;
    }
  }

  function getEditingTerminId() {
    var hidden = document.getElementById('dienstplan-termin-id');
    if (!hidden || !hidden.value) {
      return null;
    }
    var id = Number(hidden.value);
    return Number.isFinite(id) && id > 0 ? id : null;
  }

  function setEditingTerminId(id) {
    var hidden = document.getElementById('dienstplan-termin-id');
    if (hidden) {
      hidden.value = id ? String(id) : '';
    }
  }

  function selectOptionsByIds(selectEl, idsCsv) {
    if (!selectEl) {
      return;
    }
    Array.from(selectEl.options).forEach(function (option) {
      option.selected = false;
    });
    if (!idsCsv) {
      return;
    }
    idsCsv.split(',').forEach(function (rawId) {
      var id = rawId.trim();
      if (!id) {
        return;
      }
      var option = selectEl.querySelector('option[value="' + id + '"]');
      if (option) {
        option.selected = true;
      }
    });
  }

  function resetDienstplanModal() {
    setEditingTerminId(null);
    setModalTitle('Neuer Dienstplan-Termin');
    var datum = document.getElementById('dienstplan-termin-datum');
    var thema = document.getElementById('dienstplan-termin-thema');
    var beginn = document.getElementById('dienstplan-termin-beginn');
    var ende = document.getElementById('dienstplan-termin-ende');
    var ausbilder = document.getElementById('dienstplan-termin-ausbilder');
    if (datum) {
      datum.value = '';
    }
    if (thema) {
      thema.value = '';
    }
    if (beginn) {
      beginn.value = '19:00';
    }
    if (ende) {
      ende.value = '22:00';
    }
    if (ausbilder) {
      Array.from(ausbilder.options).forEach(function (option) {
        option.selected = false;
      });
    }
    lastAppliedThema = '';
    var audienceAll = document.getElementById('dienstplan-audience-all');
    var groups = document.getElementById('dienstplan-audience-groups');
    var persons = document.getElementById('dienstplan-audience-persons');
    if (audienceAll) {
      audienceAll.checked = true;
    }
    if (groups) {
      Array.from(groups.options).forEach(function (option) {
        option.selected = false;
      });
    }
    if (persons) {
      Array.from(persons.options).forEach(function (option) {
        option.selected = false;
      });
    }
    syncAudiencePickVisibility();
  }

  function syncAudiencePickVisibility() {
    var audienceAll = document.getElementById('dienstplan-audience-all');
    var pick = document.getElementById('dienstplan-audience-pick');
    if (!audienceAll || !pick) {
      return;
    }
    pick.hidden = audienceAll.checked;
  }

  function normalizeThema(value) {
    return (value || '').trim();
  }

  function themaMatches(left, right) {
    if (!left || !right) {
      return false;
    }
    return left.localeCompare(right, 'de', { sensitivity: 'accent' }) === 0;
  }

  function instructorIdsForThema(thema) {
    var ids = [];
    instructorGroups.forEach(function (group) {
      if (!group || !themaMatches(group.thema, thema)) {
        return;
      }
      (group.personIds || []).forEach(function (id) {
        var numericId = Number(id);
        if (Number.isFinite(numericId) && numericId > 0 && ids.indexOf(numericId) === -1) {
          ids.push(numericId);
        }
      });
    });
    return ids;
  }

  function applyInstructorsForThema(thema, force) {
    if (getEditingTerminId() && !force) {
      return;
    }
    var normalized = normalizeThema(thema);
    if (!normalized) {
      lastAppliedThema = '';
      return;
    }
    if (!force && normalized === lastAppliedThema) {
      return;
    }
    var ausbilder = document.getElementById('dienstplan-termin-ausbilder');
    if (!ausbilder) {
      return;
    }
    var ids = instructorIdsForThema(normalized);
    if (ids.length === 0) {
      lastAppliedThema = normalized;
      return;
    }
    ids.forEach(function (id) {
      var option = ausbilder.querySelector('option[value="' + id + '"]');
      if (option) {
        option.selected = true;
      }
    });
    lastAppliedThema = normalized;
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
    var rows = document.querySelectorAll('.dienstplan-termin-row');
    var visibleCount = 0;
    rows.forEach(function (row) {
      var show = rowMatchesZeitFilter(row);
      row.hidden = !show;
      if (show) {
        visibleCount++;
      }
    });
    var emptyHint = document.getElementById('dienstplan-filter-empty');
    var tableWrap = document.getElementById('dienstplan-table-wrap');
    if (emptyHint) {
      emptyHint.hidden = visibleCount > 0;
    }
    if (tableWrap) {
      tableWrap.hidden = visibleCount === 0;
    }
  }

  function bindZeitFilter() {
    var select = document.getElementById('dienstplan-filter-zeit');
    if (!select) {
      return;
    }
    zeitFilter = select.value || 'alle';
    select.addEventListener('change', function () {
      zeitFilter = select.value || 'alle';
      applyZeitFilter();
    });
    applyZeitFilter();
  }

  function bindThemaInstructorAutofill() {
    var themaInput = document.getElementById('dienstplan-termin-thema');
    if (!themaInput) {
      return;
    }
    themaInput.addEventListener('change', function () {
      applyInstructorsForThema(themaInput.value, true);
    });
    themaInput.addEventListener('blur', function () {
      applyInstructorsForThema(themaInput.value, false);
    });
  }

  function selectedOptionValues(selectEl) {
    if (!selectEl) {
      return [];
    }
    return Array.from(selectEl.selectedOptions)
      .map(function (option) {
        return Number(option.value);
      })
      .filter(function (id) {
        return Number.isFinite(id) && id > 0;
      });
  }

  function openCreateModal() {
    resetDienstplanModal();
    openModal('modal-dienstplan-termin');
    var thema = document.getElementById('dienstplan-termin-thema');
    if (thema) {
      thema.focus();
    }
  }

  function openEditModal(row) {
    if (!row) {
      return;
    }
    resetDienstplanModal();
    setEditingTerminId(row.getAttribute('data-id'));
    setModalTitle('Termin bearbeiten');

    var datum = document.getElementById('dienstplan-termin-datum');
    var thema = document.getElementById('dienstplan-termin-thema');
    var beginn = document.getElementById('dienstplan-termin-beginn');
    var ende = document.getElementById('dienstplan-termin-ende');
    var audienceAll = document.getElementById('dienstplan-audience-all');
    var groups = document.getElementById('dienstplan-audience-groups');
    var persons = document.getElementById('dienstplan-audience-persons');
    var ausbilder = document.getElementById('dienstplan-termin-ausbilder');

    if (datum) {
      datum.value = row.getAttribute('data-datum') || '';
    }
    if (thema) {
      thema.value = row.getAttribute('data-thema') || '';
    }
    if (beginn) {
      beginn.value = row.getAttribute('data-beginn') || '19:00';
    }
    if (ende) {
      ende.value = row.getAttribute('data-ende') || '22:00';
    }
    if (audienceAll) {
      audienceAll.checked = row.getAttribute('data-audience-all') === 'true';
    }
    selectOptionsByIds(groups, row.getAttribute('data-group-ids'));
    selectOptionsByIds(persons, row.getAttribute('data-person-ids'));
    selectOptionsByIds(ausbilder, row.getAttribute('data-instructor-ids'));
    lastAppliedThema = normalizeThema(thema ? thema.value : '');
    syncAudiencePickVisibility();
    openModal('modal-dienstplan-termin');
    if (thema) {
      thema.focus();
    }
  }

  function buildSaveBody() {
    var datum = document.getElementById('dienstplan-termin-datum');
    var thema = document.getElementById('dienstplan-termin-thema');
    var beginn = document.getElementById('dienstplan-termin-beginn');
    var ende = document.getElementById('dienstplan-termin-ende');
    var ausbilder = document.getElementById('dienstplan-termin-ausbilder');
    var audienceAll = document.getElementById('dienstplan-audience-all');
    var groups = document.getElementById('dienstplan-audience-groups');
    var persons = document.getElementById('dienstplan-audience-persons');
    if (!datum || !thema || !beginn || !ende) {
      return null;
    }
    var appliesToAll = !audienceAll || audienceAll.checked;
    var groupIds = appliesToAll ? [] : selectedOptionValues(groups);
    var personIds = appliesToAll ? [] : selectedOptionValues(persons);
    if (!appliesToAll && groupIds.length === 0 && personIds.length === 0) {
      if (typeof toast === 'function') {
        toast('Bitte mindestens eine Gruppe oder Person auswählen, oder „Alle“ aktiv lassen.', 'warning');
      }
      return null;
    }
    var body = {
      terminDatum: datum.value,
      thema: thema.value.trim(),
      dienstBeginn: beginn.value,
      dienstEnde: ende.value,
      instructorPersonIds: selectedOptionValues(ausbilder),
      audienceAll: appliesToAll,
      groupIds: groupIds,
      personIds: personIds
    };
    if (!body.terminDatum || !body.thema || !body.dienstBeginn || !body.dienstEnde) {
      if (typeof toast === 'function') {
        toast('Bitte Datum, Thema, Dienstbeginn und Dienstende ausfüllen.', 'warning');
      }
      return null;
    }
    return body;
  }

  function saveDienstplanTermin() {
    var body = buildSaveBody();
    if (!body) {
      return;
    }
    var terminId = getEditingTerminId();
    var url = '/termine/api/dienstplan?unit=' + encodeURIComponent(unitId);
    var method = 'POST';
    if (terminId) {
      url = '/termine/api/dienstplan/' + encodeURIComponent(terminId) + '?unit=' + encodeURIComponent(unitId);
      method = 'PUT';
    }
    apiFetch(url, {
      method: method,
      body: JSON.stringify(body)
    }).then(notifyResult);
  }

  function deleteDienstplanTermin(terminId) {
    if (!terminId) {
      return;
    }
    if (!window.confirm('Termin wirklich löschen?')) {
      return;
    }
    apiFetch('/termine/api/dienstplan/' + encodeURIComponent(terminId) + '?unit=' + encodeURIComponent(unitId), {
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
      document.querySelectorAll('.modal-overlay.active').forEach(closeModal);
    }
  });

  var audienceAllCheckbox = document.getElementById('dienstplan-audience-all');
  if (audienceAllCheckbox) {
    audienceAllCheckbox.addEventListener('change', syncAudiencePickVisibility);
    syncAudiencePickVisibility();
  }

  bindThemaInstructorAutofill();
  bindZeitFilter();

  if (canWrite) {
    var newBtn = document.getElementById('termine-new-dienstplan-btn');
    var emptyBtn = document.getElementById('termine-new-dienstplan-empty-btn');
    var saveBtn = document.getElementById('dienstplan-save-termin');
    if (newBtn) {
      newBtn.addEventListener('click', openCreateModal);
    }
    if (emptyBtn) {
      emptyBtn.addEventListener('click', openCreateModal);
    }
    if (saveBtn) {
      saveBtn.addEventListener('click', saveDienstplanTermin);
    }
    document.querySelectorAll('[data-edit-dienstplan-termin]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        openEditModal(btn.closest('.dienstplan-termin-row'));
      });
    });
    document.querySelectorAll('[data-delete-dienstplan-termin]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var row = btn.closest('.dienstplan-termin-row');
        deleteDienstplanTermin(row ? row.getAttribute('data-id') : null);
      });
    });
  }
})();
