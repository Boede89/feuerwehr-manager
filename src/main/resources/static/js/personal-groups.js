(function () {
  'use strict';

  var searchTimer = null;
  var unitsLoaded = false;

  function esc(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function unitId() {
    var picker = document.getElementById('group-member-picker');
    return picker ? picker.getAttribute('data-unit-id') : '';
  }

  function openModal(id) {
    var overlay = document.getElementById(id);
    if (!overlay) return;
    overlay.classList.add('active');
    document.body.classList.add('modal-open');
  }

  function closeModal(overlay) {
    if (!overlay) return;
    overlay.classList.remove('active');
    if (!document.querySelector('.modal-overlay.active')) {
      document.body.classList.remove('modal-open');
    }
  }

  function memberPicker() {
    return document.getElementById('group-member-picker');
  }

  function clearForeignMembers() {
    var picker = memberPicker();
    if (!picker) return;
    picker.querySelectorAll('.member-picker__item--foreign').forEach(function (el) {
      el.remove();
    });
  }

  function resetMemberCheckboxes() {
    clearForeignMembers();
    document.querySelectorAll('#form-group input[name="personIds"]').forEach(function (cb) {
      cb.checked = false;
    });
  }

  function ensureForeignMemberCheckbox(personId, displayName, unitName) {
    var picker = memberPicker();
    if (!picker) return null;
    var existing = picker.querySelector('input[name="personIds"][value="' + personId + '"]');
    if (existing) {
      return existing;
    }
    var label = document.createElement('label');
    label.className = 'checkbox-row member-picker__item member-picker__item--foreign';
    var input = document.createElement('input');
    input.type = 'checkbox';
    input.name = 'personIds';
    input.value = String(personId);
    input.setAttribute('data-person-id', String(personId));
    input.checked = true;
    var span = document.createElement('span');
    span.textContent = displayName + (unitName ? ' (' + unitName + ')' : '');
    label.appendChild(input);
    label.appendChild(span);
    picker.appendChild(label);
    var empty = document.getElementById('group-member-empty');
    if (empty) {
      empty.hidden = true;
    }
    return input;
  }

  function setMemberCheckboxes(memberIds, foreignMembersJson) {
    resetMemberCheckboxes();
    var foreign = [];
    try {
      foreign = foreignMembersJson ? JSON.parse(foreignMembersJson) : [];
    } catch (e) {
      foreign = [];
    }
    (foreign || []).forEach(function (item) {
      if (!item || item.id == null) return;
      ensureForeignMemberCheckbox(item.id, item.name || ('Person ' + item.id), item.unitName || '');
    });
    if (!memberIds) return;
    memberIds.split(',').forEach(function (id) {
      id = id.trim();
      if (!id) return;
      var cb = document.querySelector('#form-group input[name="personIds"][value="' + id + '"]');
      if (cb) {
        cb.checked = true;
      }
    });
  }

  function prepareCreateForm() {
    var form = document.getElementById('form-group');
    var title = document.getElementById('modal-group-title');
    if (!form) return;
    form.action = form.getAttribute('data-create-action') || form.action;
    if (title) title.textContent = 'Gruppe anlegen';
    var nameInput = document.getElementById('group-name');
    if (nameInput) nameInput.value = '';
    resetMemberCheckboxes();
  }

  function prepareEditForm(row) {
    var form = document.getElementById('form-group');
    var title = document.getElementById('modal-group-title');
    if (!form || !row) return;
    var groupId = row.getAttribute('data-id');
    var base = form.getAttribute('data-create-action') || '/personal/groups';
    form.action = base.replace(/\/groups\/?$/, '/groups/' + groupId);
    if (title) title.textContent = 'Gruppe bearbeiten';
    var nameInput = document.getElementById('group-name');
    if (nameInput) nameInput.value = row.getAttribute('data-name') || '';
    setMemberCheckboxes(row.getAttribute('data-member-ids'), row.getAttribute('data-foreign-members'));
  }

  function openForeignModal() {
    openModal('modal-group-foreign-person');
    loadForeignUnits();
  }

  function closeForeignModal() {
    closeModal(document.getElementById('modal-group-foreign-person'));
    var results = document.getElementById('group-foreign-person-results');
    if (results) {
      results.innerHTML = '<p class="hint">Bitte zuerst eine Einheit wählen.</p>';
    }
    var search = document.getElementById('group-foreign-person-search');
    if (search) {
      search.value = '';
      search.disabled = true;
    }
    var select = document.getElementById('group-foreign-unit-select');
    if (select) {
      select.value = '';
    }
  }

  function loadForeignUnits() {
    var select = document.getElementById('group-foreign-unit-select');
    var ownUnit = unitId();
    if (!select || !ownUnit) return;
    if (unitsLoaded && select.options.length > 1) return;
    fetch('/personal/foreign-units?unit=' + encodeURIComponent(ownUnit), {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' }
    })
      .then(function (res) {
        if (!res.ok) throw new Error('load failed');
        return res.json();
      })
      .then(function (units) {
        while (select.options.length > 1) {
          select.remove(1);
        }
        (units || []).forEach(function (unit) {
          var opt = document.createElement('option');
          opt.value = String(unit.id);
          opt.textContent = unit.name;
          select.appendChild(opt);
        });
        unitsLoaded = true;
      })
      .catch(function () {
        var opt = document.createElement('option');
        opt.value = '';
        opt.textContent = 'Fehler beim Laden';
        select.appendChild(opt);
      });
  }

  function searchForeignPersonnel() {
    var ownUnit = unitId();
    var select = document.getElementById('group-foreign-unit-select');
    var search = document.getElementById('group-foreign-person-search');
    var results = document.getElementById('group-foreign-person-results');
    if (!ownUnit || !select || !results) return;
    var sourceUnit = select.value;
    if (!sourceUnit) {
      results.innerHTML = '<p class="hint">Bitte zuerst eine Einheit wählen.</p>';
      return;
    }
    var q = search ? search.value.trim() : '';
    results.innerHTML = '<p class="hint">Lade …</p>';
    var url = '/personal/foreign-personnel?unit=' + encodeURIComponent(ownUnit)
      + '&sourceUnit=' + encodeURIComponent(sourceUnit)
      + '&q=' + encodeURIComponent(q);
    fetch(url, { credentials: 'same-origin', headers: { Accept: 'application/json' } })
      .then(function (res) {
        if (!res.ok) throw new Error('load failed');
        return res.json();
      })
      .then(function (persons) {
        if (!persons || !persons.length) {
          results.innerHTML = '<p class="hint">Kein Personal gefunden.</p>';
          return;
        }
        var html = '<ul class="foreign-person-results__list">';
        persons.forEach(function (person) {
          var already = !!document.querySelector('#form-group input[name="personIds"][value="' + person.id + '"]:checked');
          html += '<li class="foreign-person-results__item' + (already ? ' foreign-person-results__item--disabled' : '') + '">';
          html += '<button type="button" class="foreign-person-results__btn"'
            + ' data-person-id="' + esc(person.id) + '"'
            + ' data-display-name="' + esc(person.displayName) + '"'
            + ' data-unit-name="' + esc(person.unitName || '') + '"'
            + (already ? ' disabled' : '') + '>';
          html += '<span>' + esc(person.displayName) + '</span>';
          if (person.unitName) {
            html += '<span class="foreign-person-results__unit">' + esc(person.unitName) + '</span>';
          }
          html += already ? '<span>bereits gewählt</span>' : '<span>Hinzufügen</span>';
          html += '</button></li>';
        });
        html += '</ul>';
        results.innerHTML = html;
        results.querySelectorAll('.foreign-person-results__btn:not(:disabled)').forEach(function (btn) {
          btn.addEventListener('click', function () {
            var input = ensureForeignMemberCheckbox(
              btn.getAttribute('data-person-id'),
              btn.getAttribute('data-display-name'),
              btn.getAttribute('data-unit-name')
            );
            if (input) input.checked = true;
            btn.disabled = true;
            var item = btn.closest('.foreign-person-results__item');
            if (item) item.classList.add('foreign-person-results__item--disabled');
            btn.innerHTML = '<span>' + esc(btn.getAttribute('data-display-name')) + '</span>'
              + '<span class="foreign-person-results__unit">' + esc(btn.getAttribute('data-unit-name') || '') + '</span>'
              + '<span>bereits gewählt</span>';
          });
        });
      })
      .catch(function () {
        results.innerHTML = '<p class="hint">Personal konnte nicht geladen werden.</p>';
      });
  }

  document.querySelectorAll('[data-open-modal="modal-group-form"]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      prepareCreateForm();
      openModal('modal-group-form');
    });
  });

  document.querySelectorAll('[data-edit-group]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var row = btn.closest('.group-row');
      prepareEditForm(row);
      openModal('modal-group-form');
    });
  });

  document.querySelectorAll('#modal-group-form [data-close-modal]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var overlay = btn.closest('.modal-overlay');
      if (overlay) closeModal(overlay);
    });
  });

  document.getElementById('modal-group-form')?.addEventListener('click', function (e) {
    if (e.target === e.currentTarget) closeModal(e.currentTarget);
  });

  document.getElementById('btn-group-add-foreign')?.addEventListener('click', openForeignModal);
  document.querySelectorAll('[data-close-group-foreign]').forEach(function (btn) {
    btn.addEventListener('click', closeForeignModal);
  });
  document.getElementById('modal-group-foreign-person')?.addEventListener('click', function (e) {
    if (e.target === e.currentTarget) closeForeignModal();
  });

  document.getElementById('group-foreign-unit-select')?.addEventListener('change', function () {
    var search = document.getElementById('group-foreign-person-search');
    if (search) {
      search.disabled = !this.value;
      search.value = '';
    }
    searchForeignPersonnel();
  });

  document.getElementById('group-foreign-person-search')?.addEventListener('input', function () {
    window.clearTimeout(searchTimer);
    searchTimer = window.setTimeout(searchForeignPersonnel, 250);
  });
})();
