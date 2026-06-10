(function () {
  'use strict';

  var searchTimer = null;
  var selectedUnitId = '';

  function esc(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function boardUnitId() {
    var board = document.getElementById('incident-kraefte-board');
    return board ? board.dataset.unitId : null;
  }

  function isReadonly() {
    var board = document.getElementById('incident-kraefte-board');
    return board && board.dataset.readonly === 'true';
  }

  function personOnBoard(personId) {
    return !!document.querySelector('.incident-crew-chip[data-person-id="' + personId + '"]');
  }

  function modalEl() {
    return document.getElementById('foreign-person-modal');
  }

  function openModal() {
    var modal = modalEl();
    if (!modal) {
      return;
    }
    modal.hidden = false;
    modal.setAttribute('aria-hidden', 'false');
    document.body.classList.add('modal-open');
    loadUnits();
  }

  function closeModal() {
    var modal = modalEl();
    if (!modal) {
      return;
    }
    modal.hidden = true;
    modal.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('modal-open');
    var results = document.getElementById('foreign-person-results');
    if (results) {
      results.innerHTML = '';
    }
    var search = document.getElementById('foreign-person-search');
    if (search) {
      search.value = '';
      search.disabled = true;
    }
    selectedUnitId = '';
    var select = document.getElementById('foreign-unit-select');
    if (select) {
      select.value = '';
    }
  }

  function loadUnits() {
    var unitId = boardUnitId();
    var select = document.getElementById('foreign-unit-select');
    if (!unitId || !select) {
      return;
    }
    if (select.options.length > 1) {
      return;
    }
    fetch('/berichte/einsatzberichte/foreign-units?unit=' + encodeURIComponent(unitId), {
      headers: { Accept: 'application/json' }
    })
      .then(function (response) {
        if (!response.ok) {
          throw new Error('Einheiten konnten nicht geladen werden.');
        }
        return response.json();
      })
      .then(function (units) {
        units.forEach(function (unit) {
          var option = document.createElement('option');
          option.value = String(unit.id);
          option.textContent = unit.name;
          select.appendChild(option);
        });
      })
      .catch(function () {
        var option = document.createElement('option');
        option.value = '';
        option.textContent = 'Fehler beim Laden';
        select.appendChild(option);
      });
  }

  function renderResults(persons) {
    var results = document.getElementById('foreign-person-results');
    if (!results) {
      return;
    }
    if (!persons || persons.length === 0) {
      results.innerHTML = '<p class="hint">Kein Personal gefunden.</p>';
      return;
    }
    var html = '<ul class="foreign-person-results__list">';
    persons.forEach(function (person) {
      var personId = person.personId != null ? person.personId : person.id;
      var onBoard = personOnBoard(String(personId));
      html += '<li class="foreign-person-results__item' + (onBoard ? ' foreign-person-results__item--disabled' : '') + '">';
      html += '<button type="button" class="foreign-person-results__btn" data-person-id="' + esc(personId) + '"';
      html += ' data-display-name="' + esc(person.displayName) + '"';
      html += ' data-qual-tier="' + esc(person.qualTier || 'MANNSCHAFT') + '"';
      html += ' data-unit-label="' + esc(person.unitName || '') + '"';
      html += onBoard ? ' disabled' : '';
      html += '><span class="foreign-person-results__name">' + esc(person.displayName) + '</span>';
      if (person.unitName) {
        html += '<span class="foreign-person-results__unit">' + esc(person.unitName) + '</span>';
      }
      html += '</button></li>';
    });
    html += '</ul>';
    results.innerHTML = html;
  }

  function loadPersonnel(query) {
    var reportUnitId = boardUnitId();
    if (!reportUnitId || !selectedUnitId) {
      return;
    }
    var url = '/berichte/einsatzberichte/foreign-personnel?unit=' + encodeURIComponent(reportUnitId)
      + '&sourceUnit=' + encodeURIComponent(selectedUnitId);
    if (query && query.trim()) {
      url += '&q=' + encodeURIComponent(query.trim());
    }
    fetch(url, { headers: { Accept: 'application/json' } })
      .then(function (response) {
        if (!response.ok) {
          throw new Error('Personal konnte nicht geladen werden.');
        }
        return response.json();
      })
      .then(renderResults)
      .catch(function () {
        var results = document.getElementById('foreign-person-results');
        if (results) {
          results.innerHTML = '<p class="hint">Fehler beim Laden des Personals.</p>';
        }
      });
  }

  function onUnitChange() {
    var select = document.getElementById('foreign-unit-select');
    var search = document.getElementById('foreign-person-search');
    selectedUnitId = select && select.value ? select.value : '';
    if (search) {
      search.disabled = !selectedUnitId;
      search.value = '';
    }
    if (!selectedUnitId) {
      var results = document.getElementById('foreign-person-results');
      if (results) {
        results.innerHTML = '<p class="hint">Bitte zuerst eine Einheit wählen.</p>';
      }
      return;
    }
    loadPersonnel('');
  }

  function onSearchInput() {
    var search = document.getElementById('foreign-person-search');
    if (!search || !selectedUnitId) {
      return;
    }
    clearTimeout(searchTimer);
    searchTimer = setTimeout(function () {
      loadPersonnel(search.value);
    }, 250);
  }

  function onPersonPick(btn) {
    if (!btn || btn.disabled) {
      return;
    }
    var person = {
      id: Number(btn.dataset.personId),
      displayName: btn.dataset.displayName || '',
      qualTier: btn.dataset.qualTier || 'MANNSCHAFT',
      unitLabel: btn.dataset.unitLabel || ''
    };
    if (window.BerichteKraefte && window.BerichteKraefte.addForeignPerson) {
      window.BerichteKraefte.addForeignPerson(person);
    }
    closeModal();
  }

  function bind() {
    if (isReadonly()) {
      return;
    }
    var openBtn = document.getElementById('foreign-person-open-btn');
    if (openBtn) {
      openBtn.addEventListener('click', openModal);
    }
    var modal = modalEl();
    if (!modal) {
      return;
    }
    modal.querySelector('.modal__backdrop')?.addEventListener('click', closeModal);
    document.getElementById('foreign-person-modal-close')?.addEventListener('click', closeModal);
    document.getElementById('foreign-unit-select')?.addEventListener('change', onUnitChange);
    document.getElementById('foreign-person-search')?.addEventListener('input', onSearchInput);
    document.getElementById('foreign-person-results')?.addEventListener('click', function (e) {
      var btn = e.target.closest('.foreign-person-results__btn');
      if (btn) {
        onPersonPick(btn);
      }
    });
  }

  document.addEventListener('DOMContentLoaded', bind);
})();
