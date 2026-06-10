(function () {
  'use strict';

  var equipmentCache = {};
  var selectionByVehicle = {};
  var equipmentNameById = {};

  function hiddenField() {
    return document.getElementById('deployedEquipmentJson');
  }

  function unitId() {
    var board = document.getElementById('incident-kraefte-board');
    return board ? board.dataset.unitId : '';
  }

  function isReadonly() {
    var wrap = document.getElementById('incident-deployed-equipment');
    return wrap && wrap.dataset.readonly === 'true';
  }

  function involvedVehicleIds() {
    var ids = [];
    document.querySelectorAll('#incident-vehicle-stack .incident-vehicle-card').forEach(function (card) {
      if (card.dataset.involvedInIncident === 'true') {
        ids.push(Number(card.dataset.vehicleId));
      }
    });
    return ids.filter(function (id) {
      return !isNaN(id) && id > 0;
    });
  }

  function loadSelectionFromHidden() {
    selectionByVehicle = {};
    var hidden = hiddenField();
    var wrap = document.getElementById('incident-deployed-equipment');
    var raw = (hidden && hidden.value) || (wrap && wrap.dataset.initial) || '[]';
    try {
      var data = JSON.parse(raw);
      data.forEach(function (row) {
        if (!row || row.vehicleId == null) {
          return;
        }
        selectionByVehicle[row.vehicleId] = new Set((row.equipmentIds || []).map(Number));
      });
    } catch (e) {
      selectionByVehicle = {};
    }
  }

  function syncHiddenJson() {
    var hidden = hiddenField();
    if (!hidden) {
      updateBerichtDeployedEquipment();
      return;
    }
    var result = [];
    Object.keys(selectionByVehicle).forEach(function (vehicleId) {
      var ids = Array.from(selectionByVehicle[vehicleId] || []).filter(function (id) {
        return !isNaN(id);
      });
      if (ids.length > 0) {
        result.push({ vehicleId: Number(vehicleId), equipmentIds: ids });
      }
    });
    hidden.value = JSON.stringify(result);
    updateBerichtDeployedEquipment();
  }

  function rememberEquipmentNames(vehicles) {
    vehicles.forEach(function (vehicle) {
      (vehicle.equipment || []).forEach(function (item) {
        equipmentNameById[item.id] = {
          name: item.name,
          vehicleId: vehicle.vehicleId,
          vehicleName: vehicle.vehicleName
        };
      });
    });
  }

  function findVehicleInCache(vehicleId) {
    var keys = Object.keys(equipmentCache);
    for (var i = 0; i < keys.length; i++) {
      var vehicles = equipmentCache[keys[i]];
      for (var j = 0; j < vehicles.length; j++) {
        if (vehicles[j].vehicleId === vehicleId) {
          return vehicles[j];
        }
      }
    }
    return null;
  }

  function updateBerichtDeployedEquipment() {
    var list = document.getElementById('bericht-deployed-equipment-list');
    var empty = document.getElementById('bericht-deployed-equipment-empty');
    if (!list) {
      return;
    }
    list.textContent = '';
    var entries = [];
    Object.keys(selectionByVehicle).forEach(function (vehicleId) {
      var selected = selectionByVehicle[vehicleId];
      if (!selected || selected.size === 0) {
        return;
      }
      var vehicleData = findVehicleInCache(Number(vehicleId));
      var vehicleName = vehicleData ? vehicleData.vehicleName : 'Fahrzeug';
      var names = [];
      if (vehicleData) {
        vehicleData.equipment.forEach(function (item) {
          if (selected.has(item.id)) {
            names.push(item.name);
          }
        });
      } else {
        selected.forEach(function (equipmentId) {
          var meta = equipmentNameById[equipmentId];
          if (meta) {
            names.push(meta.name);
            vehicleName = meta.vehicleName || vehicleName;
          }
        });
      }
      names.sort(function (a, b) {
        return a.localeCompare(b, 'de');
      });
      if (names.length > 0) {
        entries.push({ vehicleName: vehicleName, names: names });
      }
    });
    entries.sort(function (a, b) {
      return a.vehicleName.localeCompare(b.vehicleName, 'de');
    });
    entries.forEach(function (entry) {
      var block = document.createElement('div');
      block.className = 'incident-bericht-equipment__vehicle';
      var title = document.createElement('h5');
      title.className = 'incident-bericht-equipment__vehicle-name';
      title.textContent = entry.vehicleName;
      block.appendChild(title);
      var ul = document.createElement('ul');
      ul.className = 'incident-bericht-equipment__items';
      entry.names.forEach(function (name) {
        var li = document.createElement('li');
        li.textContent = name;
        ul.appendChild(li);
      });
      block.appendChild(ul);
      list.appendChild(block);
    });
    if (empty) {
      empty.hidden = entries.length > 0;
    }
  }

  function groupEquipment(items) {
    var groups = {};
    var uncategorized = [];
    items.forEach(function (item) {
      if (item.categoryName) {
        if (!groups[item.categoryName]) {
          groups[item.categoryName] = [];
        }
        groups[item.categoryName].push(item);
      } else {
        uncategorized.push(item);
      }
    });
    return { groups: groups, uncategorized: uncategorized };
  }

  function renderCategoryGroup(catName, items, vehicleId) {
    var section = document.createElement('section');
    section.className = 'incident-deployed-equipment-group';
    var title = document.createElement('h5');
    title.className = 'incident-deployed-equipment-group__title';
    title.textContent = catName;
    section.appendChild(title);
    var list = document.createElement('div');
    list.className = 'incident-deployed-equipment-group__list';
    items.forEach(function (item) {
      var label = document.createElement('label');
      label.className = 'incident-deployed-equipment-item';
      var input = document.createElement('input');
      input.type = 'checkbox';
      input.value = String(item.id);
      input.checked = !!(selectionByVehicle[vehicleId] && selectionByVehicle[vehicleId].has(item.id));
      input.disabled = isReadonly();
      input.addEventListener('change', function () {
        if (!selectionByVehicle[vehicleId]) {
          selectionByVehicle[vehicleId] = new Set();
        }
        if (input.checked) {
          selectionByVehicle[vehicleId].add(item.id);
        } else {
          selectionByVehicle[vehicleId].delete(item.id);
        }
        syncHiddenJson();
      });
      var name = document.createElement('span');
      name.textContent = item.name;
      label.appendChild(input);
      label.appendChild(name);
      list.appendChild(label);
    });
    section.appendChild(list);
    return section;
  }

  function selectAllForVehicle(vehicleId, equipment, selected) {
    if (!selectionByVehicle[vehicleId]) {
      selectionByVehicle[vehicleId] = new Set();
    }
    equipment.forEach(function (item) {
      if (selected) {
        selectionByVehicle[vehicleId].add(item.id);
      } else {
        selectionByVehicle[vehicleId].delete(item.id);
      }
    });
    renderCards(equipmentCache[currentCacheKey()] || []);
    syncHiddenJson();
  }

  function currentCacheKey() {
    return involvedVehicleIds().sort(function (a, b) {
      return a - b;
    }).join(',');
  }

  function renderCards(vehicles) {
    var wrap = document.getElementById('incident-deployed-equipment');
    var empty = document.getElementById('deployed-equipment-empty');
    var noVehicles = document.getElementById('deployed-equipment-no-vehicles');
    if (!wrap) {
      return;
    }
    wrap.textContent = '';
    if (noVehicles) {
      noVehicles.hidden = true;
    }
    var hasAnyEquipment = false;
    vehicles.forEach(function (vehicle) {
      if (!vehicle.equipment || vehicle.equipment.length === 0) {
        return;
      }
      hasAnyEquipment = true;
      if (!selectionByVehicle[vehicle.vehicleId]) {
        selectionByVehicle[vehicle.vehicleId] = new Set();
      }
      var card = document.createElement('article');
      card.className = 'incident-deployed-vehicle-card';
      card.dataset.vehicleId = String(vehicle.vehicleId);

      var head = document.createElement('header');
      head.className = 'incident-deployed-vehicle-card__head';
      var title = document.createElement('h4');
      title.className = 'incident-deployed-vehicle-card__title';
      title.textContent = vehicle.vehicleName;
      head.appendChild(title);

      if (!isReadonly()) {
        var actions = document.createElement('div');
        actions.className = 'incident-deployed-vehicle-card__actions';
        var allBtn = document.createElement('button');
        allBtn.type = 'button';
        allBtn.className = 'btn btn--outline btn--sm';
        allBtn.textContent = 'Alle';
        allBtn.addEventListener('click', function () {
          selectAllForVehicle(vehicle.vehicleId, vehicle.equipment, true);
        });
        var noneBtn = document.createElement('button');
        noneBtn.type = 'button';
        noneBtn.className = 'btn btn--outline btn--sm';
        noneBtn.textContent = 'Keine';
        noneBtn.addEventListener('click', function () {
          selectAllForVehicle(vehicle.vehicleId, vehicle.equipment, false);
        });
        actions.appendChild(allBtn);
        actions.appendChild(noneBtn);
        head.appendChild(actions);
      }
      card.appendChild(head);

      var body = document.createElement('div');
      body.className = 'incident-deployed-vehicle-card__body';
      var grouped = groupEquipment(vehicle.equipment);
      Object.keys(grouped.groups).sort(function (a, b) {
        return a.localeCompare(b, 'de');
      }).forEach(function (catName) {
        body.appendChild(renderCategoryGroup(catName, grouped.groups[catName], vehicle.vehicleId));
      });
      if (grouped.uncategorized.length > 0) {
        body.appendChild(renderCategoryGroup('Ohne Kategorie', grouped.uncategorized, vehicle.vehicleId));
      }
      card.appendChild(body);
      wrap.appendChild(card);
    });

    if (empty) {
      empty.hidden = hasAnyEquipment || vehicles.length === 0;
    }
    if (!hasAnyEquipment && vehicles.length > 0) {
      var hint = document.createElement('p');
      hint.className = 'hint';
      hint.textContent = 'Für die beteiligten Fahrzeuge sind noch keine Geräte in der Technik-Verwaltung hinterlegt.';
      wrap.appendChild(hint);
    }
    updateBerichtDeployedEquipment();
  }

  function render() {
    var wrap = document.getElementById('incident-deployed-equipment');
    var noVehicles = document.getElementById('deployed-equipment-no-vehicles');
    if (!wrap) {
      return;
    }
    var vehicleIds = involvedVehicleIds();
    if (vehicleIds.length === 0) {
      wrap.textContent = '';
      if (noVehicles) {
        noVehicles.hidden = false;
      }
      updateBerichtDeployedEquipment();
      return;
    }
    if (noVehicles) {
      noVehicles.hidden = true;
    }
    var cacheKey = currentCacheKey();
    if (equipmentCache[cacheKey]) {
      renderCards(equipmentCache[cacheKey]);
      return;
    }
    wrap.innerHTML = '<p class="hint">Geräte werden geladen …</p>';
    var url = '/berichte/einsatzberichte/vehicle-equipment?unit=' + encodeURIComponent(unitId())
      + vehicleIds.map(function (id) {
        return '&vehicleIds=' + encodeURIComponent(id);
      }).join('');
    fetch(url, { credentials: 'same-origin' })
      .then(function (res) {
        if (!res.ok) {
          throw new Error('load failed');
        }
        return res.json();
      })
      .then(function (data) {
        equipmentCache[cacheKey] = data;
        rememberEquipmentNames(data);
        renderCards(data);
      })
      .catch(function () {
        wrap.innerHTML = '<p class="hint">Geräte konnten nicht geladen werden.</p>';
      });
  }

  window.BerichteGeraete = {
    onTabShow: function () {
      loadSelectionFromHidden();
      render();
    },
    refresh: function () {
      equipmentCache = {};
      render();
    },
    syncSummary: function () {
      updateBerichtDeployedEquipment();
    }
  };

  document.addEventListener('DOMContentLoaded', function () {
    loadSelectionFromHidden();
    if (isReadonly()) {
      render();
    } else {
      updateBerichtDeployedEquipment();
    }
    var form = document.getElementById('einsatzbericht-form');
    if (form) {
      form.addEventListener('submit', syncHiddenJson);
    }
  });
})();
