(function () {
  'use strict';

  var pickerSnapshot = [];

  function hiddenIdsField() {
    return document.getElementById('instructorPersonIdsJson');
  }

  function hiddenNamesField() {
    return document.getElementById('incidentCommander');
  }

  function parseInitialIds() {
    var field = hiddenIdsField();
    if (!field || !field.value) {
      return [];
    }
    try {
      var ids = JSON.parse(field.value);
      return Array.isArray(ids) ? ids.map(String) : [];
    } catch (e) {
      return [];
    }
  }

  function selectedIds() {
    return Array.from(document.querySelectorAll('.anwesenheit-ausbilder-cb:checked')).map(function (cb) {
      return cb.value;
    });
  }

  function displayNameForCheckbox(cb) {
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

  function syncHiddenFields() {
    var ids = selectedIds().map(Number).filter(function (id) {
      return !isNaN(id);
    });
    var names = [];
    document.querySelectorAll('.anwesenheit-ausbilder-cb:checked').forEach(function (cb) {
      var name = displayNameForCheckbox(cb);
      if (name) {
        names.push(name);
      }
    });
    var idsField = hiddenIdsField();
    var namesField = hiddenNamesField();
    if (idsField) {
      idsField.value = JSON.stringify(ids);
    }
    if (namesField) {
      namesField.value = names.join(', ');
      namesField.dispatchEvent(new Event('change', { bubbles: true }));
    }
    syncSummary();
    syncInstructorsInvolved(ids);
  }

  function syncInstructorsInvolved(ids) {
    if (!window.BerichteKraefte || !window.BerichteKraefte.ensurePersonInvolved) {
      return;
    }
    (ids || []).forEach(function (id) {
      window.BerichteKraefte.ensurePersonInvolved(id);
    });
  }

  function syncSummary() {
    var summary = document.getElementById('anwesenheit-ausbilder-summary');
    var empty = document.getElementById('anwesenheit-ausbilder-empty');
    var count = document.getElementById('anwesenheit-ausbilder-count');
    var names = [];
    document.querySelectorAll('.anwesenheit-ausbilder-cb:checked').forEach(function (cb) {
      var name = displayNameForCheckbox(cb);
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

  function applyInitialSelection() {
    var ids = parseInitialIds();
    document.querySelectorAll('.anwesenheit-ausbilder-cb').forEach(function (cb) {
      cb.checked = ids.indexOf(cb.value) >= 0;
    });
    syncSummary();
  }

  function snapshotSelection() {
    return selectedIds();
  }

  function restoreSelection(ids) {
    document.querySelectorAll('.anwesenheit-ausbilder-cb').forEach(function (cb) {
      cb.checked = false;
    });
    (ids || []).forEach(function (id) {
      var cb = document.querySelector('.anwesenheit-ausbilder-cb[value="' + id + '"]');
      if (cb) {
        cb.checked = true;
      }
    });
    syncSummary();
  }

  function resetSearch() {
    var search = document.getElementById('anwesenheit-ausbilder-search');
    var emptyHint = document.getElementById('anwesenheit-ausbilder-search-empty');
    var picker = document.getElementById('anwesenheit-ausbilder-picker');
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

  function openModal() {
    var overlay = document.getElementById('modal-anwesenheit-ausbilder');
    if (!overlay) {
      return;
    }
    pickerSnapshot = snapshotSelection();
    resetSearch();
    overlay.hidden = false;
    overlay.classList.add('active');
    document.body.classList.add('modal-open');
    var search = document.getElementById('anwesenheit-ausbilder-search');
    if (search) {
      window.setTimeout(function () {
        search.focus();
      }, 50);
    }
  }

  function closeModal(restore) {
    var overlay = document.getElementById('modal-anwesenheit-ausbilder');
    if (!overlay) {
      return;
    }
    if (restore) {
      restoreSelection(pickerSnapshot);
    }
    overlay.hidden = true;
    overlay.classList.remove('active');
    if (!document.querySelector('.modal-overlay.active:not([hidden])')) {
      document.body.classList.remove('modal-open');
    }
  }

  function bindSearch() {
    var search = document.getElementById('anwesenheit-ausbilder-search');
    var picker = document.getElementById('anwesenheit-ausbilder-picker');
    var emptyHint = document.getElementById('anwesenheit-ausbilder-search-empty');
    if (!search || !picker) {
      return;
    }
    search.addEventListener('input', function () {
      var q = search.value.trim().toLocaleLowerCase('de');
      var visible = 0;
      picker.querySelectorAll('.user-picker__item').forEach(function (item) {
        var nameEl = item.querySelector('.user-picker__name');
        var name = nameEl ? nameEl.textContent.trim().toLocaleLowerCase('de') : '';
        var show = !q || name.indexOf(q) >= 0;
        item.style.display = show ? '' : 'none';
        if (show) {
          visible++;
        }
      });
      if (emptyHint) {
        emptyHint.hidden = visible > 0;
      }
    });
  }

  function bind(scope) {
    var root = scope || document;
    var openBtn = root.querySelector('#anwesenheit-ausbilder-open-btn');
    if (!openBtn || openBtn.dataset.bound === 'true') {
      return;
    }
    openBtn.dataset.bound = 'true';
    applyInitialSelection();
    bindSearch();
    openBtn.addEventListener('click', openModal);
    root.querySelector('#anwesenheit-ausbilder-apply')?.addEventListener('click', function (e) {
      e.preventDefault();
      e.stopPropagation();
      syncHiddenFields();
      closeModal(false);
    });
    root.querySelectorAll('[data-close-anwesenheit-ausbilder-modal]').forEach(function (btn) {
      btn.addEventListener('click', function (e) {
        e.preventDefault();
        e.stopPropagation();
        closeModal(true);
      });
    });
    // Schließen nur über Übernehmen, Abbrechen oder ✕ — nicht per Hintergrundklick.
    var form = root.querySelector('#anwesenheitsliste-form');
    if (form) {
      form.addEventListener('submit', syncHiddenFields, true);
    }
    syncInstructorsInvolved(parseInitialIds().map(Number));
  }

  window.BerichteAnwesenheitInstructor = { init: bind };

  document.addEventListener('DOMContentLoaded', function () {
    bind(document);
  });
})();
