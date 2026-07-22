(function () {
  'use strict';

  var equipmentCache = {};
  var selectionByVehicle = {};
  var customByVehicle = {};
  var collapsedByVehicle = {};
  var categoryModal = null;

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

  function ensureVehicleMaps(vehicleId) {
    if (!selectionByVehicle[vehicleId]) {
      selectionByVehicle[vehicleId] = new Set();
    }
    if (!customByVehicle[vehicleId]) {
      customByVehicle[vehicleId] = [];
    }
  }

  function loadSelectionFromHidden() {
    selectionByVehicle = {};
    customByVehicle = {};
    var hidden = hiddenField();
    var wrap = document.getElementById('incident-deployed-equipment');
    var raw = (hidden && hidden.value) || (wrap && wrap.dataset.initial) || '[]';
    try {
      var data = JSON.parse(raw);
      data.forEach(function (row) {
        if (!row || row.vehicleId == null) {
          return;
        }
        ensureVehicleMaps(row.vehicleId);
        selectionByVehicle[row.vehicleId] = new Set((row.equipmentIds || []).map(Number));
        customByVehicle[row.vehicleId] = (row.customEquipment || [])
          .filter(function (item) {
            return item && item.name;
          })
          .map(function (item) {
            return {
              name: String(item.name),
              categoryName: item.categoryName ? String(item.categoryName) : null
            };
          });
      });
    } catch (e) {
      selectionByVehicle = {};
      customByVehicle = {};
    }
  }

  function syncHiddenJson() {
    var hidden = hiddenField();
    if (!hidden) {
      return;
    }
    var vehicleIds = new Set();
    Object.keys(selectionByVehicle).forEach(function (id) {
      vehicleIds.add(Number(id));
    });
    Object.keys(customByVehicle).forEach(function (id) {
      vehicleIds.add(Number(id));
    });
    var result = [];
    vehicleIds.forEach(function (vehicleId) {
      if (isNaN(vehicleId)) {
        return;
      }
      var ids = Array.from(selectionByVehicle[vehicleId] || []).filter(function (id) {
        return !isNaN(id);
      });
      var custom = (customByVehicle[vehicleId] || []).filter(function (item) {
        return item && item.name;
      });
      if (ids.length > 0 || custom.length > 0) {
        result.push({
          vehicleId: vehicleId,
          equipmentIds: ids,
          customEquipment: custom
        });
      }
    });
    hidden.value = JSON.stringify(result);
  }

  function selectedCountForVehicle(vehicleId) {
    var set = selectionByVehicle[vehicleId];
    var custom = customByVehicle[vehicleId];
    return (set ? set.size : 0) + (custom ? custom.length : 0);
  }

  function isVehicleCollapsed(vehicleId) {
    if (!Object.prototype.hasOwnProperty.call(collapsedByVehicle, vehicleId)) {
      collapsedByVehicle[vehicleId] = true;
    }
    return collapsedByVehicle[vehicleId];
  }

  function setVehicleCollapsed(vehicleId, collapsed, card) {
    collapsedByVehicle[vehicleId] = collapsed;
    if (!card) {
      return;
    }
    card.classList.toggle('incident-deployed-vehicle-card--collapsed', collapsed);
    var toggle = card.querySelector('.incident-deployed-vehicle-card__toggle');
    if (toggle) {
      toggle.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
    }
  }

  function updateVehicleStatusBadge(card, vehicleId, totalEquipment) {
    var badge = card.querySelector('.incident-deployed-vehicle-card__status');
    if (!badge) {
      return;
    }
    var selected = selectedCountForVehicle(vehicleId);
    var total = Number(totalEquipment) || 0;
    var customCount = (customByVehicle[vehicleId] || []).length;
    var catalogTotal = total;
    if (selected > 0) {
      badge.textContent = selected + ' ausgewählt';
      badge.classList.add('incident-deployed-vehicle-card__status--selected');
      badge.classList.remove('incident-deployed-vehicle-card__status--empty');
    } else {
      badge.textContent = 'Keine Auswahl';
      badge.classList.remove('incident-deployed-vehicle-card__status--selected');
      badge.classList.add('incident-deployed-vehicle-card__status--empty');
    }
    if (customCount > 0 && catalogTotal > 0) {
      badge.textContent = selected + ' ausgewählt (' + customCount + ' manuell)';
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

  function categoryNamesForVehicle(vehicle) {
    var names = {};
    (vehicle.equipment || []).forEach(function (item) {
      if (item.categoryName) {
        names[item.categoryName] = true;
      }
    });
    (customByVehicle[vehicle.vehicleId] || []).forEach(function (item) {
      if (item.categoryName) {
        names[item.categoryName] = true;
      }
    });
    return Object.keys(names).sort(function (a, b) {
      return a.localeCompare(b, 'de');
    });
  }

  function ensureCategoryModal() {
    if (categoryModal) {
      return categoryModal;
    }
    var overlay = document.createElement('div');
    overlay.id = 'modal-deployed-equipment-category';
    overlay.className = 'modal-overlay modal-overlay--stack';
    overlay.hidden = true;
    overlay.innerHTML =
      '<div class="modal modal--sm" role="dialog" aria-labelledby="deployed-equipment-category-title">' +
      '  <header class="modal__header">' +
      '    <h3 id="deployed-equipment-category-title">Kategorie wählen</h3>' +
      '    <button type="button" class="modal__close" data-close-category-modal aria-label="Schließen">✕</button>' +
      '  </header>' +
      '  <div class="modal__body">' +
      '    <div class="form-group">' +
      '      <label for="deployed-equipment-category-select">Kategorie</label>' +
      '      <select id="deployed-equipment-category-select" class="field"></select>' +
      '    </div>' +
      '    <div class="form-group" id="deployed-equipment-category-custom-wrap" hidden>' +
      '      <label for="deployed-equipment-category-custom">Eigene Kategorie</label>' +
      '      <input type="text" id="deployed-equipment-category-custom" class="field" maxlength="120" placeholder="Kategoriename …"/>' +
      '    </div>' +
      '  </div>' +
      '  <footer class="modal__footer">' +
      '    <button type="button" class="btn btn--primary" id="deployed-equipment-category-apply">Hinzufügen</button>' +
      '    <button type="button" class="btn btn--outline" data-close-category-modal>Abbrechen</button>' +
      '  </footer>' +
      '</div>';
    document.body.appendChild(overlay);
    categoryModal = overlay;
    overlay.querySelector('[data-close-category-modal]').addEventListener('click', closeCategoryModal);
    overlay.querySelectorAll('[data-close-category-modal]').forEach(function (btn) {
      btn.addEventListener('click', closeCategoryModal);
    });
    overlay.addEventListener('click', function (e) {
      if (e.target === overlay) {
        closeCategoryModal();
      }
    });
    var select = overlay.querySelector('#deployed-equipment-category-select');
    select.addEventListener('change', function () {
      var customWrap = overlay.querySelector('#deployed-equipment-category-custom-wrap');
      if (customWrap) {
        customWrap.hidden = select.value !== '__custom__';
      }
    });
    return overlay;
  }

  function closeCategoryModal() {
    if (!categoryModal) {
      return;
    }
    categoryModal.hidden = true;
    categoryModal.classList.remove('active');
    if (!document.querySelector('.modal-overlay.active:not([hidden])')) {
      document.body.classList.remove('modal-open');
    }
    categoryModal._resolve = null;
  }

  function promptCategory(vehicle) {
    var modal = ensureCategoryModal();
    var select = modal.querySelector('#deployed-equipment-category-select');
    var customInput = modal.querySelector('#deployed-equipment-category-custom');
    var customWrap = modal.querySelector('#deployed-equipment-category-custom-wrap');
    select.textContent = '';
    var categories = categoryNamesForVehicle(vehicle);
    var optNone = document.createElement('option');
    optNone.value = '';
    optNone.textContent = 'Ohne Kategorie';
    select.appendChild(optNone);
    categories.forEach(function (name) {
      var opt = document.createElement('option');
      opt.value = name;
      opt.textContent = name;
      select.appendChild(opt);
    });
    var optCustom = document.createElement('option');
    optCustom.value = '__custom__';
    optCustom.textContent = 'Eigene Kategorie …';
    select.appendChild(optCustom);
    select.value = categories.length > 0 ? categories[0] : '';
    if (customInput) {
      customInput.value = '';
    }
    if (customWrap) {
      customWrap.hidden = true;
    }
    modal.hidden = false;
    modal.classList.add('active');
    document.body.classList.add('modal-open');
    return new Promise(function (resolve) {
      modal._resolve = resolve;
      var applyBtn = modal.querySelector('#deployed-equipment-category-apply');
      function onApply() {
        var categoryName = null;
        if (select.value === '__custom__') {
          var custom = customInput ? customInput.value.trim() : '';
          if (!custom) {
            window.alert('Bitte eine Kategorie eingeben.');
            return;
          }
          categoryName = custom;
        } else if (select.value) {
          categoryName = select.value;
        }
        closeCategoryModal();
        resolve(categoryName);
      }
      applyBtn.onclick = onApply;
    });
  }

  function renderCustomEquipmentItem(item, vehicleId, card) {
    var label = document.createElement('div');
    label.className = 'incident-deployed-equipment-item incident-deployed-equipment-item--custom';
    var name = document.createElement('span');
    name.className = 'incident-deployed-equipment-item__name';
    name.textContent = item.name + (item.categoryName ? ' (' + item.categoryName + ')' : '');
    label.appendChild(name);
    if (!isReadonly()) {
      var removeBtn = document.createElement('button');
      removeBtn.type = 'button';
      removeBtn.className = 'btn btn--outline btn--sm incident-deployed-equipment-item__remove';
      removeBtn.textContent = 'Entfernen';
      removeBtn.addEventListener('click', function () {
        customByVehicle[vehicleId] = (customByVehicle[vehicleId] || []).filter(function (entry) {
          return entry !== item;
        });
        syncHiddenJson();
        renderCards(equipmentCache[currentCacheKey()] || []);
      });
      label.appendChild(removeBtn);
    }
    return label;
  }

  function renderCategoryGroup(catName, items, vehicleId, card) {
    var section = document.createElement('section');
    section.className = 'incident-deployed-equipment-group';
    var title = document.createElement('h5');
    title.className = 'incident-deployed-equipment-group__title';
    title.textContent = catName;
    section.appendChild(title);
    var list = document.createElement('div');
    list.className = 'incident-deployed-equipment-group__list';
    items.sort(function (a, b) {
      return a.name.localeCompare(b.name, 'de');
    });
    items.forEach(function (item) {
      var label = document.createElement('label');
      label.className = 'incident-deployed-equipment-item';
      var input = document.createElement('input');
      input.type = 'checkbox';
      input.value = String(item.id);
      input.checked = !!(selectionByVehicle[vehicleId] && selectionByVehicle[vehicleId].has(item.id));
      input.disabled = isReadonly();
      input.addEventListener('change', function () {
        ensureVehicleMaps(vehicleId);
        if (input.checked) {
          selectionByVehicle[vehicleId].add(item.id);
        } else {
          selectionByVehicle[vehicleId].delete(item.id);
        }
        syncHiddenJson();
        updateVehicleStatusBadge(card, vehicleId, card.dataset.equipmentCount);
      });
      var name = document.createElement('span');
      name.className = 'incident-deployed-equipment-item__name';
      name.textContent = item.name;
      label.appendChild(input);
      label.appendChild(name);
      list.appendChild(label);
    });
    section.appendChild(list);
    return section;
  }

  function renderCustomAddRow(vehicle, card) {
    var row = document.createElement('div');
    row.className = 'incident-deployed-equipment-custom-add';
    var input = document.createElement('input');
    input.type = 'text';
    input.className = 'field incident-deployed-equipment-custom-add__input';
    input.placeholder = 'Gerät manuell eingeben …';
    input.maxLength = 255;
    input.setAttribute('aria-label', 'Gerät manuell eingeben');
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'btn btn--outline btn--sm';
    btn.textContent = 'Hinzufügen';
    btn.addEventListener('click', function () {
      var name = input.value.trim();
      if (!name) {
        window.alert('Bitte einen Gerätenamen eingeben.');
        return;
      }
      promptCategory(vehicle).then(function (categoryName) {
        ensureVehicleMaps(vehicle.vehicleId);
        customByVehicle[vehicle.vehicleId].push({
          name: name,
          categoryName: categoryName
        });
        input.value = '';
        syncHiddenJson();
        renderCards(equipmentCache[currentCacheKey()] || []);
      });
    });
    row.appendChild(input);
    row.appendChild(btn);
    return row;
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
    var hasAnyContent = false;
    vehicles.forEach(function (vehicle) {
      ensureVehicleMaps(vehicle.vehicleId);
      var hasCatalog = vehicle.equipment && vehicle.equipment.length > 0;
      var hasCustom = (customByVehicle[vehicle.vehicleId] || []).length > 0;
      if (!hasCatalog && !hasCustom && isReadonly()) {
        return;
      }
      hasAnyContent = true;
      var card = document.createElement('article');
      card.className = 'incident-deployed-vehicle-card';
      card.dataset.vehicleId = String(vehicle.vehicleId);
      card.dataset.equipmentCount = String((vehicle.equipment || []).length);
      setVehicleCollapsed(vehicle.vehicleId, isVehicleCollapsed(vehicle.vehicleId), card);

      var head = document.createElement('header');
      head.className = 'incident-deployed-vehicle-card__head';

      var toggle = document.createElement('button');
      toggle.type = 'button';
      toggle.className = 'incident-deployed-vehicle-card__toggle';
      toggle.setAttribute('aria-expanded', isVehicleCollapsed(vehicle.vehicleId) ? 'false' : 'true');

      var chevron = document.createElement('span');
      chevron.className = 'incident-deployed-vehicle-card__chevron';
      chevron.setAttribute('aria-hidden', 'true');
      chevron.textContent = '›';

      var titleWrap = document.createElement('span');
      titleWrap.className = 'incident-deployed-vehicle-card__title-wrap';

      var title = document.createElement('span');
      title.className = 'incident-deployed-vehicle-card__title';
      title.textContent = vehicle.vehicleName;

      var status = document.createElement('span');
      status.className = 'incident-deployed-vehicle-card__status';

      titleWrap.appendChild(title);
      titleWrap.appendChild(status);
      toggle.appendChild(chevron);
      toggle.appendChild(titleWrap);
      toggle.addEventListener('click', function () {
        setVehicleCollapsed(vehicle.vehicleId, !isVehicleCollapsed(vehicle.vehicleId), card);
      });
      head.appendChild(toggle);
      card.appendChild(head);
      updateVehicleStatusBadge(card, vehicle.vehicleId, card.dataset.equipmentCount);

      var body = document.createElement('div');
      body.className = 'incident-deployed-vehicle-card__body';

      if (hasCatalog) {
        var grouped = groupEquipment(vehicle.equipment);
        Object.keys(grouped.groups).sort(function (a, b) {
          return a.localeCompare(b, 'de');
        }).forEach(function (catName) {
          body.appendChild(renderCategoryGroup(catName, grouped.groups[catName], vehicle.vehicleId, card));
        });
        if (grouped.uncategorized.length > 0) {
          body.appendChild(renderCategoryGroup('Ohne Kategorie', grouped.uncategorized, vehicle.vehicleId, card));
        }
      }

      var customItems = customByVehicle[vehicle.vehicleId] || [];
      if (customItems.length > 0) {
        var customSection = document.createElement('section');
        customSection.className = 'incident-deployed-equipment-group incident-deployed-equipment-group--custom';
        var customTitle = document.createElement('h5');
        customTitle.className = 'incident-deployed-equipment-group__title';
        customTitle.textContent = 'Manuell hinzugefügt';
        customSection.appendChild(customTitle);
        var customList = document.createElement('div');
        customList.className = 'incident-deployed-equipment-group__list';
        customItems.forEach(function (item) {
          customList.appendChild(renderCustomEquipmentItem(item, vehicle.vehicleId, card));
        });
        customSection.appendChild(customList);
        body.appendChild(customSection);
      }

      if (!isReadonly()) {
        body.appendChild(renderCustomAddRow(vehicle, card));
      }

      if (!hasCatalog && !isReadonly()) {
        var hint = document.createElement('p');
        hint.className = 'hint text-sm';
        hint.textContent = 'Keine Stammdaten-Geräte hinterlegt — Sie können Geräte manuell hinzufügen.';
        body.insertBefore(hint, body.firstChild);
      }

      card.appendChild(body);
      wrap.appendChild(card);
    });

    if (empty) {
      empty.hidden = hasAnyContent || vehicles.length === 0;
    }
    if (!hasAnyContent && vehicles.length > 0 && isReadonly()) {
      var viewHint = document.createElement('p');
      viewHint.className = 'hint';
      viewHint.textContent = 'Keine eingesetzten Geräte erfasst.';
      wrap.appendChild(viewHint);
    }
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
    var apiBase = window.BerichteApiBase ? window.BerichteApiBase.path() : '/berichte/einsatzberichte';
    var url = apiBase + '/vehicle-equipment?unit=' + encodeURIComponent(unitId())
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
    clearVehicleSelection: function (vehicleId) {
      var key = Number(vehicleId);
      delete selectionByVehicle[key];
      delete selectionByVehicle[String(vehicleId)];
      delete customByVehicle[key];
      delete customByVehicle[String(vehicleId)];
      syncHiddenJson();
    },
    refresh: function () {
      loadSelectionFromHidden();
      equipmentCache = {};
      render();
    },
    initView: function () {
      loadSelectionFromHidden();
      render();
    },
    sync: function () {
      syncHiddenJson();
    }
  };

  document.addEventListener('DOMContentLoaded', function () {
    loadSelectionFromHidden();
    if (isReadonly()) {
      render();
    }
    var form = document.getElementById('einsatzbericht-form') || document.getElementById('geraetewart-form')
      || document.getElementById('anwesenheitsliste-form');
    if (form) {
      form.addEventListener('submit', syncHiddenJson);
    }
  });
})();
