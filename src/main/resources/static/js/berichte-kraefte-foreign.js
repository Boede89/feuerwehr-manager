(function () {
  'use strict';

  var searchTimer = null;
  var selectedUnitId = '';
  var creating = false;

  function esc(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function csrfToken() {
    var meta = document.querySelector('meta[name="csrf-token"]');
    return meta ? meta.getAttribute('content') : '';
  }

  function csrfHeader() {
    var meta = document.querySelector('meta[name="csrf-header"]');
    return meta ? meta.getAttribute('content') : 'X-CSRF-TOKEN';
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

  function createOpenBtn() {
    return document.getElementById('foreign-person-create-open');
  }

  function createFormEl() {
    return document.getElementById('foreign-person-create-form');
  }

  function resetCreateFields() {
    var first = document.getElementById('foreign-person-first-name');
    var last = document.getElementById('foreign-person-last-name');
    if (first) {
      first.value = '';
    }
    if (last) {
      last.value = '';
    }
    showCreateError('');
  }

  function hideCreateForm() {
    var form = createFormEl();
    var openBtn = createOpenBtn();
    if (form) {
      form.hidden = true;
    }
    if (openBtn) {
      openBtn.hidden = false;
    }
    resetCreateFields();
  }

  function showCreateForm() {
    if (!selectedUnitId) {
      return;
    }
    var form = createFormEl();
    var openBtn = createOpenBtn();
    if (form) {
      form.hidden = false;
    }
    if (openBtn) {
      openBtn.hidden = true;
    }
    showCreateError('');
    var first = document.getElementById('foreign-person-first-name');
    if (first) {
      first.focus();
    }
  }

  function setCreateEnabled(enabled) {
    var openBtn = createOpenBtn();
    if (openBtn) {
      openBtn.disabled = !enabled;
    }
    if (!enabled) {
      hideCreateForm();
    }
  }

  function showCreateError(message) {
    var el = document.getElementById('foreign-person-create-error');
    if (!el) {
      return;
    }
    if (!message) {
      el.hidden = true;
      el.textContent = '';
      return;
    }
    el.hidden = false;
    el.textContent = message;
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
    setCreateEnabled(false);
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
    var apiBase = window.BerichteApiBase ? window.BerichteApiBase.path() : '/berichte/einsatzberichte';
    fetch(apiBase + '/foreign-units?unit=' + encodeURIComponent(unitId), {
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
    var apiBase = window.BerichteApiBase ? window.BerichteApiBase.path() : '/berichte/einsatzberichte';
    var url = apiBase + '/foreign-personnel?unit=' + encodeURIComponent(reportUnitId)
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
    setCreateEnabled(!!selectedUnitId);
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

  function addCreatedPerson(person) {
    var mapped = {
      id: Number(person.personId != null ? person.personId : person.id),
      displayName: person.displayName || '',
      qualTier: person.qualTier || 'MANNSCHAFT',
      unitLabel: person.unitName || person.unitLabel || ''
    };
    if (window.BerichteKraefte && window.BerichteKraefte.addForeignPerson) {
      window.BerichteKraefte.addForeignPerson(mapped);
    }
    closeModal();
  }

  function onPersonPick(btn) {
    if (!btn || btn.disabled) {
      return;
    }
    addCreatedPerson({
      personId: Number(btn.dataset.personId),
      displayName: btn.dataset.displayName || '',
      qualTier: btn.dataset.qualTier || 'MANNSCHAFT',
      unitName: btn.dataset.unitLabel || ''
    });
  }

  function createPerson() {
    if (creating || !selectedUnitId) {
      return;
    }
    var first = document.getElementById('foreign-person-first-name');
    var last = document.getElementById('foreign-person-last-name');
    var firstName = first ? first.value.trim() : '';
    var lastName = last ? last.value.trim() : '';
    if (!firstName || !lastName) {
      showCreateError('Bitte Vor- und Nachname eingeben.');
      return;
    }
    var reportUnitId = boardUnitId();
    if (!reportUnitId) {
      showCreateError('Einheit des Berichts fehlt.');
      return;
    }
    var apiBase = window.BerichteApiBase ? window.BerichteApiBase.path() : '/berichte/einsatzberichte';
    var body = new URLSearchParams();
    body.set('unit', reportUnitId);
    body.set('sourceUnit', selectedUnitId);
    body.set('firstName', firstName);
    body.set('lastName', lastName);
    creating = true;
    showCreateError('');
    var btn = document.getElementById('foreign-person-create-btn');
    if (btn) {
      btn.disabled = true;
    }
    var headers = {
      Accept: 'application/json',
      'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
    };
    headers[csrfHeader()] = csrfToken();
    fetch(apiBase + '/foreign-personnel', {
      method: 'POST',
      credentials: 'same-origin',
      headers: headers,
      body: body.toString()
    })
      .then(function (response) {
        return response.text().then(function (text) {
          var data = {};
          if (text) {
            try {
              data = JSON.parse(text);
            } catch (ignore) {
              data = {};
            }
          }
          return { ok: response.ok, data: data };
        });
      })
      .then(function (result) {
        if (!result.ok) {
          throw new Error((result.data && result.data.error) || 'Person konnte nicht angelegt werden.');
        }
        addCreatedPerson(result.data);
      })
      .catch(function (err) {
        showCreateError(err.message || 'Person konnte nicht angelegt werden.');
      })
      .finally(function () {
        creating = false;
        if (btn) {
          btn.disabled = false;
        }
      });
  }

  var bound = false;

  function bind() {
    if (bound) {
      return;
    }
    bound = true;
    document.addEventListener('click', function (e) {
      if (e.target.closest('#foreign-person-open-btn')) {
        if (!isReadonly()) {
          openModal();
        }
        return;
      }
      var modal = modalEl();
      if (!modal || modal.hidden) {
        return;
      }
      if (e.target.closest('#foreign-person-modal-close')
          || (e.target.classList && e.target.classList.contains('modal__backdrop')
              && e.target.closest('#foreign-person-modal'))) {
        closeModal();
        return;
      }
      var pickBtn = e.target.closest('#foreign-person-results .foreign-person-results__btn');
      if (pickBtn) {
        onPersonPick(pickBtn);
        return;
      }
      if (e.target.closest('#foreign-person-create-open')) {
        showCreateForm();
        return;
      }
      if (e.target.closest('#foreign-person-create-cancel')) {
        hideCreateForm();
        return;
      }
      if (e.target.closest('#foreign-person-create-btn')) {
        createPerson();
      }
    });
    document.addEventListener('change', function (e) {
      if (e.target && e.target.id === 'foreign-unit-select') {
        onUnitChange();
      }
    });
    document.addEventListener('input', function (e) {
      if (e.target && e.target.id === 'foreign-person-search') {
        onSearchInput();
      }
    });
    document.addEventListener('keydown', function (e) {
      if (e.key !== 'Enter') {
        return;
      }
      if (!e.target.closest('#foreign-person-create-form')) {
        return;
      }
      e.preventDefault();
      createPerson();
    });
  }

  window.BerichteKraefteForeign = { init: bind };

  document.addEventListener('DOMContentLoaded', bind);
})();
