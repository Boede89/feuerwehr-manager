(function () {
  'use strict';

  var vehicleState = {};
  var equipmentCache = {};
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

  function apiBase() {
    return window.BerichteApiBase ? window.BerichteApiBase.path() : '/berichte/geraetewartmitteilungen';
  }

  function unitId() {
    var board = document.getElementById('incident-kraefte-board');
    if (board && board.dataset.unitId) {
      return board.dataset.unitId;
    }
    return new URLSearchParams(window.location.search).get('unit');
  }

  function isReadonly() {
    var stack = document.getElementById('gwm-vehicle-stack');
    return stack && stack.dataset.readonly === 'true';
  }

  function ensureVehicleState(vehicleId) {
    var key = String(vehicleId);
    if (!vehicleState[key]) {
      vehicleState[key] = {
        selected: false,
        maschinistPersonId: null,
        einheitsfuehrerPersonId: null,
        equipmentIds: [],
        defectiveEquipmentIds: [],
        defectiveFreitext: '',
        defectiveMangel: ''
      };
    }
    return vehicleState[key];
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
    if (idx === 3) {
      renderDefects();
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
        }),
        defectiveEquipmentIds: (entry.defectiveEquipmentIds || []).map(Number).filter(function (id) {
          return !isNaN(id);
        }),
        defectiveFreitext: entry.defectiveFreitext || '',
        defectiveMangel: entry.defectiveMangel || ''
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

  function buildVehicleCard(vehicle) {
    var state = ensureVehicleState(vehicle.id);
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
    return '<article class="incident-vehicle-card incident-gwm-vehicle-card' +
      (checked ? ' incident-vehicle-card--einsatz-beteiligt' : '') +
      '" data-vehicle-id="' + vehicle.id + '">' +
      '<header class="incident-vehicle-card__head">' +
      '<div class="incident-vehicle-card__title-wrap">' +
      '<h5 class="incident-vehicle-card__name">' + esc(vehicle.name) + '</h5>' +
      '</div>' +
      '<div class="incident-vehicle-card__meta">' +
      (readonly
        ? (checked ? '<span class="incident-vehicle-involved-badge">Eingesetzt</span>' : '')
        : '<button type="button" class="incident-vehicle-involved-toggle gwm-vehicle-toggle' +
          (checked ? ' incident-vehicle-involved-toggle--active' : '') +
          '" data-vehicle-id="' + vehicle.id + '" aria-pressed="' + (checked ? 'true' : 'false') +
          '" title="Fahrzeug als eingesetzt markieren">Eingesetzt</button>') +
      '</div></header>' +
      '<div class="incident-gwm-vehicle-card__body"' + (checked ? '' : ' hidden') + '>' +
      '<div class="incident-gwm-vehicle-card__fields">' +
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
      '</div></div></div></article>';
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
    stack.innerHTML = vehicles.map(buildVehicleCard).join('');
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
        var state = ensureVehicleState(row.vehicleId);
        state.selected = true;
        state.equipmentIds = (row.equipmentIds || []).map(Number).filter(function (id) {
          return !isNaN(id);
        });
      });
    } catch (e) {
      // ignore
    }
  }

  function fetchEquipmentMeta(vehicleId) {
    if (equipmentCache[vehicleId]) {
      return Promise.resolve(equipmentCache[vehicleId]);
    }
    var uid = unitId();
    if (!uid) {
      return Promise.resolve([]);
    }
    return fetch(apiBase() + '/vehicle-equipment?unit=' + encodeURIComponent(uid) +
      '&vehicleIds=' + encodeURIComponent(vehicleId), { credentials: 'same-origin' })
      .then(function (res) {
        if (!res.ok) {
          throw new Error('load failed');
        }
        return res.json();
      })
      .then(function (data) {
        var items = (data[0] && data[0].equipment) ? data[0].equipment : [];
        equipmentCache[vehicleId] = items;
        return items;
      })
      .catch(function () {
        return [];
      });
  }

  function getUsedEquipment(vehicleId, allEquipment) {
    var state = vehicleState[String(vehicleId)];
    if (!state || !state.equipmentIds.length) {
      return [];
    }
    var idSet = {};
    state.equipmentIds.forEach(function (id) {
      idSet[id] = true;
    });
    return allEquipment.filter(function (item) {
      return idSet[item.id];
    });
  }

  function buildDefectCard(vehicle, usedEquipment) {
    var state = ensureVehicleState(vehicle.id);
    var defectChecks = '';
    if (usedEquipment.length > 0) {
      usedEquipment.forEach(function (item) {
        var checked = state.defectiveEquipmentIds.indexOf(Number(item.id)) >= 0;
        defectChecks += '<label class="incident-deployed-equipment-item incident-gwm-defect-item">' +
          '<input type="checkbox" class="gwm-defect-equipment" data-vehicle-id="' + vehicle.id +
          '" data-equipment-id="' + item.id + '"' + (checked ? ' checked' : '') +
          (readonly ? ' disabled' : '') + '/>' +
          '<span class="incident-deployed-equipment-item__name">' + esc(item.name) + '</span></label>';
      });
    } else {
      defectChecks = '<p class="hint">Keine eingesetzten Geräte – nutzen Sie den Freitext unten.</p>';
    }
    return '<article class="incident-deployed-vehicle-card incident-gwm-defect-card" data-vehicle-id="' + vehicle.id + '">' +
      '<header class="incident-deployed-vehicle-card__head">' +
      '<span class="incident-deployed-vehicle-card__title">' + esc(vehicle.name) + '</span>' +
      '</header>' +
      '<div class="incident-gwm-defect-card__body">' +
      '<div class="form-group">' +
      '<label>Defekte Geräte (aus eingesetzten)</label>' +
      '<div class="incident-gwm-defect-checks">' + defectChecks + '</div>' +
      '</div>' +
      '<div class="form-group">' +
      '<label>Freitext (defektes Gerät)</label>' +
      (readonly
        ? '<p class="form-readonly">' + esc(state.defectiveFreitext || '—') + '</p>'
        : '<input type="text" class="field gwm-defect-freitext" data-vehicle-id="' + vehicle.id +
          '" maxlength="255" placeholder="z. B. Schlauch 123" value="' + esc(state.defectiveFreitext || '') + '"/>') +
      '</div>' +
      '<div class="form-group">' +
      '<label>Mangel beschreiben</label>' +
      (readonly
        ? '<p class="form-readonly form-readonly--multiline">' + esc(state.defectiveMangel || '—') + '</p>'
        : '<textarea class="field gwm-defect-mangel" data-vehicle-id="' + vehicle.id +
          '" rows="3" placeholder="Beschreibung des Mangels …">' + esc(state.defectiveMangel || '') + '</textarea>') +
      '</div></div></article>';
  }

  function renderDefects() {
    var container = document.getElementById('gwm-defects-container');
    var noVehicles = document.getElementById('gwm-defects-no-vehicles');
    if (!container) {
      return;
    }
    mergeEquipmentFromHidden();
    var vehicles = parseJsonScript('gwm-vehicles-data', []).filter(function (vehicle) {
      return vehicleState[String(vehicle.id)] && vehicleState[String(vehicle.id)].selected;
    });
    if (!vehicles.length) {
      container.innerHTML = '';
      if (noVehicles) {
        noVehicles.hidden = false;
      }
      return;
    }
    if (noVehicles) {
      noVehicles.hidden = true;
    }
    container.innerHTML = '<p class="hint">Defekte werden geladen …</p>';
    Promise.all(vehicles.map(function (vehicle) {
      return fetchEquipmentMeta(vehicle.id).then(function (equipment) {
        return { vehicle: vehicle, usedEquipment: getUsedEquipment(vehicle.id, equipment) };
      });
    })).then(function (rows) {
      container.innerHTML = rows.map(function (row) {
        return buildDefectCard(row.vehicle, row.usedEquipment);
      }).join('');
      bindDefectEvents(container);
    });
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
        defectiveEquipmentIds: (state.defectiveEquipmentIds || []).slice(),
        defectiveFreitext: state.defectiveFreitext ? state.defectiveFreitext.trim() : null,
        defectiveMangel: state.defectiveMangel ? state.defectiveMangel.trim() : null
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
    var state = ensureVehicleState(vehicleId);
    state.selected = checked;
    if (!checked) {
      state.maschinistPersonId = null;
      state.einheitsfuehrerPersonId = null;
      state.equipmentIds = [];
      state.defectiveEquipmentIds = [];
      state.defectiveFreitext = '';
      state.defectiveMangel = '';
    }
    var card = document.querySelector('.incident-gwm-vehicle-card[data-vehicle-id="' + vehicleId + '"]');
    if (card) {
      card.classList.toggle('incident-vehicle-card--einsatz-beteiligt', checked);
      var body = card.querySelector('.incident-gwm-vehicle-card__body');
      if (body) {
        body.hidden = !checked;
      }
      var toggle = card.querySelector('.gwm-vehicle-toggle');
      if (toggle) {
        toggle.classList.toggle('incident-vehicle-involved-toggle--active', checked);
        toggle.setAttribute('aria-pressed', checked ? 'true' : 'false');
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

  function bindDefectEvents(container) {
    container.querySelectorAll('.gwm-defect-equipment').forEach(function (input) {
      input.addEventListener('change', function () {
        var vid = String(input.dataset.vehicleId);
        var eqId = Number(input.dataset.equipmentId);
        var state = ensureVehicleState(vid);
        var idx = state.defectiveEquipmentIds.indexOf(eqId);
        if (input.checked && idx < 0) {
          state.defectiveEquipmentIds.push(eqId);
        } else if (!input.checked && idx >= 0) {
          state.defectiveEquipmentIds.splice(idx, 1);
        }
        syncHiddenJson();
      });
    });
    container.querySelectorAll('.gwm-defect-freitext').forEach(function (input) {
      input.addEventListener('input', function () {
        ensureVehicleState(input.dataset.vehicleId).defectiveFreitext = input.value;
        syncHiddenJson();
      });
    });
    container.querySelectorAll('.gwm-defect-mangel').forEach(function (input) {
      input.addEventListener('input', function () {
        ensureVehicleState(input.dataset.vehicleId).defectiveMangel = input.value;
        syncHiddenJson();
      });
    });
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
    scope.querySelectorAll('.gwm-vehicle-toggle').forEach(function (button) {
      button.addEventListener('click', function () {
        var vid = button.dataset.vehicleId;
        var next = button.getAttribute('aria-pressed') !== 'true';
        toggleVehicle(vid, next);
      });
    });
    scope.querySelectorAll('.gwm-maschinist').forEach(function (select) {
      select.addEventListener('change', function () {
        var state = ensureVehicleState(select.dataset.vehicleId);
        state.selected = true;
        state.maschinistPersonId = select.value ? Number(select.value) : null;
        syncHiddenJson();
      });
    });
    scope.querySelectorAll('.gwm-einheitsfuehrer').forEach(function (select) {
      select.addEventListener('change', function () {
        var state = ensureVehicleState(select.dataset.vehicleId);
        state.selected = true;
        state.einheitsfuehrerPersonId = select.value ? Number(select.value) : null;
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
        mergeEquipmentFromHidden();
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
    mergeEquipmentFromHidden();
    syncHiddenJson();
    if (readonly) {
      if (window.BerichteGeraete) {
        window.BerichteGeraete.initView();
      }
      renderDefects();
    }
  }

  window.BerichteGeraetewartForm = {
    init: init,
    refreshDefects: renderDefects
  };

  document.addEventListener('DOMContentLoaded', function () {
    if (document.querySelector('[data-berichte-form="geraetewart"]')) {
      init();
    }
  });
})();
