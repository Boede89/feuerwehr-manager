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
    var showAll = shouldShowAllVehicles();
    var ids = [];
    document.querySelectorAll('#incident-vehicle-stack .incident-vehicle-card').forEach(function (card) {
      if (showAll || card.dataset.involvedInIncident === 'true') {
        ids.push(Number(card.dataset.vehicleId));
      }
    });
    return ids.filter(function (id) {
      return !isNaN(id) && id > 0;
    });
  }

  function shouldShowAllVehicles() {
    var wrap = document.getElementById('incident-deployed-equipment');
    if (wrap && wrap.dataset.showAllVehicles === 'true') {
      return true;
    }
    return !!document.querySelector('[data-berichte-form="anwesenheit"]');
  }

  function isVehicleInvolvedInEinsatz(vehicleId) {
    var id = String(vehicleId);
    var card = document.querySelector(
      '#incident-vehicle-stack .incident-vehicle-card[data-vehicle-id="' + id + '"]'
    );
    return !!(card && card.dataset.involvedInIncident === 'true');
  }

  function syncInvolvementHighlight() {
    document.querySelectorAll('#incident-deployed-equipment .incident-deployed-vehicle-card').forEach(function (card) {
      var involved = isVehicleInvolvedInEinsatz(card.dataset.vehicleId);
      card.classList.toggle('incident-deployed-vehicle-card--einsatz-beteiligt', involved);
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

  /**
   * Übernimmt noch nicht per „Hinzufügen“ bestätigte Entwürfe aus den Textareas
   * als berichtsbezogene Custom-Geräte (ohne Kategorie, ohne Stammdaten am Fahrzeug).
   */
  function flushPendingCustomInputs() {
    var wrap = document.getElementById('incident-deployed-equipment');
    if (!wrap || isReadonly()) {
      return false;
    }
    var changed = false;
    wrap.querySelectorAll('.incident-deployed-equipment-custom-add__input').forEach(function (input) {
      var vehicleId = Number(input.dataset.vehicleId);
      if (isNaN(vehicleId) || vehicleId <= 0) {
        var card = input.closest('.incident-deployed-vehicle-card');
        vehicleId = card ? Number(card.dataset.vehicleId) : NaN;
      }
      if (isNaN(vehicleId) || vehicleId <= 0) {
        return;
      }
      var names = parseCustomEquipmentNames(input.value);
      if (names.length === 0) {
        return;
      }
      ensureVehicleMaps(vehicleId);
      var existing = customByVehicle[vehicleId];
      var existingKeys = {};
      existing.forEach(function (item) {
        if (item && item.name) {
          existingKeys[String(item.name).toLocaleLowerCase('de')] = true;
        }
      });
      names.forEach(function (name) {
        var key = name.toLocaleLowerCase('de');
        if (existingKeys[key]) {
          return;
        }
        existingKeys[key] = true;
        existing.push({
          name: name,
          categoryName: null
        });
        changed = true;
      });
      input.value = '';
    });
    return changed;
  }

  function syncHiddenJson(options) {
    if (options && options.flushPending) {
      flushPendingCustomInputs();
    }
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

  function fillCategorySelect(select, categories, selectedValue) {
    select.textContent = '';
    var optNone = document.createElement('option');
    optNone.value = '';
    optNone.textContent = 'Ohne Kategorie';
    select.appendChild(optNone);
    (categories || []).forEach(function (name) {
      var opt = document.createElement('option');
      opt.value = name;
      opt.textContent = name;
      select.appendChild(opt);
    });
    var optCustom = document.createElement('option');
    optCustom.value = '__custom__';
    optCustom.textContent = 'Neue Kategorie …';
    select.appendChild(optCustom);
    if (selectedValue === '__custom__' || (selectedValue != null && selectedValue !== '')) {
      select.value = selectedValue;
    } else if (categories && categories.length > 0) {
      select.value = categories[0];
    } else {
      select.value = '';
    }
  }

  function syncCategoryCustomVisibility(select, customWrap) {
    if (!select || !customWrap) {
      return;
    }
    customWrap.hidden = select.value !== '__custom__';
  }

  function resolveCategoryFromControls(select, customInput) {
    if (!select) {
      return { ok: true, categoryName: null };
    }
    if (select.value === '__custom__') {
      var custom = customInput ? customInput.value.trim() : '';
      if (!custom) {
        return { ok: false, categoryName: null };
      }
      return { ok: true, categoryName: custom.length > 120 ? custom.slice(0, 120) : custom };
    }
    if (select.value) {
      return { ok: true, categoryName: select.value };
    }
    return { ok: true, categoryName: null };
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
      '<div class="modal modal--md" role="dialog" aria-labelledby="deployed-equipment-category-title">' +
      '  <header class="modal__header">' +
      '    <h3 id="deployed-equipment-category-title">Kategorien zuordnen</h3>' +
      '    <button type="button" class="modal__close" data-close-category-modal aria-label="Schließen">✕</button>' +
      '  </header>' +
      '  <div class="modal__body">' +
      '    <p class="hint text-sm" id="deployed-equipment-category-hint">' +
      '      Ordnen Sie jedem Gerät eine Kategorie zu.' +
      '    </p>' +
      '    <div class="form-group" id="deployed-equipment-category-bulk-wrap">' +
      '      <label for="deployed-equipment-category-bulk">Für alle setzen</label>' +
      '      <select id="deployed-equipment-category-bulk" class="field"></select>' +
      '      <div class="form-group" id="deployed-equipment-category-bulk-custom-wrap" hidden>' +
      '        <label for="deployed-equipment-category-bulk-custom">Neue Kategorie</label>' +
      '        <input type="text" id="deployed-equipment-category-bulk-custom" class="field" maxlength="120"' +
      '               placeholder="Kategoriename …"/>' +
      '      </div>' +
      '    </div>' +
      '    <div id="deployed-equipment-category-rows" class="deployed-equipment-category-rows"></div>' +
      '  </div>' +
      '  <footer class="modal__footer">' +
      '    <button type="button" class="btn btn--primary" id="deployed-equipment-category-apply">Hinzufügen</button>' +
      '    <button type="button" class="btn btn--outline" data-close-category-modal>Abbrechen</button>' +
      '  </footer>' +
      '</div>';
    document.body.appendChild(overlay);
    categoryModal = overlay;
    overlay.querySelectorAll('[data-close-category-modal]').forEach(function (btn) {
      btn.addEventListener('click', closeCategoryModal);
    });
    overlay.addEventListener('click', function (e) {
      if (e.target === overlay) {
        closeCategoryModal();
      }
    });
    var bulkSelect = overlay.querySelector('#deployed-equipment-category-bulk');
    var bulkCustomWrap = overlay.querySelector('#deployed-equipment-category-bulk-custom-wrap');
    var bulkCustomInput = overlay.querySelector('#deployed-equipment-category-bulk-custom');
    bulkSelect.addEventListener('change', function () {
      syncCategoryCustomVisibility(bulkSelect, bulkCustomWrap);
      applyBulkCategoryToRows();
    });
    if (bulkCustomInput) {
      bulkCustomInput.addEventListener('input', function () {
        if (bulkSelect.value === '__custom__') {
          applyBulkCategoryToRows();
        }
      });
    }
    return overlay;
  }

  function applyBulkCategoryToRows() {
    if (!categoryModal) {
      return;
    }
    var bulkSelect = categoryModal.querySelector('#deployed-equipment-category-bulk');
    var bulkCustomInput = categoryModal.querySelector('#deployed-equipment-category-bulk-custom');
    var rows = categoryModal.querySelectorAll('.deployed-equipment-category-row');
    rows.forEach(function (row) {
      var select = row.querySelector('.deployed-equipment-category-row__select');
      var customWrap = row.querySelector('.deployed-equipment-category-row__custom-wrap');
      var customInput = row.querySelector('.deployed-equipment-category-row__custom');
      if (!select) {
        return;
      }
      select.value = bulkSelect.value;
      syncCategoryCustomVisibility(select, customWrap);
      if (bulkSelect.value === '__custom__' && customInput && bulkCustomInput) {
        customInput.value = bulkCustomInput.value;
      }
    });
  }

  function closeCategoryModal() {
    if (!categoryModal) {
      return;
    }
    var resolve = categoryModal._resolve;
    categoryModal.hidden = true;
    categoryModal.classList.remove('active');
    if (!document.querySelector('.modal-overlay.active:not([hidden])')) {
      document.body.classList.remove('modal-open');
    }
    categoryModal._resolve = null;
    if (typeof resolve === 'function') {
      resolve(undefined);
    }
  }

  function promptCategoriesForNames(vehicle, names) {
    var modal = ensureCategoryModal();
    var categories = categoryNamesForVehicle(vehicle);
    var title = modal.querySelector('#deployed-equipment-category-title');
    var hint = modal.querySelector('#deployed-equipment-category-hint');
    var bulkWrap = modal.querySelector('#deployed-equipment-category-bulk-wrap');
    var bulkSelect = modal.querySelector('#deployed-equipment-category-bulk');
    var bulkCustomWrap = modal.querySelector('#deployed-equipment-category-bulk-custom-wrap');
    var bulkCustomInput = modal.querySelector('#deployed-equipment-category-bulk-custom');
    var rowsWrap = modal.querySelector('#deployed-equipment-category-rows');
    var multi = names.length > 1;

    if (title) {
      title.textContent = multi ? 'Kategorien zuordnen' : 'Kategorie wählen';
    }
    if (hint) {
      hint.textContent = multi
        ? 'Jedem Gerät eine eigene Kategorie zuweisen — oder oben eine für alle setzen.'
        : 'Kategorie für das manuell hinzugefügte Gerät wählen.';
    }
    if (bulkWrap) {
      bulkWrap.hidden = !multi;
    }
    fillCategorySelect(bulkSelect, categories, categories.length > 0 ? categories[0] : '');
    if (bulkCustomInput) {
      bulkCustomInput.value = '';
    }
    syncCategoryCustomVisibility(bulkSelect, bulkCustomWrap);

    rowsWrap.textContent = '';
    names.forEach(function (name, index) {
      var row = document.createElement('div');
      row.className = 'deployed-equipment-category-row';
      row.dataset.name = name;

      var nameEl = document.createElement('div');
      nameEl.className = 'deployed-equipment-category-row__name';
      nameEl.textContent = name;

      var selectId = 'deployed-equipment-category-row-' + index;
      var selectLabel = document.createElement('label');
      selectLabel.className = 'deployed-equipment-category-row__label';
      selectLabel.setAttribute('for', selectId);
      selectLabel.textContent = 'Kategorie';

      var select = document.createElement('select');
      select.id = selectId;
      select.className = 'field deployed-equipment-category-row__select';
      fillCategorySelect(select, categories, bulkSelect.value);

      var customWrap = document.createElement('div');
      customWrap.className = 'form-group deployed-equipment-category-row__custom-wrap';
      customWrap.hidden = true;
      var customLabel = document.createElement('label');
      customLabel.setAttribute('for', selectId + '-custom');
      customLabel.textContent = 'Neue Kategorie';
      var customInput = document.createElement('input');
      customInput.type = 'text';
      customInput.id = selectId + '-custom';
      customInput.className = 'field deployed-equipment-category-row__custom';
      customInput.maxLength = 120;
      customInput.placeholder = 'Kategoriename …';
      customWrap.appendChild(customLabel);
      customWrap.appendChild(customInput);

      select.addEventListener('change', function () {
        syncCategoryCustomVisibility(select, customWrap);
      });

      row.appendChild(nameEl);
      row.appendChild(selectLabel);
      row.appendChild(select);
      row.appendChild(customWrap);
      rowsWrap.appendChild(row);
    });

    modal.hidden = false;
    modal.classList.add('active');
    document.body.classList.add('modal-open');

    return new Promise(function (resolve) {
      modal._resolve = resolve;
      var applyBtn = modal.querySelector('#deployed-equipment-category-apply');
      applyBtn.onclick = function () {
        var assignments = [];
        var rows = modal.querySelectorAll('.deployed-equipment-category-row');
        for (var i = 0; i < rows.length; i++) {
          var row = rows[i];
          var select = row.querySelector('.deployed-equipment-category-row__select');
          var customInput = row.querySelector('.deployed-equipment-category-row__custom');
          var resolved = resolveCategoryFromControls(select, customInput);
          if (!resolved.ok) {
            window.alert('Bitte für „' + (row.dataset.name || 'Gerät') + '“ eine Kategorie eingeben.');
            return;
          }
          assignments.push({
            name: row.dataset.name,
            categoryName: resolved.categoryName
          });
        }
        var done = modal._resolve;
        modal._resolve = null;
        modal.hidden = true;
        modal.classList.remove('active');
        if (!document.querySelector('.modal-overlay.active:not([hidden])')) {
          document.body.classList.remove('modal-open');
        }
        if (typeof done === 'function') {
          done(assignments);
        }
      };
    });
  }

  function renderCustomEquipmentItem(item, vehicleId, card) {
    var label = document.createElement('div');
    label.className = 'incident-deployed-equipment-item incident-deployed-equipment-item--custom';
    var name = document.createElement('span');
    name.className = 'incident-deployed-equipment-item__name';
    name.textContent = item.name;
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

  function renderCustomEquipmentGroups(customItems, vehicleId, card) {
    var fragment = document.createDocumentFragment();
    var byCategory = {};
    var uncategorized = [];
    customItems.forEach(function (item) {
      if (item.categoryName) {
        if (!byCategory[item.categoryName]) {
          byCategory[item.categoryName] = [];
        }
        byCategory[item.categoryName].push(item);
      } else {
        uncategorized.push(item);
      }
    });
    var catNames = Object.keys(byCategory).sort(function (a, b) {
      return a.localeCompare(b, 'de');
    });
    function appendGroup(titleText, items) {
      var section = document.createElement('section');
      section.className = 'incident-deployed-equipment-group incident-deployed-equipment-group--custom';
      var title = document.createElement('h5');
      title.className = 'incident-deployed-equipment-group__title';
      title.textContent = titleText;
      section.appendChild(title);
      var list = document.createElement('div');
      list.className = 'incident-deployed-equipment-group__list';
      items.forEach(function (item) {
        list.appendChild(renderCustomEquipmentItem(item, vehicleId, card));
      });
      section.appendChild(list);
      fragment.appendChild(section);
    }
    catNames.forEach(function (catName) {
      appendGroup(catName + ' (manuell)', byCategory[catName]);
    });
    if (uncategorized.length > 0) {
      appendGroup('Manuell ohne Kategorie', uncategorized);
    }
    return fragment;
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

  function parseCustomEquipmentNames(raw) {
    var seen = {};
    var names = [];
    String(raw || '').split(/[\n,;]+/).forEach(function (part) {
      var name = part.trim();
      if (!name) {
        return;
      }
      var key = name.toLocaleLowerCase('de');
      if (seen[key]) {
        return;
      }
      seen[key] = true;
      names.push(name.length > 255 ? name.slice(0, 255) : name);
    });
    return names;
  }

  function renderCustomAddRow(vehicle, card) {
    var row = document.createElement('div');
    row.className = 'incident-deployed-equipment-custom-add';
    var input = document.createElement('textarea');
    input.className = 'field incident-deployed-equipment-custom-add__input';
    input.rows = 2;
    input.dataset.vehicleId = String(vehicle.vehicleId);
    input.placeholder = 'Geräte manuell eingeben (eines pro Zeile oder mit Komma) …';
    input.setAttribute('aria-label', 'Geräte manuell eingeben');
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'btn btn--outline btn--sm';
    btn.textContent = 'Hinzufügen';
    btn.title = 'Mit Kategorie zum Bericht hinzufügen (nicht in die Fahrzeug-Stammdaten)';
    btn.addEventListener('click', function () {
      var names = parseCustomEquipmentNames(input.value);
      if (names.length === 0) {
        window.alert('Bitte mindestens einen Gerätenamen eingeben.');
        return;
      }
      promptCategoriesForNames(vehicle, names).then(function (assignments) {
        if (!assignments || !assignments.length) {
          return;
        }
        ensureVehicleMaps(vehicle.vehicleId);
        var existing = customByVehicle[vehicle.vehicleId];
        var existingKeys = {};
        existing.forEach(function (item) {
          if (item && item.name) {
            existingKeys[String(item.name).toLocaleLowerCase('de')] = true;
          }
        });
        assignments.forEach(function (item) {
          var key = String(item.name).toLocaleLowerCase('de');
          if (existingKeys[key]) {
            return;
          }
          existingKeys[key] = true;
          existing.push({
            name: item.name,
            categoryName: item.categoryName
          });
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
    // Offene Eingaben zuerst in den Bericht übernehmen, bevor die DOM-Zeilen neu gebaut werden.
    flushPendingCustomInputs();
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
      if (isVehicleInvolvedInEinsatz(vehicle.vehicleId)) {
        card.classList.add('incident-deployed-vehicle-card--einsatz-beteiligt');
      }
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
        body.appendChild(renderCustomEquipmentGroups(customItems, vehicle.vehicleId, card));
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
    syncHiddenJson();
  }

  function render() {
    var wrap = document.getElementById('incident-deployed-equipment');
    var noVehicles = document.getElementById('deployed-equipment-no-vehicles');
    if (!wrap) {
      return;
    }
    var vehicleIds = involvedVehicleIds();
    if (vehicleIds.length === 0) {
      flushPendingCustomInputs();
      syncHiddenJson();
      wrap.textContent = '';
      if (noVehicles) {
        noVehicles.hidden = shouldShowAllVehicles();
        if (shouldShowAllVehicles()) {
          wrap.innerHTML = '<p class="hint">Keine Fahrzeuge in dieser Einheit hinterlegt.</p>';
        } else {
          noVehicles.hidden = false;
        }
      }
      return;
    }
    if (noVehicles) {
      noVehicles.hidden = true;
    }
    var anwesenheitHint = document.getElementById('deployed-equipment-anwesenheit-hint');
    if (anwesenheitHint) {
      anwesenheitHint.hidden = !shouldShowAllVehicles();
    }
    var cacheKey = currentCacheKey();
    if (equipmentCache[cacheKey]) {
      renderCards(equipmentCache[cacheKey]);
      return;
    }
    flushPendingCustomInputs();
    syncHiddenJson();
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
      flushPendingCustomInputs();
      syncHiddenJson();
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
      flushPendingCustomInputs();
      syncHiddenJson();
      loadSelectionFromHidden();
      equipmentCache = {};
      render();
    },
    initView: function () {
      loadSelectionFromHidden();
      render();
    },
    sync: function () {
      syncHiddenJson({ flushPending: true });
    },
    syncInvolvementHighlight: syncInvolvementHighlight
  };

  document.addEventListener('DOMContentLoaded', function () {
    loadSelectionFromHidden();
    if (isReadonly()) {
      render();
    }
    var form = document.getElementById('einsatzbericht-form') || document.getElementById('geraetewart-form')
      || document.getElementById('anwesenheitsliste-form');
    if (form) {
      form.addEventListener('submit', function () {
        syncHiddenJson({ flushPending: true });
      });
    }
  });
})();
