(function () {
  'use strict';

  var searchTimer = null;
  var unitPersons = [];

  function esc(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function isReadonly() {
    var board = document.getElementById('incident-kraefte-board');
    return board && board.dataset.readonly === 'true';
  }

  function personOnBoard(personId) {
    var chips = document.querySelectorAll('.incident-crew-chip[data-person-id="' + personId + '"]');
    for (var i = 0; i < chips.length; i++) {
      if (!chips[i].classList.contains('incident-crew-chip--reserve')) {
        return true;
      }
    }
    return false;
  }

  function modalEl() {
    return document.getElementById('unit-person-modal');
  }

  function loadPersons() {
    var dataEl = document.getElementById('unit-person-picker-data');
    if (!dataEl) {
      unitPersons = [];
      return;
    }
    try {
      unitPersons = JSON.parse(dataEl.textContent || '[]');
    } catch (e) {
      unitPersons = [];
    }
  }

  function openModal() {
    var modal = modalEl();
    if (!modal || isReadonly()) {
      return;
    }
    loadPersons();
    modal.hidden = false;
    modal.setAttribute('aria-hidden', 'false');
    document.body.classList.add('modal-open');
    var search = document.getElementById('unit-person-search');
    if (search) {
      search.value = '';
      search.focus();
    }
    renderResults('');
  }

  function closeModal() {
    var modal = modalEl();
    if (!modal) {
      return;
    }
    modal.hidden = true;
    modal.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('modal-open');
  }

  function renderResults(query) {
    var results = document.getElementById('unit-person-results');
    if (!results) {
      return;
    }
    var q = (query || '').trim().toLocaleLowerCase('de');
    var matches = unitPersons.filter(function (person) {
      var name = (person.name || '').toLocaleLowerCase('de');
      return !q || name.indexOf(q) !== -1;
    });
    if (matches.length === 0) {
      results.innerHTML = '<p class="hint">Keine passenden Personen gefunden.</p>';
      return;
    }
    var html = '<ul class="foreign-person-results__list">';
    matches.forEach(function (person) {
      var personId = String(person.id);
      var onBoard = personOnBoard(personId);
      html += '<li class="foreign-person-results__item' + (onBoard ? ' foreign-person-results__item--disabled' : '') + '">';
      html += '<button type="button" class="foreign-person-results__btn" data-person-id="' + esc(personId) + '"';
      if (onBoard) {
        html += ' disabled';
      }
      html += '><span class="foreign-person-results__name">' + esc(person.name) + '</span>';
      html += '</button></li>';
    });
    html += '</ul>';
    results.innerHTML = html;
  }

  function onSearchInput() {
    var search = document.getElementById('unit-person-search');
    if (!search) {
      return;
    }
    if (searchTimer) {
      window.clearTimeout(searchTimer);
    }
    var value = search.value;
    searchTimer = window.setTimeout(function () {
      renderResults(value);
    }, 150);
  }

  function onResultClick(e) {
    var btn = e.target.closest('.foreign-person-results__btn');
    if (!btn || btn.disabled) {
      return;
    }
    var personId = btn.dataset.personId;
    var person = unitPersons.find(function (p) {
      return String(p.id) === String(personId);
    });
    if (!person || !window.BerichteKraefte || !window.BerichteKraefte.addUnitPerson) {
      return;
    }
    var added = window.BerichteKraefte.addUnitPerson({
      id: person.id,
      displayName: person.name,
      name: person.name
    });
    if (added) {
      renderResults(document.getElementById('unit-person-search')?.value || '');
    }
  }

  function bind() {
    if (!document.querySelector('[data-berichte-form="anwesenheit"]')) {
      return;
    }
    document.getElementById('unit-person-open-btn')?.addEventListener('click', openModal);
    document.getElementById('unit-person-modal-close')?.addEventListener('click', closeModal);
    modalEl()?.querySelector('.modal__backdrop')?.addEventListener('click', closeModal);
    document.getElementById('unit-person-search')?.addEventListener('input', onSearchInput);
    document.getElementById('unit-person-results')?.addEventListener('click', onResultClick);
  }

  document.addEventListener('DOMContentLoaded', bind);
})();
