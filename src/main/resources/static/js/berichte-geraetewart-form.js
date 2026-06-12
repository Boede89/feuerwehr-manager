(function () {
  'use strict';

  var equipmentCache = {};
  var selectedEquipment = {};
  var readonly = false;

  function esc(text) {
    var div = document.createElement('div');
    div.textContent = text == null ? '' : String(text);
    return div.innerHTML;
  }

  function parseJson(id, fallback) {
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
    var params = new URLSearchParams(window.location.search);
    return params.get('unit');
  }

  function isReadonly() {
    var stack = document.getElementById('gwm-vehicle-stack');
    return stack && stack.dataset.readonly === 'true';
  }

  function syncHiddenJson() {
    var hidden = document.getElementById('deployedEquipmentJson');
    if (!hidden || readonly) {
      return;
    }
    var assignments = [];
    Object.keys(selectedEquipment).forEach(function (vehicleId) {
      if (!selectedEquipment[vehicleId].selected) {
        return;
      }
      var ids = selectedEquipment[vehicleId].equipmentIds || [];
      assignments.push({
        vehicleId: Number(vehicleId),
        equipmentIds: ids.slice()
      });
    });
    hidden.value = JSON.stringify(assignments);
  }

  function loadInitialSelection() {
    var initial = parseJson('gwm-initial-equipment-data', []);
    selectedEquipment = {};
    initial.forEach(function (entry) {
      if (!entry || entry.vehicleId == null) {
        return;
      }
      selectedEquipment[String(entry.vehicleId)] = {
        selected: true,
        equipmentIds: (entry.equipmentIds || []).map(Number).filter(function (id) {
          return !isNaN(id);
        })
      };
    });
  }

  function renderEquipmentSection(vehicleId, container, items) {
    var state = selectedEquipment[String(vehicleId)] || { selected: true, equipmentIds: [] };
    if (!items.length) {
      container.innerHTML = '<span class="hint">Keine Geräte hinterlegt</span>';
      return;
    }
    var byCategory = {};
    items.forEach(function (item) {
      var key = item.categoryName || 'Sonstiges';
      if (!byCategory[key]) {
        byCategory[key] = [];
      }
      byCategory[key].push(item);
    });
    var html = '<div class="gwm-equipment-groups">';
    Object.keys(byCategory).sort().forEach(function (category) {
      html += '<div class="gwm-equipment-group"><div class="gwm-equipment-group__title">' + esc(category) + '</div><div class="gwm-equipment-checks">';
      byCategory[category].forEach(function (item) {
        var checked = state.equipmentIds.indexOf(Number(item.id)) >= 0;
        html += '<label class="checkbox-label gwm-equipment-check">' +
          '<input type="checkbox" data-vehicle-id="' + vehicleId + '" data-equipment-id="' + item.id + '"' +
          (checked ? ' checked' : '') + (readonly ? ' disabled' : '') + '/> ' +
          esc(item.name) + '</label>';
      });
      html += '</div></div>';
    });
    html += '</div>';
    container.innerHTML = html;
  }

  function fetchEquipment(vehicleId) {
    var uid = unitId();
    if (!uid) {
      return Promise.resolve([]);
    }
    if (equipmentCache[vehicleId]) {
      return Promise.resolve(equipmentCache[vehicleId]);
    }
    return fetch(apiBase() + '/vehicle-equipment?unit=' + encodeURIComponent(uid) +
      '&vehicleIds=' + encodeURIComponent(vehicleId), { credentials: 'same-origin' })
      .then(function (res) {
        if (!res.ok) {
          throw new Error('Geräte konnten nicht geladen werden');
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

  function toggleVehicleCard(vehicleId, checked) {
    if (!selectedEquipment[String(vehicleId)]) {
      selectedEquipment[String(vehicleId)] = { selected: checked, equipmentIds: [] };
    } else {
      selectedEquipment[String(vehicleId)].selected = checked;
    }
    var card = document.querySelector('.gwm-vehicle-card[data-vehicle-id="' + vehicleId + '"]');
    if (!card) {
      return;
    }
    var fields = card.querySelector('.gwm-vehicle-fields');
    if (fields) {
      fields.hidden = !checked;
    }
    if (!checked) {
      syncHiddenJson();
      return;
    }
    var eqWrap = card.querySelector('.gwm-equipment-wrap');
    if (!eqWrap) {
      syncHiddenJson();
      return;
    }
    eqWrap.innerHTML = '<p class="hint">Geräte werden geladen …</p>';
    fetchEquipment(vehicleId).then(function (items) {
      renderEquipmentSection(vehicleId, eqWrap, items);
      syncHiddenJson();
    });
  }

  function renderVehicleCards(vehicles) {
    var stack = document.getElementById('gwm-vehicle-stack');
    var empty = document.getElementById('gwm-vehicle-empty');
    if (!stack) {
      return;
    }
    if (readonly) {
      vehicles = vehicles.filter(function (vehicle) {
        return selectedEquipment[String(vehicle.id)] && selectedEquipment[String(vehicle.id)].selected;
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
    stack.innerHTML = vehicles.map(function (vehicle) {
      var state = selectedEquipment[String(vehicle.id)] || { selected: false, equipmentIds: [] };
      var checked = state.selected;
      return '<div class="card gwm-vehicle-card" data-vehicle-id="' + vehicle.id + '">' +
        '<div class="card__body">' +
        '<div class="gwm-vehicle-head">' +
        (readonly
          ? '<strong>' + esc(vehicle.name) + '</strong>'
          : '<label class="checkbox-label gwm-vehicle-check">' +
            '<input type="checkbox" class="gwm-vehicle-toggle" data-vehicle-id="' + vehicle.id + '"' +
            (checked ? ' checked' : '') + '/> <strong>' + esc(vehicle.name) + '</strong></label>') +
        '</div>' +
        '<div class="gwm-vehicle-fields"' + (checked ? '' : ' hidden') + '>' +
        '<div class="gwm-equipment-wrap"><p class="hint">Geräte werden geladen …</p></div>' +
        '</div></div></div>';
    }).join('');

    vehicles.forEach(function (vehicle) {
      var state = selectedEquipment[String(vehicle.id)];
      if (state && state.selected) {
        fetchEquipment(vehicle.id).then(function (items) {
          var card = document.querySelector('.gwm-vehicle-card[data-vehicle-id="' + vehicle.id + '"]');
          var eqWrap = card ? card.querySelector('.gwm-equipment-wrap') : null;
          if (eqWrap) {
            renderEquipmentSection(vehicle.id, eqWrap, items);
          }
        });
      }
    });
  }

  function updateLeaderLabel() {
    var label = document.getElementById('leader-label');
    if (!label) {
      return;
    }
    var typInput = document.querySelector('input[name="typ"]:checked');
    var typ = typInput ? typInput.value : 'UEBUNG';
    label.textContent = typ === 'EINSATZ' ? 'Einsatzleiter' : 'Übungsleiter';
  }

  function bindLeaderPersonId() {
    var hiddenPersonId = document.getElementById('leaderPersonId');
    if (!hiddenPersonId || readonly) {
      return;
    }
    document.querySelectorAll('.combo-suggest__option[data-person-id]').forEach(function (option) {
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

  function bindEvents() {
    document.querySelectorAll('.gwm-vehicle-toggle').forEach(function (checkbox) {
      checkbox.addEventListener('change', function () {
        toggleVehicleCard(checkbox.dataset.vehicleId, checkbox.checked);
      });
    });
    document.getElementById('gwm-vehicle-stack')?.addEventListener('change', function (event) {
      var target = event.target;
      if (!target.matches('input[data-equipment-id]')) {
        return;
      }
      var vehicleId = String(target.dataset.vehicleId);
      var equipmentId = Number(target.dataset.equipmentId);
      if (!selectedEquipment[vehicleId]) {
        selectedEquipment[vehicleId] = { selected: true, equipmentIds: [] };
      }
      var ids = selectedEquipment[vehicleId].equipmentIds;
      var idx = ids.indexOf(equipmentId);
      if (target.checked && idx < 0) {
        ids.push(equipmentId);
      } else if (!target.checked && idx >= 0) {
        ids.splice(idx, 1);
      }
      syncHiddenJson();
    });
    document.querySelectorAll('input[name="typ"]').forEach(function (radio) {
      radio.addEventListener('change', updateLeaderLabel);
    });
    var form = document.getElementById('geraetewart-form');
    if (form) {
      form.addEventListener('submit', syncHiddenJson);
    }
  }

  function init(scope) {
    scope = scope || document;
    readonly = isReadonly();
    loadInitialSelection();
    var vehicles = parseJson('gwm-vehicles-data', []);
    renderVehicleCards(vehicles);
    bindEvents();
    bindLeaderPersonId();
    updateLeaderLabel();
    syncHiddenJson();
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
