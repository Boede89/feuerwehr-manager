(function () {
  'use strict';

  function esc(text) {
    var div = document.createElement('div');
    div.textContent = text == null ? '' : String(text);
    return div.innerHTML;
  }

  function hiddenField() {
    return document.getElementById('crewInjuryEntriesJson');
  }

  function wrapEl(scope) {
    return (scope || document).querySelector('#crew-injury-wrap')
      || document.getElementById('crew-injury-wrap');
  }

  function isReadonly(wrap) {
    return wrap && wrap.dataset.readonly === 'true';
  }

  function parseEntries(raw) {
    try {
      var data = JSON.parse(raw || '[]');
      return Array.isArray(data) ? data : [];
    } catch (e) {
      return [];
    }
  }

  function involvedPersonOptions() {
    var options = [];
    var seen = {};
    document.querySelectorAll(
      '#involved-card .incident-crew-chip:not(.incident-crew-chip--involved-mirror),' +
      '.incident-vehicle-dropzone .incident-crew-chip:not(.incident-crew-chip--involved-mirror)'
    ).forEach(function (chip) {
      var id = chip.dataset.personId;
      if (!id || seen[id]) {
        return;
      }
      seen[id] = true;
      options.push({
        id: id,
        name: chip.dataset.personName || chip.textContent.trim()
      });
    });
    options.sort(function (a, b) {
      return a.name.localeCompare(b.name, 'de');
    });
    return options;
  }

  function syncHidden(entries) {
    var field = hiddenField();
    if (field) {
      field.value = JSON.stringify(entries || []);
    }
  }

  function readFromDom(wrap) {
    var entries = [];
    wrap.querySelectorAll('[data-crew-injury-index]').forEach(function (row) {
      var personSelect = row.querySelector('[data-field="personId"]');
      var timeInput = row.querySelector('[data-field="time"]');
      var descInput = row.querySelector('[data-field="description"]');
      var personId = personSelect ? Number(personSelect.value) : null;
      var personName = '';
      if (personSelect && personSelect.selectedOptions[0]) {
        personName = personSelect.selectedOptions[0].textContent.trim();
      }
      entries.push({
        personId: personId && !isNaN(personId) ? personId : null,
        personName: personName || null,
        time: timeInput ? timeInput.value : '',
        description: descInput ? descInput.value.trim() : ''
      });
    });
    return entries.filter(function (entry) {
      return entry.personId || entry.description || entry.time;
    });
  }

  function renderEntry(index, entry, readonly, personOptions) {
    var row = document.createElement('article');
    row.className = 'crew-injury-entry';
    row.dataset.crewInjuryIndex = String(index);
    if (readonly) {
      row.innerHTML =
        '<div class="crew-injury-entry__title">Eintrag ' + (index + 1) + '</div>' +
        '<dl class="material-damage-entry__readonly">' +
        '<div><dt>Einsatzkraft</dt><dd>' + esc(entry.personName || '—') + '</dd></div>' +
        '<div><dt>Uhrzeit</dt><dd>' + esc(entry.time || '—') + '</dd></div>' +
        '<div class="material-damage-entry__readonly--full"><dt>Beschreibung</dt><dd>' +
        esc(entry.description || '—') + '</dd></div></dl>';
      return row;
    }

    var optionsHtml = '<option value="">Person wählen …</option>';
    personOptions.forEach(function (p) {
      var selected = String(entry.personId || '') === String(p.id) ? ' selected' : '';
      optionsHtml += '<option value="' + esc(p.id) + '"' + selected + '>' + esc(p.name) + '</option>';
    });
    if (entry.personId && !personOptions.some(function (p) { return String(p.id) === String(entry.personId); })) {
      optionsHtml += '<option value="' + esc(entry.personId) + '" selected>' +
        esc(entry.personName || ('Person #' + entry.personId)) + '</option>';
    }

    row.innerHTML =
      '<div class="material-damage-entry__header">' +
      '  <div class="crew-injury-entry__title">Eintrag ' + (index + 1) + '</div>' +
      '  <button type="button" class="btn btn--outline btn--sm" data-action="remove-crew-injury">Entfernen</button>' +
      '</div>' +
      '<div class="incident-form-grid-2 material-damage-entry__fields">' +
      '  <div class="form-group"><label>Einsatzkraft</label>' +
      '    <select class="field" data-field="personId">' + optionsHtml + '</select></div>' +
      '  <div class="form-group"><label>Uhrzeit</label>' +
      '    <input type="time" class="field" data-field="time" value="' + esc(entry.time || '') + '"/></div>' +
      '  <div class="form-group form-group--full"><label>Was ist passiert?</label>' +
      '    <textarea class="field" rows="3" data-field="description" placeholder="Kurzbeschreibung …">' +
      esc(entry.description || '') + '</textarea></div>' +
      '</div>';

    row.querySelector('[data-action="remove-crew-injury"]')?.addEventListener('click', function () {
      var wrap = wrapEl();
      if (!wrap) {
        return;
      }
      var entries = readFromDom(wrap);
      entries.splice(index, 1);
      syncHidden(entries);
      render(wrap);
    });
    row.querySelectorAll('[data-field]').forEach(function (input) {
      input.addEventListener('change', function () {
        var wrap = wrapEl();
        if (wrap) {
          syncHidden(readFromDom(wrap));
        }
      });
      input.addEventListener('input', function () {
        var wrap = wrapEl();
        if (wrap) {
          syncHidden(readFromDom(wrap));
        }
      });
    });
    return row;
  }

  function render(wrap) {
    if (!wrap) {
      return;
    }
    var readonly = isReadonly(wrap);
    var entries = parseEntries((hiddenField() && hiddenField().value) || wrap.dataset.initial || '[]');
    var personOptions = involvedPersonOptions();
    wrap.textContent = '';
    if (entries.length === 0 && readonly) {
      wrap.innerHTML = '<p class="hint">Keine Personenschäden erfasst.</p>';
      return;
    }
    var list = document.createElement('div');
    list.className = 'crew-injury-list';
    entries.forEach(function (entry, index) {
      list.appendChild(renderEntry(index, entry || {}, readonly, personOptions));
    });
    wrap.appendChild(list);
    if (!readonly) {
      var actions = document.createElement('div');
      actions.className = 'material-damage-actions';
      actions.innerHTML =
        '<button type="button" class="btn btn--primary btn--sm" data-action="add-crew-injury">' +
        '+ Personenschaden hinzufügen</button>';
      actions.querySelector('[data-action="add-crew-injury"]')?.addEventListener('click', function () {
        var current = readFromDom(wrap);
        current.push({ personId: null, personName: '', time: '', description: '' });
        syncHidden(current);
        render(wrap);
      });
      wrap.appendChild(actions);
    }
    syncHidden(entries);
  }

  function init(scope) {
    var wrap = wrapEl(scope);
    if (!wrap || wrap.dataset.bound === 'true') {
      if (wrap) {
        render(wrap);
      }
      return;
    }
    wrap.dataset.bound = 'true';
    var field = hiddenField();
    if (field && (!field.value || field.value === '') && wrap.dataset.initial) {
      field.value = wrap.dataset.initial;
    }
    render(wrap);
    var form = document.getElementById('anwesenheitsliste-form');
    if (form) {
      form.addEventListener('submit', function () {
        syncHidden(readFromDom(wrap));
      }, true);
    }
  }

  window.BerichteAnwesenheitCrewInjury = {
    init: init,
    sync: function () {
      var wrap = wrapEl();
      if (wrap) {
        syncHidden(readFromDom(wrap));
      }
    }
  };
})();
