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
        defectiveMangelByEquipmentId: {}
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
      var mangelMap = {};
      if (entry.defectiveMangelByEquipmentId && typeof entry.defectiveMangelByEquipmentId === 'object') {
        Object.keys(entry.defectiveMangelByEquipmentId).forEach(function (key) {
          var val = entry.defectiveMangelByEquipmentId[key];
          if (val) {
            mangelMap[key] = String(val);
          }
        });
      }
      var defectiveIds = (entry.defectiveEquipmentIds || []).map(Number).filter(function (id) {
        return !isNaN(id);
      });
      if (!Object.keys(mangelMap).length && entry.defectiveMangel && defectiveIds.length) {
        defectiveIds.forEach(function (id) {
          mangelMap[String(id)] = entry.defectiveMangel;
        });
      }
      vehicleState[String(entry.vehicleId)] = {
        selected: true,
        maschinistPersonId: entry.maschinistPersonId || null,
        einheitsfuehrerPersonId: entry.einheitsfuehrerPersonId || null,
        equipmentIds: (entry.equipmentIds || []).map(Number).filter(function (id) {
          return !isNaN(id);
        }),
        defectiveEquipmentIds: defectiveIds,
        defectiveMangelByEquipmentId: mangelMap
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
        if (!state.selected) {
          return;
        }
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

  function buildDefectEquipmentRow(vehicleId, item, state) {
    var eqKey = String(item.id);
    var checked = state.defectiveEquipmentIds.indexOf(Number(item.id)) >= 0;
    var mangel = state.defectiveMangelByEquipmentId[eqKey] || '';
    return '<div class="incident-gwm-defect-row' + (checked ? ' incident-gwm-defect-row--active' : '') +
      '" data-vehicle-id="' + vehicleId + '" data-equipment-id="' + item.id + '">' +
      '<label class="incident-gwm-defect-row__check">' +
      '<input type="checkbox" class="gwm-defect-equipment" data-vehicle-id="' + vehicleId +
      '" data-equipment-id="' + item.id + '"' + (checked ? ' checked' : '') +
      (readonly ? ' disabled' : '') + '/>' +
      '<span class="incident-gwm-defect-row__name">' + esc(item.name) + '</span></label>' +
      '<div class="incident-gwm-defect-row__mangel">' +
      '<label class="incident-gwm-defect-row__mangel-label">Mangel beschreiben</label>' +
      (readonly
        ? '<p class="form-readonly form-readonly--multiline">' + esc(mangel || '—') + '</p>'
        : '<textarea class="field gwm-defect-equipment-mangel" data-vehicle-id="' + vehicleId +
          '" data-equipment-id="' + item.id + '" rows="2"' +
          (checked ? '' : ' disabled') +
          ' placeholder="Beschreibung des Mangels …">' + esc(mangel) + '</textarea>') +
      '</div></div>';
  }

  function buildDefectCard(vehicle, usedEquipment) {
    var state = ensureVehicleState(vehicle.id);
    if (readonly) {
      usedEquipment = usedEquipment.filter(function (item) {
        return state.defectiveEquipmentIds.indexOf(Number(item.id)) >= 0;
      });
    }
    var defectRows = '';
    if (usedEquipment.length > 0) {
      usedEquipment.forEach(function (item) {
        defectRows += buildDefectEquipmentRow(vehicle.id, item, state);
      });
    } else {
      defectRows = '<p class="hint incident-gwm-defect-empty">' +
        (readonly ? 'Keine Defekte erfasst.' : 'Keine eingesetzten Geräte für dieses Fahrzeug.') +
        '</p>';
    }
    return '<article class="incident-deployed-vehicle-card incident-gwm-defect-card" data-vehicle-id="' + vehicle.id + '">' +
      '<header class="incident-deployed-vehicle-card__head">' +
      '<span class="incident-deployed-vehicle-card__title">' + esc(vehicle.name) + '</span>' +
      '</header>' +
      '<div class="incident-gwm-defect-card__body">' +
      '<div class="incident-gwm-defect-rows">' + defectRows + '</div>' +
      '</div></article>';
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
      if (readonly) {
        rows = rows.filter(function (row) {
          var state = vehicleState[String(row.vehicle.id)];
          return state && state.defectiveEquipmentIds.length > 0;
        });
      }
      if (!rows.length) {
        container.innerHTML = '';
        if (noVehicles) {
          noVehicles.hidden = false;
          noVehicles.textContent = readonly
            ? 'Keine Defekte erfasst.'
            : 'Markieren Sie zuerst im Reiter Fahrzeuge die eingesetzten Fahrzeuge.';
        }
        return;
      }
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
      var equipmentIdSet = {};
      equipmentIds.forEach(function (id) {
        equipmentIdSet[id] = true;
      });
      var defectiveIds = (state.defectiveEquipmentIds || []).filter(function (id) {
        return equipmentIdSet[id];
      });
      var mangelByEquipment = {};
      Object.keys(state.defectiveMangelByEquipmentId || {}).forEach(function (key) {
        var eqId = Number(key);
        if (!defectiveIds.some(function (id) { return Number(id) === eqId; })) {
          return;
        }
        var val = state.defectiveMangelByEquipmentId[key];
        if (val && String(val).trim()) {
          mangelByEquipment[key] = String(val).trim();
        }
      });
      rows.push({
        vehicleId: vid,
        maschinistPersonId: state.maschinistPersonId || null,
        einheitsfuehrerPersonId: state.einheitsfuehrerPersonId || null,
        equipmentIds: equipmentIds,
        defectiveEquipmentIds: defectiveIds,
        defectiveMangelByEquipmentId: mangelByEquipment
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
      state.defectiveMangelByEquipmentId = {};
    }
    syncHiddenJson();
    if (!checked && window.BerichteGeraete &&
        typeof window.BerichteGeraete.clearVehicleSelection === 'function') {
      window.BerichteGeraete.clearVehicleSelection(vehicleId);
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

  function setDefectRowState(row, checked, state, eqKey) {
    if (row) {
      row.classList.toggle('incident-gwm-defect-row--active', checked);
    }
    var mangelInput = row ? row.querySelector('.gwm-defect-equipment-mangel') : null;
    if (mangelInput) {
      mangelInput.disabled = !checked;
      if (!checked) {
        mangelInput.value = '';
        delete state.defectiveMangelByEquipmentId[eqKey];
      } else {
        mangelInput.focus();
      }
    }
  }

  function bindDefectEvents(container) {
    container.querySelectorAll('.gwm-defect-equipment').forEach(function (input) {
      input.addEventListener('change', function () {
        var vid = String(input.dataset.vehicleId);
        var eqId = Number(input.dataset.equipmentId);
        var eqKey = String(eqId);
        var state = ensureVehicleState(vid);
        var idx = state.defectiveEquipmentIds.indexOf(eqId);
        var row = input.closest('.incident-gwm-defect-row');
        if (input.checked && idx < 0) {
          state.defectiveEquipmentIds.push(eqId);
        } else if (!input.checked && idx >= 0) {
          state.defectiveEquipmentIds.splice(idx, 1);
        }
        setDefectRowState(row, input.checked, state, eqKey);
        syncHiddenJson();
      });
    });
    container.querySelectorAll('.incident-gwm-defect-row').forEach(function (row) {
      if (readonly) {
        return;
      }
      row.addEventListener('click', function (e) {
        if (e.target.closest('label, textarea, input, .incident-gwm-defect-row__mangel-label')) {
          return;
        }
        var checkbox = row.querySelector('.gwm-defect-equipment');
        if (!checkbox || e.target === checkbox) {
          return;
        }
        checkbox.checked = !checkbox.checked;
        checkbox.dispatchEvent(new Event('change', { bubbles: true }));
      });
    });
    container.querySelectorAll('.gwm-defect-equipment-mangel').forEach(function (input) {
      input.addEventListener('input', function () {
        var state = ensureVehicleState(input.dataset.vehicleId);
        state.defectiveMangelByEquipmentId[String(input.dataset.equipmentId)] = input.value;
        syncHiddenJson();
      });
      input.addEventListener('click', function (e) {
        e.stopPropagation();
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
    var vehicleStack = document.getElementById('gwm-vehicle-stack');
    if (vehicleStack && vehicleStack.dataset.gwmVehicleClickBound !== 'true' && !readonly) {
      vehicleStack.dataset.gwmVehicleClickBound = 'true';
      vehicleStack.addEventListener('click', function (e) {
        var toggleBtn = e.target.closest('.gwm-vehicle-toggle');
        if (toggleBtn) {
          e.stopPropagation();
          var vid = toggleBtn.dataset.vehicleId;
          var next = toggleBtn.getAttribute('aria-pressed') !== 'true';
          toggleVehicle(vid, next);
          return;
        }
        var card = e.target.closest('.incident-gwm-vehicle-card');
        if (!card || e.target.closest('select, button, input, textarea, option')) {
          return;
        }
        var vehicleId = card.dataset.vehicleId;
        var state = ensureVehicleState(vehicleId);
        toggleVehicle(vehicleId, !state.selected);
      });
    }
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
