(function () {
  'use strict';

  var CATEGORIES = [
    { key: 'rescued', countId: 'personsRescued', label: 'Gerettet', personLabel: 'Gerettete Person' },
    { key: 'injured', countId: 'personsInjured', label: 'Verletzt', personLabel: 'Verletzte Person' },
    { key: 'recovered', countId: 'personsRecovered', label: 'Geborgen', personLabel: 'Geborgene Person' },
    { key: 'dead', countId: 'personsDead', label: 'Verstorben', personLabel: 'Verstorbene Person' }
  ];

  function esc(text) {
    var div = document.createElement('div');
    div.textContent = text == null ? '' : String(text);
    return div.innerHTML;
  }

  function parseInitial(raw) {
    if (!raw) {
      return emptyDetails();
    }
    try {
      var parsed = JSON.parse(raw);
      return {
        rescued: Array.isArray(parsed.rescued) ? parsed.rescued : [],
        injured: Array.isArray(parsed.injured) ? parsed.injured : [],
        recovered: Array.isArray(parsed.recovered) ? parsed.recovered : [],
        dead: Array.isArray(parsed.dead) ? parsed.dead : []
      };
    } catch (e) {
      return emptyDetails();
    }
  }

  function emptyDetails() {
    return { rescued: [], injured: [], recovered: [], dead: [] };
  }

  function emptyEntry() {
    return { name: '', address: '', birthdate: '' };
  }

  function fitEntries(list, count) {
    var items = Array.isArray(list) ? list.slice() : [];
    while (items.length < count) {
      items.push(emptyEntry());
    }
    if (items.length > count) {
      items = items.slice(0, count);
    }
    return items.map(function (item) {
      return {
        name: item && item.name ? String(item.name) : '',
        address: item && item.address ? String(item.address) : '',
        birthdate: item && item.birthdate ? String(item.birthdate) : ''
      };
    });
  }

  function readCount(id) {
    var input = document.getElementById(id);
    if (!input) {
      return 0;
    }
    var value = Number(input.value);
    return Number.isFinite(value) && value > 0 ? Math.floor(value) : 0;
  }

  function personDamagesActive() {
    var yes = document.querySelector('input[name="personDamagesEnabled"][value="true"]');
    if (yes && yes.checked) {
      return true;
    }
    return CATEGORIES.some(function (category) {
      return readCount(category.countId) > 0;
    });
  }

  function ensurePersonDamagesEnabled() {
    if (!personDamagesActive()) {
      return;
    }
    var yes = document.querySelector('input[name="personDamagesEnabled"][value="true"]');
    if (yes && !yes.checked && !yes.disabled) {
      yes.checked = true;
    }
  }

  function collectState(current) {
    var next = emptyDetails();
    CATEGORIES.forEach(function (category) {
      var count = readCount(category.countId);
      next[category.key] = fitEntries(current[category.key], count);
      var section = document.querySelector('[data-person-damage-category="' + category.key + '"]');
      if (!section) {
        return;
      }
      section.querySelectorAll('[data-person-damage-index]').forEach(function (row) {
        var idx = Number(row.dataset.personDamageIndex);
        if (!Number.isFinite(idx) || idx < 0 || idx >= count) {
          return;
        }
        var nameInput = row.querySelector('[data-field="name"]');
        var addressInput = row.querySelector('[data-field="address"]');
        var birthdateInput = row.querySelector('[data-field="birthdate"]');
        var birthdate = birthdateInput && birthdateInput.value ? birthdateInput.value : null;
        next[category.key][idx] = {
          name: nameInput ? nameInput.value.trim() : '',
          address: addressInput ? addressInput.value.trim() : '',
          birthdate: birthdate
        };
      });
    });
    return next;
  }

  function syncHidden(state) {
    var hidden = document.getElementById('personDamageDetailsJson');
    if (!hidden) {
      return;
    }
    var payload = state || emptyDetails();
    if (!personDamagesActive()) {
      payload = emptyDetails();
    }
    hidden.value = JSON.stringify(payload);
  }

  function parsePerpetratorInitial(raw) {
    if (!raw) {
      return emptyPerpetrator();
    }
    try {
      var parsed = JSON.parse(raw);
      return {
        name: parsed && parsed.name ? String(parsed.name) : '',
        address: parsed && parsed.address ? String(parsed.address) : '',
        birthdate: parsed && parsed.birthdate ? String(parsed.birthdate) : '',
        licensePlate: parsed && parsed.licensePlate ? String(parsed.licensePlate) : ''
      };
    } catch (e) {
      return emptyPerpetrator();
    }
  }

  function emptyPerpetrator() {
    return { name: '', address: '', birthdate: '', licensePlate: '' };
  }

  function collectPerpetratorState() {
    var wrapEl = document.getElementById('damage-perpetrator-wrap');
    if (!wrapEl) {
      return emptyPerpetrator();
    }
    var nameInput = wrapEl.querySelector('[data-field="name"]');
    var addressInput = wrapEl.querySelector('[data-field="address"]');
    var birthdateInput = wrapEl.querySelector('[data-field="birthdate"]');
    var licensePlateInput = wrapEl.querySelector('[data-field="licensePlate"]');
    return {
      name: nameInput ? nameInput.value.trim() : '',
      address: addressInput ? addressInput.value.trim() : '',
      birthdate: birthdateInput && birthdateInput.value ? birthdateInput.value : null,
      licensePlate: licensePlateInput ? licensePlateInput.value.trim() : ''
    };
  }

  function perpetratorPayload(entry) {
    var data = entry || emptyPerpetrator();
    return {
      name: data.name ? data.name : null,
      address: data.address ? data.address : null,
      birthdate: data.birthdate ? data.birthdate : null,
      licensePlate: data.licensePlate ? data.licensePlate : null
    };
  }

  function syncPerpetratorHidden(entry) {
    var hidden = document.getElementById('damagePerpetratorJson');
    if (!hidden) {
      return;
    }
    hidden.value = JSON.stringify(perpetratorPayload(entry || collectPerpetratorState()));
  }

  function renderPerpetrator() {
    var wrapEl = document.getElementById('damage-perpetrator-wrap');
    if (!wrapEl) {
      return;
    }
    var entry = perpetratorState;
    wrapEl.innerHTML =
      '<div class="incident-form-grid-4 damage-perpetrator-fields">' +
        '<div class="form-group">' +
          '<label>Name</label>' +
          '<input type="text" maxlength="255" data-field="name" ' +
            (perpetratorReadonly ? 'readonly ' : '') +
            'value="' + esc(entry.name || '') + '" placeholder="Vor- und Nachname"/>' +
        '</div>' +
        '<div class="form-group">' +
          '<label>Anschrift</label>' +
          '<input type="text" maxlength="512" data-field="address" ' +
            (perpetratorReadonly ? 'readonly ' : '') +
            'value="' + esc(entry.address || '') + '" placeholder="Straße, PLZ Ort"/>' +
        '</div>' +
        '<div class="form-group">' +
          '<label>Geburtsdatum</label>' +
          '<input type="date" data-field="birthdate" ' +
            (perpetratorReadonly ? 'readonly ' : '') +
            'value="' + esc(entry.birthdate || '') + '"/>' +
        '</div>' +
        '<div class="form-group">' +
          '<label>Kennzeichen</label>' +
          '<input type="text" maxlength="32" data-field="licensePlate" ' +
            (perpetratorReadonly ? 'readonly ' : '') +
            'value="' + esc(entry.licensePlate || '') + '" placeholder="z. B. AB-C 1234"/>' +
        '</div>' +
      '</div>';
    if (!perpetratorReadonly) {
      wrapEl.querySelectorAll('input').forEach(function (input) {
        input.addEventListener('input', onPerpetratorFieldChange);
        input.addEventListener('change', onPerpetratorFieldChange);
      });
    }
    syncPerpetratorHidden();
  }

  function onPerpetratorFieldChange() {
    perpetratorState = collectPerpetratorState();
    syncPerpetratorHidden();
  }

  function initPerpetratorHiddenFromInitial() {
    var hidden = document.getElementById('damagePerpetratorJson');
    if (!hidden || hidden.value) {
      return;
    }
    var initial = readPerpetratorInitialPayload();
    if (initial) {
      hidden.value = initial;
    }
  }

  function readPerpetratorInitialPayload() {
    var hidden = document.getElementById('damagePerpetratorJson');
    if (hidden && hidden.value) {
      return hidden.value;
    }
    var wrapEl = document.getElementById('damage-perpetrator-wrap');
    if (wrapEl && wrapEl.dataset.initial) {
      return wrapEl.dataset.initial;
    }
    return '{}';
  }

  var perpetratorState = emptyPerpetrator();
  var perpetratorReadonly = false;

  var MANGEL_AN_TYPES = [
    { key: 'GEBAEUDE', label: 'Gebäude' },
    { key: 'FAHRZEUG', label: 'Fahrzeug' },
    { key: 'GERAET', label: 'Gerät' },
    { key: 'PSA', label: 'PSA' }
  ];

  function emptyMaterialEntry() {
    return {
      mangelAn: 'GEBAEUDE',
      bezeichnung: '',
      vehicleId: null,
      mangelBeschreibung: '',
      ursache: '',
      verbleib: ''
    };
  }

  function parseMaterialInitial(raw) {
    if (!raw) {
      return [];
    }
    try {
      var parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) {
        return [];
      }
      return parsed.map(function (item) {
        return {
          mangelAn: item && item.mangelAn ? String(item.mangelAn) : 'GEBAEUDE',
          bezeichnung: item && item.bezeichnung ? String(item.bezeichnung) : '',
          vehicleId: item && item.vehicleId ? Number(item.vehicleId) : null,
          mangelBeschreibung: item && item.mangelBeschreibung ? String(item.mangelBeschreibung) : '',
          ursache: item && item.ursache ? String(item.ursache) : '',
          verbleib: item && item.verbleib ? String(item.verbleib) : ''
        };
      });
    } catch (e) {
      return [];
    }
  }

  function parseVehicles(raw) {
    if (!raw) {
      return [];
    }
    try {
      var parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed : [];
    } catch (e) {
      return [];
    }
  }

  function materialEntryHasContent(entry) {
    if (!entry) {
      return false;
    }
    return Boolean(
      (entry.bezeichnung && entry.bezeichnung.trim())
        || (entry.mangelBeschreibung && entry.mangelBeschreibung.trim())
        || (entry.ursache && entry.ursache.trim())
        || (entry.verbleib && entry.verbleib.trim())
        || (entry.vehicleId && Number(entry.vehicleId) > 0)
    );
  }

  function normalizeMaterialPayload(entries) {
    return (entries || [])
      .map(function (entry) {
        var vehicleId = entry.vehicleId && Number(entry.vehicleId) > 0 ? Number(entry.vehicleId) : null;
        return {
          mangelAn: entry.mangelAn || 'GEBAEUDE',
          bezeichnung: entry.bezeichnung ? entry.bezeichnung.trim() : null,
          vehicleId: vehicleId,
          mangelBeschreibung: entry.mangelBeschreibung ? entry.mangelBeschreibung.trim() : null,
          ursache: entry.ursache ? entry.ursache.trim() : null,
          verbleib: entry.verbleib ? entry.verbleib.trim() : null
        };
      })
      .filter(materialEntryHasContent);
  }

  function collectMaterialState() {
    var wrapEl = document.getElementById('material-damage-wrap');
    if (!wrapEl) {
      return [];
    }
    var entries = [];
    wrapEl.querySelectorAll('[data-material-damage-index]').forEach(function (row) {
      var typeSelect = row.querySelector('[data-field="mangelAn"]');
      var bezeichnungInput = row.querySelector('[data-field="bezeichnung"]');
      var vehicleSelect = row.querySelector('[data-field="vehicleId"]');
      var beschreibungInput = row.querySelector('[data-field="mangelBeschreibung"]');
      var ursacheInput = row.querySelector('[data-field="ursache"]');
      var verbleibInput = row.querySelector('[data-field="verbleib"]');
      var vehicleValue = vehicleSelect && vehicleSelect.value ? Number(vehicleSelect.value) : null;
      entries.push({
        mangelAn: typeSelect ? typeSelect.value : 'GEBAEUDE',
        bezeichnung: bezeichnungInput ? bezeichnungInput.value.trim() : '',
        vehicleId: Number.isFinite(vehicleValue) && vehicleValue > 0 ? vehicleValue : null,
        mangelBeschreibung: beschreibungInput ? beschreibungInput.value.trim() : '',
        ursache: ursacheInput ? ursacheInput.value.trim() : '',
        verbleib: verbleibInput ? verbleibInput.value.trim() : ''
      });
    });
    return entries;
  }

  function syncMaterialHidden(entries) {
    var hidden = document.getElementById('materialDamageEntriesJson');
    if (!hidden) {
      return;
    }
    hidden.value = JSON.stringify(normalizeMaterialPayload(entries || collectMaterialState()));
  }

  function mangelAnLabel(key) {
    var found = MANGEL_AN_TYPES.find(function (item) {
      return item.key === key;
    });
    return found ? found.label : key;
  }

  function vehicleLabel(vehicles, vehicleId) {
    if (!vehicleId) {
      return '—';
    }
    var found = vehicles.find(function (vehicle) {
      return Number(vehicle.id) === Number(vehicleId);
    });
    return found ? found.name : 'Fahrzeug #' + vehicleId;
  }

  function buildMangelAnOptions(selected) {
    return MANGEL_AN_TYPES.map(function (item) {
      return '<option value="' + esc(item.key) + '"' + (item.key === selected ? ' selected' : '') + '>'
        + esc(item.label) + '</option>';
    }).join('');
  }

  function buildVehicleOptions(vehicles, selectedId) {
    var html = '<option value="">— kein Fahrzeug / nicht zutreffend —</option>';
    vehicles.forEach(function (vehicle) {
      var id = Number(vehicle.id);
      html += '<option value="' + esc(String(id)) + '"' + (id === Number(selectedId) ? ' selected' : '') + '>'
        + esc(vehicle.name || ('Fahrzeug #' + id)) + '</option>';
    });
    return html;
  }

  var materialDamageState = [];
  var materialDamageReadonly = false;
  var materialDamageVehicles = [];

  function renderMaterialEntry(index, entry, readonly) {
    var row = document.createElement('div');
    row.className = 'material-damage-entry';
    row.dataset.materialDamageIndex = String(index);
    if (readonly) {
      row.innerHTML =
        '<div class="material-damage-entry__title">Sachschaden ' + (index + 1) + '</div>' +
        '<dl class="material-damage-entry__readonly">' +
          '<div><dt>Art</dt><dd>' + esc(mangelAnLabel(entry.mangelAn)) + '</dd></div>' +
          '<div><dt>Bezeichnung</dt><dd>' + esc(entry.bezeichnung || '—') + '</dd></div>' +
          '<div><dt>Fahrzeug</dt><dd>' + esc(vehicleLabel(materialDamageVehicles, entry.vehicleId)) + '</dd></div>' +
          '<div class="material-damage-entry__readonly--full"><dt>Beschreibung</dt><dd>'
            + esc(entry.mangelBeschreibung || '—') + '</dd></div>' +
          '<div><dt>Ursache</dt><dd>' + esc(entry.ursache || '—') + '</dd></div>' +
          '<div><dt>Verbleib</dt><dd>' + esc(entry.verbleib || '—') + '</dd></div>' +
        '</dl>';
      return row;
    }
    row.innerHTML =
      '<div class="material-damage-entry__header">' +
        '<div class="material-damage-entry__title">Sachschaden ' + (index + 1) + '</div>' +
        '<button type="button" class="btn btn--outline btn--sm material-damage-entry__remove" ' +
          'data-action="remove-material-damage">Entfernen</button>' +
      '</div>' +
      '<div class="incident-form-grid-2 material-damage-entry__fields">' +
        '<div class="form-group">' +
          '<label>Art</label>' +
          '<select data-field="mangelAn" class="field">' + buildMangelAnOptions(entry.mangelAn || 'GEBAEUDE') + '</select>' +
        '</div>' +
        '<div class="form-group">' +
          '<label>Bezeichnung, ggf. Gerätenummer</label>' +
          '<input type="text" maxlength="255" data-field="bezeichnung" class="field" ' +
            'value="' + esc(entry.bezeichnung || '') + '" placeholder="z. B. TLF 16/25, Gerätenummer 123"/>' +
        '</div>' +
        '<div class="form-group form-group--full">' +
          '<label>Fahrzeug (auf dem sich das Gerät befindet)</label>' +
          '<select data-field="vehicleId" class="field">' +
            buildVehicleOptions(materialDamageVehicles, entry.vehicleId) +
          '</select>' +
          '<p class="hint text-sm">Wichtig, da Geräte auf mehreren Fahrzeugen existieren können.</p>' +
        '</div>' +
        '<div class="form-group form-group--full">' +
          '<label>Schaden Beschreibung</label>' +
          '<textarea data-field="mangelBeschreibung" class="field" rows="3" ' +
            'placeholder="Beschreibung des Schadens">' + esc(entry.mangelBeschreibung || '') + '</textarea>' +
        '</div>' +
        '<div class="form-group">' +
          '<label>Ursache</label>' +
          '<input type="text" data-field="ursache" class="field" ' +
            'value="' + esc(entry.ursache || '') + '" placeholder="Vermutete oder festgestellte Ursache"/>' +
        '</div>' +
        '<div class="form-group">' +
          '<label>Verbleib</label>' +
          '<input type="text" data-field="verbleib" class="field" ' +
            'value="' + esc(entry.verbleib || '') + '" placeholder="Verbleib"/>' +
        '</div>' +
      '</div>';
    row.querySelector('[data-action="remove-material-damage"]')?.addEventListener('click', function () {
      var damageWrap = document.getElementById('material-damage-wrap');
      if (damageWrap && damageWrap.querySelector('[data-material-damage-index]')) {
        materialDamageState = collectMaterialState();
      }
      materialDamageState.splice(index, 1);
      renderMaterialDamages();
    });
    row.querySelectorAll('input, select, textarea').forEach(function (input) {
      input.addEventListener('input', onMaterialFieldChange);
      input.addEventListener('change', onMaterialFieldChange);
    });
    return row;
  }

  function onMaterialFieldChange() {
    materialDamageState = collectMaterialState();
    syncMaterialHidden(materialDamageState);
  }

  function renderMaterialDamages() {
    var wrapEl = document.getElementById('material-damage-wrap');
    if (!wrapEl) {
      return;
    }
    wrapEl.innerHTML = '';
    var entries = materialDamageState.length ? materialDamageState : (materialDamageReadonly ? [] : []);
    if (!entries.length && materialDamageReadonly) {
      wrapEl.innerHTML = '<p class="hint material-damage-details-hint">Keine Sachschäden erfasst.</p>';
      return;
    }
    var list = document.createElement('div');
    list.className = 'material-damage-list';
    entries.forEach(function (entry, index) {
      list.appendChild(renderMaterialEntry(index, entry, materialDamageReadonly));
    });
    wrapEl.appendChild(list);
    if (!materialDamageReadonly) {
      var actions = document.createElement('div');
      actions.className = 'material-damage-actions';
      actions.innerHTML =
        '<button type="button" class="btn btn--outline btn--sm" data-action="add-material-damage">+ Sachschaden hinzufügen</button>';
      actions.querySelector('[data-action="add-material-damage"]')?.addEventListener('click', function () {
        if (wrapEl.querySelector('[data-material-damage-index]')) {
          materialDamageState = collectMaterialState();
        }
        materialDamageState.push(emptyMaterialEntry());
        renderMaterialDamages();
      });
      wrapEl.appendChild(actions);
    }
    syncMaterialHidden(entries);
  }

  function readMaterialInitialPayload() {
    var hidden = document.getElementById('materialDamageEntriesJson');
    if (hidden && hidden.value) {
      return hidden.value;
    }
    var wrapEl = document.getElementById('material-damage-wrap');
    if (wrapEl && wrapEl.dataset.initial) {
      return wrapEl.dataset.initial;
    }
    return '[]';
  }

  function initMaterialHiddenFromInitial() {
    var hidden = document.getElementById('materialDamageEntriesJson');
    if (!hidden || hidden.value) {
      return;
    }
    var initial = readMaterialInitialPayload();
    if (initial) {
      hidden.value = initial;
    }
  }

  function initMaterialDamages(scope) {
    var wrapEl = scope.querySelector
        ? scope.querySelector('#material-damage-wrap')
        : document.getElementById('material-damage-wrap');
    if (!wrapEl) {
      return;
    }
    materialDamageReadonly = wrapEl.dataset.readonly === 'true';
    materialDamageVehicles = parseVehicles(wrapEl.dataset.vehicles || '[]');
    if (wrapEl.querySelector('[data-material-damage-index]') && !materialDamageReadonly) {
      materialDamageState = collectMaterialState();
      syncMaterialHidden(materialDamageState);
      return;
    }
    initMaterialHiddenFromInitial();
    materialDamageState = parseMaterialInitial(readMaterialInitialPayload());
    renderMaterialDamages();
  }

  function syncBeforeSave() {
    ensurePersonDamagesEnabled();
    state = collectState(state);
    syncHidden(state);
    perpetratorState = collectPerpetratorState();
    syncPerpetratorHidden();
    materialDamageState = collectMaterialState();
    syncMaterialHidden(materialDamageState);
  }

  function renderEntry(category, index, entry, readonly) {
    var row = document.createElement('div');
    row.className = 'person-damage-entry';
    row.dataset.personDamageIndex = String(index);
    row.innerHTML =
      '<div class="person-damage-entry__title">' + esc(category.personLabel) + ' ' + (index + 1) + '</div>' +
      '<div class="incident-form-grid-3 person-damage-entry__fields">' +
        '<div class="form-group">' +
          '<label>Name</label>' +
          '<input type="text" maxlength="255" data-field="name" ' +
            (readonly ? 'readonly ' : '') +
            'value="' + esc(entry.name || '') + '" placeholder="Vor- und Nachname"/>' +
        '</div>' +
        '<div class="form-group">' +
          '<label>Adresse</label>' +
          '<input type="text" maxlength="512" data-field="address" ' +
            (readonly ? 'readonly ' : '') +
            'value="' + esc(entry.address || '') + '" placeholder="Straße, PLZ Ort"/>' +
        '</div>' +
        '<div class="form-group">' +
          '<label>Geburtsdatum</label>' +
          '<input type="date" data-field="birthdate" ' +
            (readonly ? 'readonly ' : '') +
            'value="' + esc(entry.birthdate || '') + '"/>' +
        '</div>' +
      '</div>';
    if (!readonly) {
      row.querySelectorAll('input').forEach(function (input) {
        input.addEventListener('input', onFieldChange);
        input.addEventListener('change', onFieldChange);
      });
    }
    return row;
  }

  function renderCategory(category, entries, readonly) {
    if (!entries.length) {
      return null;
    }
    var section = document.createElement('div');
    section.className = 'person-damage-category';
    section.dataset.personDamageCategory = category.key;
    var title = document.createElement('h5');
    title.className = 'person-damage-category__title';
    title.textContent = category.label + ' (' + entries.length + ')';
    section.appendChild(title);
    entries.forEach(function (entry, index) {
      section.appendChild(renderEntry(category, index, entry, readonly));
    });
    return section;
  }

  var state = emptyDetails();
  var wrap = null;
  var readonly = false;

  function onFieldChange() {
    state = collectState(state);
    syncHidden(state);
  }

  function render() {
    if (!wrap) {
      return;
    }
    state = collectState(state);
    wrap.innerHTML = '';
    if (!personDamagesActive()) {
      syncHidden(emptyDetails());
      return;
    }
    var hasAny = false;
    CATEGORIES.forEach(function (category) {
      var count = readCount(category.countId);
      var entries = fitEntries(state[category.key], count);
      state[category.key] = entries;
      var section = renderCategory(category, entries, readonly);
      if (section) {
        wrap.appendChild(section);
        hasAny = true;
      }
    });
    if (!hasAny) {
      wrap.innerHTML = '<p class="hint person-damage-details-hint">Tragen Sie bei den Personenschäden eine Anzahl ein, um die Detailfelder anzuzeigen.</p>';
    }
    syncHidden(state);
  }

  function bindCountInputs(scope) {
    CATEGORIES.forEach(function (category) {
      var input = scope.getElementById ? scope.getElementById(category.countId) : document.getElementById(category.countId);
      if (!input || input.dataset.personDamageBound === 'true') {
        return;
      }
      input.dataset.personDamageBound = 'true';
      input.addEventListener('input', render);
      input.addEventListener('change', render);
    });
    scope.querySelectorAll('input[name="personDamagesEnabled"]').forEach(function (radio) {
      if (radio.dataset.personDamageBound === 'true') {
        return;
      }
      radio.dataset.personDamageBound = 'true';
      radio.addEventListener('change', render);
    });
    bindSaveSync(scope);
  }

  function bindSaveSync(scope) {
    var form = document.getElementById('einsatzbericht-form');
    if (form && form.dataset.personDamageSubmitBound !== 'true') {
      form.dataset.personDamageSubmitBound = 'true';
      form.addEventListener('submit', syncBeforeSave, true);
    }
    (scope.querySelectorAll
        ? scope.querySelectorAll('button[form="einsatzbericht-form"][type="submit"]')
        : document.querySelectorAll('button[form="einsatzbericht-form"][type="submit"]'))
      .forEach(function (btn) {
        if (btn.dataset.personDamageSubmitBound === 'true') {
          return;
        }
        btn.dataset.personDamageSubmitBound = 'true';
        btn.addEventListener('click', syncBeforeSave);
      });
  }

  function readInitialPayload() {
    var hidden = document.getElementById('personDamageDetailsJson');
    if (hidden && hidden.value) {
      return hidden.value;
    }
    var wrapEl = document.getElementById('person-damage-details-wrap');
    if (wrapEl && wrapEl.dataset.initial) {
      return wrapEl.dataset.initial;
    }
    return '{}';
  }

  function initHiddenFromInitial() {
    var hidden = document.getElementById('personDamageDetailsJson');
    if (!hidden || hidden.value) {
      return;
    }
    var initial = readInitialPayload();
    if (initial) {
      hidden.value = initial;
    }
  }

  function initPerpetrator(root) {
    var scope = root || document;
    var wrapEl = scope.querySelector
        ? scope.querySelector('#damage-perpetrator-wrap')
        : document.getElementById('damage-perpetrator-wrap');
    if (!wrapEl) {
      return;
    }
    perpetratorReadonly = wrapEl.dataset.readonly === 'true';
    if (wrapEl.querySelector('[data-field="name"]') && !perpetratorReadonly) {
      perpetratorState = collectPerpetratorState();
      syncPerpetratorHidden(perpetratorState);
      return;
    }
    initPerpetratorHiddenFromInitial();
    perpetratorState = parsePerpetratorInitial(readPerpetratorInitialPayload());
    renderPerpetrator();
  }

  function initPersonDamage(scope) {
    wrap = scope.querySelector
        ? scope.querySelector('#person-damage-details-wrap')
        : document.getElementById('person-damage-details-wrap');
    if (!wrap) {
      return;
    }
    readonly = wrap.dataset.readonly === 'true';
    if (wrap.querySelector('[data-person-damage-index]') && !readonly) {
      state = collectState(state);
      syncHidden(state);
      return;
    }
    initHiddenFromInitial();
    state = parseInitial(readInitialPayload());
    bindCountInputs(scope);
    render();
  }

  function init(root) {
    var scope = root || document;
    bindSaveSync(scope);
    initPersonDamage(scope);
    initPerpetrator(scope);
    initMaterialDamages(scope);
  }

  window.BerichteSchaeden = { init: init, syncBeforeSave: syncBeforeSave };
})();
