(function () {
  function parsePerms(json) {
    if (!json) return [];
    try {
      return JSON.parse(json);
    } catch (e) {
      return [];
    }
  }

  function permSelected(cbValue, perms) {
    if (perms.indexOf(cbValue) >= 0) return true;
    var dot = cbValue.indexOf('.');
    if (dot > 0 && perms.indexOf(cbValue.substring(0, dot)) >= 0) return true;
    return false;
  }

  function setPermChecks(perms) {
    document.querySelectorAll('#unit-role-perms input[type="checkbox"]').forEach(function (cb) {
      cb.checked = permSelected(cb.value, perms);
    });
  }

  document.querySelectorAll('[data-open-modal="modal-unit-role"]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var form = document.getElementById('form-unit-role');
      var title = document.getElementById('modal-unit-role-title');
      var roleId = btn.getAttribute('data-role-id');
      var isSystem = btn.getAttribute('data-role-system') === 'true';
      var nameInput = document.getElementById('unit-role-name');
      var typeSelect = document.getElementById('unit-role-type');
      var nameGroup = document.getElementById('unit-role-name-group');
      var typeGroup = document.getElementById('unit-role-type-group');
      if (!form || !title) return;

      var createAction = form.getAttribute('data-create-action');
      var updateAction = form.getAttribute('data-update-action');
      var roleIdInput = document.getElementById('unit-role-id');
      if (roleId) {
        title.textContent = 'Rolle bearbeiten';
        if (updateAction) form.action = updateAction;
        if (roleIdInput) {
          roleIdInput.disabled = false;
          roleIdInput.value = roleId;
        }
        nameInput.value = btn.getAttribute('data-role-name') || '';
        typeSelect.value = btn.getAttribute('data-role-type') || 'DIENSTGRAD';
        setPermChecks(parsePerms(btn.getAttribute('data-role-perms')));
        if (nameInput) nameInput.readOnly = isSystem;
        if (typeSelect) typeSelect.disabled = isSystem;
        if (nameGroup) nameGroup.hidden = isSystem;
        if (typeGroup) typeGroup.hidden = isSystem;
      } else {
        title.textContent = 'Rolle anlegen';
        if (createAction) form.action = createAction;
        if (roleIdInput) {
          roleIdInput.value = '';
          roleIdInput.disabled = true;
        }
        nameInput.value = '';
        typeSelect.value = 'DIENSTGRAD';
        setPermChecks([]);
        if (nameInput) {
          nameInput.readOnly = false;
          nameInput.value = '';
        }
        if (typeSelect) {
          typeSelect.disabled = false;
          typeSelect.value = 'DIENSTGRAD';
        }
        if (nameGroup) nameGroup.hidden = false;
        if (typeGroup) typeGroup.hidden = false;
      }
    });
  });

  document.querySelectorAll('[data-open-modal="modal-vehicle-edit"]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      document.getElementById('edit-vehicle-id').value = btn.getAttribute('data-id') || '';
      document.getElementById('edit-vehicle-name').value = btn.getAttribute('data-name') || '';
      document.getElementById('edit-vehicle-desc').value = btn.getAttribute('data-desc') || '';
      document.getElementById('edit-vehicle-active').checked = btn.getAttribute('data-active') === 'true';
    });
  });

  document.querySelectorAll('[data-open-modal="modal-room-edit"]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      document.getElementById('edit-room-id').value = btn.getAttribute('data-id') || '';
      document.getElementById('edit-room-name').value = btn.getAttribute('data-name') || '';
      document.getElementById('edit-room-desc').value = btn.getAttribute('data-desc') || '';
      document.getElementById('edit-room-active').checked = btn.getAttribute('data-active') === 'true';
    });
  });

  document.querySelectorAll('[data-open-modal="modal-smtp-edit"]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var set = function (id, val) {
        var el = document.getElementById(id);
        if (el) el.value = val != null ? val : '';
      };
      set('edit-smtp-id', btn.getAttribute('data-id'));
      set('edit-smtp-label', btn.getAttribute('data-label'));
      set('edit-smtp-host', btn.getAttribute('data-host'));
      set('edit-smtp-port', btn.getAttribute('data-port') || '587');
      set('edit-smtp-user', btn.getAttribute('data-user'));
      set('edit-smtp-from', btn.getAttribute('data-from'));
      set('edit-smtp-from-name', btn.getAttribute('data-from-name'));
      var enc = document.getElementById('edit-smtp-enc');
      if (enc) enc.value = btn.getAttribute('data-enc') || 'TLS';
      var hint = document.getElementById('edit-smtp-pw-hint');
      if (hint) hint.hidden = btn.getAttribute('data-pw-config') !== 'true';
    });
  });

  (function scrollToEquipmentPanel() {
    var panel = document.getElementById('vehicle-equipment-panel');
    if (panel && window.location.search.indexOf('vehicle=') >= 0) {
      panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  })();

  document.querySelectorAll('[data-open-modal="modal-calendar-edit"]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var set = function (id, val) {
        var el = document.getElementById(id);
        if (el) el.value = val != null ? val : '';
      };
      set('edit-calendar-id', btn.getAttribute('data-id'));
      set('edit-calendar-label', btn.getAttribute('data-label'));
      set('edit-calendar-url', btn.getAttribute('data-url'));
      set('edit-calendar-cal-id', btn.getAttribute('data-cal-id'));
      var enabled = document.getElementById('edit-calendar-enabled');
      if (enabled) enabled.checked = btn.getAttribute('data-enabled') === 'true';
      var hint = document.getElementById('edit-calendar-json-hint');
      if (hint) hint.hidden = btn.getAttribute('data-json-config') !== 'true';
    });
  });
})();
