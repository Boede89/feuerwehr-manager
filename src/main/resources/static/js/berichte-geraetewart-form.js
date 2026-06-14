(function () {
  'use strict';

  var vehicleState = {};
  var readonly = false;
  var unitPersons = [];

  function esc(text) {
    var div = document.createElement('div');
    div.textContent = text == null ? '' : String(text);
    return div.innerHTML;
  }

  function parseJsonScript(id, fallback) {
    var el = document.getElementById(id);
    if (!el) {
      return fallback;
    }
    try {
      return JSON.parse(el.textContent || JSON.stringify(fallback));
    } catch (e) {
      return fallback;
    }
  }

  function isReadonly() {
    var stack = document.getElementById('gwm-vehicle-stack');
    return stack && stack.dataset.readonly === 'true';
  }

  function switchTab(idx) {
    document.querySelectorAll('[data-berichte-form="geraetewart"] .incident-tab').forEach(function (btn) {
      btn.classList.toggle('tab-btn--active', Number(btn.dataset.tab) === idx);
    });
    document.querySelectorAll('[data-berichte-form="geraetewart"] .incident-tab-panel').forEach(function (panel) {
      panel.hidden = Number(panel.dataset.panel) !== idx;
    });
    if (idx === 2 && window.BerichteGeraete) {
      window.BerichteGeraete.onTabShow();
    }
  }

  function loadInitialState() {
    vehicleState = {};
    var initial = parseJsonScript('gwm-initial-vehicles-data', []);
    initial.forEach(function (entry) {
      if (!entry || entry.vehicleId == null) {
        return;
      }
      vehicleState[String(entry.vehicleId)] = {
        selected: true,
        maschinistPersonId: entry.maschinistPersonId || null,
        einheitsfuehrerPersonId: entry.einheitsfuehrerPersonId || null,
        equipmentIds: (entry.equipmentIds || []).map(Number).filter(function (id) {
          return !isNaN(id);
        })
      };
    });
  }

  function personOptions(selectedId) {
    var html = '<option value="">— keine Auswahl —</option>';
    unitPersons.forEach(function (person) {
      var selected = selectedId != null && Number(selectedId) === Number(person.id) ? ' selected' : '';
      html += '<option value="' + esc(person.id) + '"' + selected + '>' + esc(person.name) + '</option>';
    });
    return html;
  }

  function syncIncidentVehicleStack() {
    var stack = document.getElementById('incident-vehicle-stack');
    if (!stack) {
      return;
    }
    stack.innerHTML = '';
    Object.keys(vehicleState).forEach(function (vehicleId) {
      if (!vehicleState[vehicleId].selected) {
        return;
      }
      var card = document.createElement('article');
      card.className = 'incident-vehicle-card';
      card.dataset.vehicleId = vehicleId;
      card.dataset.involvedInIncident = 'true';
      stack.appendChild(card);
    });
    if (window.BerichteGeraete) {
      window.BerichteGeraete.refresh();
    }
  }

  function buildVehicleRows(vehicles) {
    return vehicles.map(function (vehicle) {
      var state = vehicleState[String(vehicle.id)] || {
        selected: false,
        maschinistPersonId: null,
        einheitsfuehrerPersonId: null,
        equipmentIds: []
      };
      if (readonly && !state.selected) {
        return '';
      }
      var checked = state.selected;
      var maschName = '—';
      var einhName = '—';
      if (readonly) {
        unitPersons.forEach(function (p) {
          if (state.maschinistPersonId != null && Number(p.id) === Number(state.maschinistPersonId)) {
            maschName = p.name;
          }
          if (state.einheitsfuehrerPersonId != null && Number(p.id) === Number(state.einheitsfuehrerPersonId)) {
            einhName = p.name;
          }
        });
      }
      return '<article class="incident-gwm-vehicle-row' + (checked ? ' incident-gwm-vehicle-row--active' : '') +
        '" data-vehicle-id="' + vehicle.id + '">' +
        '<header class="incident-gwm-vehicle-row__head">' +
        (readonly
          ? '<strong class="incident-gwm-vehicle-row__name">' + esc(vehicle.name) + '</strong>'
          : '<label class="incident-gwm-vehicle-row__check">' +
            '<input type="checkbox" class="gwm-vehicle-toggle" data-vehicle-id="' + vehicle.id + '"' +
            (checked ? ' checked' : '') + '/>' +
            '<span class="incident-gwm-vehicle-row__name">' + esc(vehicle.name) + '</span></label>') +
        '</header>' +
        '<div class="incident-gwm-vehicle-row__fields"' + (checked ? '' : ' hidden') + '>' +
        '<div class="form-group">' +
        '<label>Maschinist</label>' +
        (readonly
          ? '<p class="form-readonly">' + esc(maschName) + '</p>'
          : '<select class="field gwm-maschinist" data-vehicle-id="' + vehicle.id + '">' +
            personOptions(state.maschinistPersonId) + '</select>') +
        '</div>' +
        '<div class="form-group">' +
        '<label>Einheitsführer</label>' +
        (readonly
          ? '<p class="form-readonly">' + esc(einhName) + '</p>'
          : '<select class="field gwm-einheitsfuehrer" data-vehicle-id="' + vehicle.id + '">' +
            personOptions(state.einheitsfuehrerPersonId) + '</select>') +
        '</div>' +
        '</div></article>';
    }).join('');
  }

  function renderVehicles() {
    var stack = document.getElementById('gwm-vehicle-stack');
    var empty = document.getElementById('gwm-vehicle-empty');
    if (!stack) {
      return;
    }
    var vehicles = parseJsonScript('gwm-vehicles-data', []);
    if (readonly) {
      vehicles = vehicles.filter(function (vehicle) {
        return vehicleState[String(vehicle.id)] && vehicleState[String(vehicle.id)].selected;
      });
    }
    if (!vehicles.length) {
      stack.innerHTML = '';
      if (empty) {
        empty.hidden = false;
      }
      return;
    }
    if (empty) {
      empty.hidden = true;
    }
    stack.innerHTML = buildVehicleRows(vehicles);
    syncIncidentVehicleStack();
  }

  function mergeEquipmentFromHidden() {
    var hidden = document.getElementById('deployedEquipmentJson');
    if (!hidden || !hidden.value) {
      return;
    }
    try {
      var data = JSON.parse(hidden.value);
      data.forEach(function (row) {
        if (!row || row.vehicleId == null) {
          return;
        }
        var key = String(row.vehicleId);
        if (!vehicleState[key]) {
          vehicleState[key] = {
            selected: true,
            maschinistPersonId: null,
            einheitsfuehrerPersonId: null,
            equipmentIds: []
          };
        }
        vehicleState[key].equipmentIds = (row.equipmentIds || []).map(Number).filter(function (id) {
          return !isNaN(id);
        });
      });
    } catch (e) {
      // ignore
    }
  }

  function syncHiddenJson() {
    if (readonly) {
      return;
    }
    mergeEquipmentFromHidden();
    var vehiclesHidden = document.getElementById('vehiclesDataJson');
    var equipmentHidden = document.getElementById('deployedEquipmentJson');
    var rows = [];
    var equipmentRows = [];
    Object.keys(vehicleState).forEach(function (vehicleId) {
      var state = vehicleState[vehicleId];
      if (!state.selected) {
        return;
      }
      var vid = Number(vehicleId);
      var equipmentIds = (state.equipmentIds || []).slice();
      rows.push({
        vehicleId: vid,
        maschinistPersonId: state.maschinistPersonId || null,
        einheitsfuehrerPersonId: state.einheitsfuehrerPersonId || null,
        equipmentIds: equipmentIds,
        defectiveEquipmentIds: [],
        defectiveFreitext: null,
        defectiveMangel: null
      });
      equipmentRows.push({ vehicleId: vid, equipmentIds: equipmentIds });
    });
    if (vehiclesHidden) {
      vehiclesHidden.value = JSON.stringify(rows);
    }
    if (equipmentHidden) {
      equipmentHidden.value = JSON.stringify(equipmentRows);
    }
  }

  function toggleVehicle(vehicleId, checked) {
    if (!vehicleState[String(vehicleId)]) {
      vehicleState[String(vehicleId)] = {
        selected: checked,
        maschinistPersonId: null,
        einheitsfuehrerPersonId: null,
        equipmentIds: []
      };
    } else {
      vehicleState[String(vehicleId)].selected = checked;
    }
    var row = document.querySelector('.incident-gwm-vehicle-row[data-vehicle-id="' + vehicleId + '"]');
    if (row) {
      row.classList.toggle('incident-gwm-vehicle-row--active', checked);
      var fields = row.querySelector('.incident-gwm-vehicle-row__fields');
      if (fields) {
        fields.hidden = !checked;
      }
    }
    syncIncidentVehicleStack();
    syncHiddenJson();
  }

  function updateLeaderLabel() {
    var label = document.getElementById('leader-label');
    if (!label) {
      return;
    }
    var typInput = document.querySelector('[data-berichte-form="geraetewart"] input[name="typ"]:checked');
    var typ = typInput ? typInput.value : 'UEBUNG';
    label.textContent = typ === 'EINSATZ' ? 'Einsatzleiter' : 'Übungsleiter';
  }

  function bindLeaderPersonId(scope) {
    var hiddenPersonId = document.getElementById('leaderPersonId');
    if (!hiddenPersonId || readonly) {
      return;
    }
    scope.querySelectorAll('.combo-suggest__option[data-person-id]').forEach(function (option) {
      option.addEventListener('mousedown', function () {
        hiddenPersonId.value = option.getAttribute('data-person-id') || '';
      });
    });
    var leaderInput = document.getElementById('leaderName');
    if (leaderInput) {
      leaderInput.addEventListener('input', function () {
        hiddenPersonId.value = '';
      });
    }
  }

  function bindTabs(scope) {
    scope.querySelectorAll('.incident-tab').forEach(function (btn) {
      if (btn.dataset.gwmBound === 'true') {
        return;
      }
      btn.dataset.gwmBound = 'true';
      btn.addEventListener('click', function () {
        switchTab(Number(btn.dataset.tab));
      });
    });
  }

  function bindEvents(scope) {
    scope.querySelectorAll('.gwm-vehicle-toggle').forEach(function (checkbox) {
      checkbox.addEventListener('change', function () {
        toggleVehicle(checkbox.dataset.vehicleId, checkbox.checked);
      });
    });
    scope.querySelectorAll('.gwm-maschinist').forEach(function (select) {
      select.addEventListener('change', function () {
        var vid = String(select.dataset.vehicleId);
        if (!vehicleState[vid]) {
          vehicleState[vid] = { selected: true, maschinistPersonId: null, einheitsfuehrerPersonId: null, equipmentIds: [] };
        }
        vehicleState[vid].maschinistPersonId = select.value ? Number(select.value) : null;
        syncHiddenJson();
      });
    });
    scope.querySelectorAll('.gwm-einheitsfuehrer').forEach(function (select) {
      select.addEventListener('change', function () {
        var vid = String(select.dataset.vehicleId);
        if (!vehicleState[vid]) {
          vehicleState[vid] = { selected: true, maschinistPersonId: null, einheitsfuehrerPersonId: null, equipmentIds: [] };
        }
        vehicleState[vid].einheitsfuehrerPersonId = select.value ? Number(select.value) : null;
        syncHiddenJson();
      });
    });
    scope.querySelectorAll('input[name="typ"]').forEach(function (radio) {
      radio.addEventListener('change', updateLeaderLabel);
    });
    var form = document.getElementById('geraetewart-form');
    if (form && form.dataset.gwmSubmitBound !== 'true') {
      form.dataset.gwmSubmitBound = 'true';
      form.addEventListener('submit', function () {
        if (window.BerichteGeraete && typeof window.BerichteGeraete.sync === 'function') {
          window.BerichteGeraete.sync();
        }
        syncHiddenJson();
      });
    }
  }

  function loadUnitPersons() {
    unitPersons = [];
    document.querySelectorAll('.combo-suggest__option[data-person-id]').forEach(function (option) {
      unitPersons.push({
        id: Number(option.getAttribute('data-person-id')),
        name: option.getAttribute('data-value') || option.textContent.trim()
      });
    });
  }

  function init(scope) {
    scope = scope || document;
    readonly = isReadonly();
    loadUnitPersons();
    loadInitialState();
    renderVehicles();
    bindTabs(scope);
    bindEvents(scope);
    bindLeaderPersonId(scope);
    updateLeaderLabel();
    syncHiddenJson();
    if (readonly && window.BerichteGeraete) {
      window.BerichteGeraete.initView();
    }
  }

  window.BerichteGeraetewartForm = {
    init: init
  };

  document.addEventListener('DOMContentLoaded', function () {
    if (document.querySelector('[data-berichte-form="geraetewart"]')) {
      init();
    }
  });
})();
