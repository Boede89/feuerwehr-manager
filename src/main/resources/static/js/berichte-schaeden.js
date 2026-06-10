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
    return yes && yes.checked;
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
        next[category.key][idx] = {
          name: nameInput ? nameInput.value.trim() : '',
          address: addressInput ? addressInput.value.trim() : '',
          birthdate: birthdateInput ? birthdateInput.value : ''
        };
      });
    });
    return next;
  }

  function syncHidden(state) {
    var hidden = document.getElementById('personDamageDetailsJson');
    if (hidden) {
      hidden.value = JSON.stringify(state);
    }
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
    var form = document.getElementById('einsatzbericht-form');
    if (form && form.dataset.personDamageSubmitBound !== 'true') {
      form.dataset.personDamageSubmitBound = 'true';
      form.addEventListener('submit', function () {
        state = collectState(state);
        syncHidden(state);
      });
    }
  }

  function readInitialFromHidden() {
    var hidden = document.getElementById('personDamageDetailsJson');
    if (hidden && hidden.value) {
      return hidden.value;
    }
    return '{}';
  }

  function init(root) {
    var scope = root || document;
    wrap = scope.querySelector ? scope.querySelector('#person-damage-details-wrap') : document.getElementById('person-damage-details-wrap');
    if (!wrap) {
      return;
    }
    readonly = wrap.dataset.readonly === 'true';
    state = parseInitial(wrap.dataset.initial || readInitialFromHidden());
    bindCountInputs(scope);
    render();
  }

  window.BerichteSchaeden = { init: init };
})();
