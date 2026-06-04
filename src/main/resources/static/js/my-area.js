(function () {
  function onReady(fn) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', fn);
    } else {
      fn();
    }
  }

  function syncRowToHidden(row) {
    var name = row.querySelector('.ec-name');
    var phone = row.querySelector('.ec-phone');
    var rel = row.querySelector('.ec-rel');
    var hName = row.querySelector('.ec-hidden-name');
    var hPhone = row.querySelector('.ec-hidden-phone');
    var hRel = row.querySelector('.ec-hidden-relationship');
    if (hName && name) hName.value = name.value.trim();
    if (hPhone && phone) hPhone.value = phone.value.trim();
    if (hRel && rel) hRel.value = rel.value.trim();
  }

  function bindEmergencyRow(row) {
    row.querySelectorAll('.ec-save-form, .ec-create-form').forEach(function (form) {
      form.addEventListener('submit', function (e) {
        syncRowToHidden(row);
        var name = row.querySelector('.ec-name');
        var phone = row.querySelector('.ec-phone');
        if (!name || !name.value.trim()) {
          e.preventDefault();
          alert('Bitte einen Namen eingeben.');
          return;
        }
        if (!phone || !phone.value.trim()) {
          e.preventDefault();
          alert('Bitte eine Telefonnummer eingeben.');
          return;
        }
      });
    });

    var cancel = row.querySelector('.ec-cancel-new');
    if (cancel) {
      cancel.addEventListener('click', function () {
        row.remove();
        updateEmergencyEmptyState();
      });
    }
  }

  function updateEmergencyEmptyState() {
    var list = document.getElementById('emergency-contacts-list');
    if (!list) return;
    var rows = list.querySelectorAll('.ec-row');
    var emptyMsg = document.getElementById('emergency-contacts-empty-msg');
    if (rows.length === 0) {
      if (!emptyMsg) {
        emptyMsg = document.createElement('p');
        emptyMsg.id = 'emergency-contacts-empty-msg';
        emptyMsg.className = 'hint emergency-contacts-empty';
        emptyMsg.textContent = 'Noch keine Notfallkontakte hinterlegt.';
        list.appendChild(emptyMsg);
      }
      emptyMsg.style.display = '';
    } else if (emptyMsg) {
      emptyMsg.style.display = 'none';
    }
  }

  onReady(function () {
    document.querySelectorAll('form[data-confirm]').forEach(function (form) {
      form.addEventListener('submit', function (e) {
        var msg = form.getAttribute('data-confirm');
        if (msg && !window.confirm(msg)) e.preventDefault();
      });
    });

    document.querySelectorAll('#emergency-contacts-list .ec-row').forEach(bindEmergencyRow);

    var addBtn = document.getElementById('btn-add-emergency');
    var template = document.getElementById('ec-row-template');
    var list = document.getElementById('emergency-contacts-list');
    if (addBtn && template && list) {
      addBtn.addEventListener('click', function () {
        var emptyMsg = document.getElementById('emergency-contacts-empty-msg');
        if (emptyMsg) emptyMsg.style.display = 'none';

        var fragment = template.content.cloneNode(true);
        var row = fragment.querySelector('.ec-row');
        if (!row) return;
        list.appendChild(fragment);
        bindEmergencyRow(row);
        var nameInput = row.querySelector('.ec-name');
        if (nameInput) nameInput.focus();
      });
    }

    updateEmergencyEmptyState();
  });
})();
