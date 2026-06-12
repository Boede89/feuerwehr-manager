(function () {
  'use strict';

  var STATUS_OPTIONS = [
    { value: 'PRESENT', label: 'Anwesend' },
    { value: 'ABSENT', label: 'Abwesend' },
    { value: 'EXCUSED', label: 'Entschuldigt' }
  ];

  var personnel = [];
  var readonly = false;
  var unitPersons = [];

  function esc(text) {
    var div = document.createElement('div');
    div.textContent = text == null ? '' : String(text);
    return div.innerHTML;
  }

  function switchTab(idx) {
    document.querySelectorAll('.incident-tab').forEach(function (btn) {
      btn.classList.toggle('tab-btn--active', Number(btn.dataset.tab) === idx);
    });
    document.querySelectorAll('.incident-tab-panel').forEach(function (panel) {
      panel.hidden = Number(panel.dataset.panel) !== idx;
    });
  }

  function syncHiddenJson() {
    var hidden = document.getElementById('personnelJson');
    if (hidden) {
      hidden.value = JSON.stringify(personnel);
    }
  }

  function renderPersonnel() {
    var tbody = document.getElementById('anwesenheit-personnel-body');
    var empty = document.getElementById('anwesenheit-personnel-empty');
    if (!tbody) {
      return;
    }
    if (!personnel.length) {
      tbody.innerHTML = '';
      if (empty) {
        empty.hidden = false;
      }
      syncHiddenJson();
      return;
    }
    if (empty) {
      empty.hidden = true;
    }
    tbody.innerHTML = personnel.map(function (row, index) {
      var statusCell = readonly
        ? '<td>' + esc(statusLabel(row.status)) + '</td>'
        : '<td><select class="field anwesenheit-status-select" data-index="' + index + '">' +
          STATUS_OPTIONS.map(function (opt) {
            return '<option value="' + esc(opt.value) + '"' +
              (row.status === opt.value ? ' selected' : '') + '>' + esc(opt.label) + '</option>';
          }).join('') +
          '</select></td>';
      var actionCell = readonly
        ? ''
        : '<td><button type="button" class="btn btn--danger btn--sm" data-remove-index="' + index + '">Entfernen</button></td>';
      return '<tr>' +
        '<td>' + esc(row.displayName) + '</td>' +
        statusCell +
        actionCell +
        '</tr>';
    }).join('');

    if (!readonly) {
      tbody.querySelectorAll('.anwesenheit-status-select').forEach(function (sel) {
        sel.addEventListener('change', function () {
          var idx = Number(sel.dataset.index);
          if (personnel[idx]) {
            personnel[idx].status = sel.value;
            syncHiddenJson();
          }
        });
      });
      tbody.querySelectorAll('[data-remove-index]').forEach(function (btn) {
        btn.addEventListener('click', function () {
          personnel.splice(Number(btn.dataset.removeIndex), 1);
          renderPersonnel();
        });
      });
    }
    syncHiddenJson();
  }

  function statusLabel(value) {
    var found = STATUS_OPTIONS.find(function (opt) { return opt.value === value; });
    return found ? found.label : value;
  }

  function addPersonFromUnit() {
    if (!unitPersons.length) {
      window.alert('Keine Personen in der Einheit vorhanden.');
      return;
    }
    var options = unitPersons.map(function (p, i) {
      return (i + 1) + '. ' + p.name;
    }).join('\n');
    var input = window.prompt('Person hinzufügen — Nummer eingeben:\n\n' + options);
    if (input == null || input.trim() === '') {
      return;
    }
    var index = Number(input.trim()) - 1;
    if (Number.isNaN(index) || index < 0 || index >= unitPersons.length) {
      window.alert('Ungültige Auswahl.');
      return;
    }
    var person = unitPersons[index];
    if (personnel.some(function (row) { return row.personId === person.id; })) {
      window.alert('Person ist bereits in der Liste.');
      return;
    }
    personnel.push({
      personId: person.id,
      displayName: person.name,
      status: 'PRESENT'
    });
    renderPersonnel();
  }

  function bindAutoNumber() {
    var dateInput = document.getElementById('eventDate');
    var numberInput = document.getElementById('reportNumber');
    if (!dateInput || !numberInput || numberInput.dataset.autoNumber !== 'true') {
      return;
    }
    var unitId = numberInput.dataset.unitId;
    function refreshNumber() {
      if (!dateInput.value || !unitId) {
        return;
      }
      fetch('/berichte/anwesenheitslisten/suggest-number?unit=' + encodeURIComponent(unitId) +
        '&date=' + encodeURIComponent(dateInput.value), { credentials: 'same-origin' })
        .then(function (res) { return res.ok ? res.text() : ''; })
        .then(function (text) {
          if (text) {
            numberInput.value = text;
          }
        })
        .catch(function () {});
    }
    dateInput.addEventListener('change', refreshNumber);
    refreshNumber();
  }

  function init() {
    var root = document.getElementById('anwesenheit-personnel-root');
    if (!root) {
      document.querySelectorAll('.incident-tab').forEach(function (btn) {
        btn.addEventListener('click', function () {
          switchTab(Number(btn.dataset.tab));
        });
      });
      return;
    }

    readonly = root.dataset.readonly === 'true';
    var personnelData = document.getElementById('anwesenheit-personnel-data');
    try {
      personnel = JSON.parse((personnelData && personnelData.textContent) || '[]').map(function (row) {
        return {
          personId: row.personId || null,
          displayName: row.displayName || '',
          status: row.status || 'PRESENT'
        };
      });
    } catch (e) {
      personnel = [];
    }

    var personsData = document.getElementById('anwesenheit-unit-persons-data');
    if (personsData) {
      try {
        unitPersons = JSON.parse(personsData.textContent || '[]');
      } catch (e2) {
        unitPersons = [];
      }
    }

    document.querySelectorAll('.incident-tab').forEach(function (btn) {
      btn.addEventListener('click', function () {
        switchTab(Number(btn.dataset.tab));
      });
    });

    document.getElementById('btn-add-personnel')?.addEventListener('click', addPersonFromUnit);

    var form = document.getElementById('anwesenheitsliste-form');
    if (form) {
      form.addEventListener('submit', function () {
        syncHiddenJson();
      });
    }

    bindAutoNumber();
    renderPersonnel();
  }

  document.addEventListener('DOMContentLoaded', init);
})();
