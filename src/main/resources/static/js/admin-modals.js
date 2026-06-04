(function () {
  function closeModal(overlay) {
    if (!overlay) return;
    overlay.classList.remove('active');
    if (!document.querySelector('.modal-overlay.active')) {
      document.body.classList.remove('modal-open');
    }
  }

  function openModal(id) {
    const overlay = document.getElementById(id);
    if (!overlay) return;
    overlay.classList.add('active');
    document.body.classList.add('modal-open');
    const focusable = overlay.querySelector(
      'input:not([type="hidden"]):not([disabled]), select, textarea, button[data-close-modal]');
    if (focusable && focusable.type !== 'button') {
      focusable.focus();
    }
  }

  function syncUnitRequirement(scopeRoot) {
    const roleSelect = scopeRoot.querySelector('[data-unit-role-select]');
    const unitField = scopeRoot.querySelector('[data-unit-field]');
    if (!roleSelect || !unitField) return;

    const role = roleSelect.value;
    const isSuperAdmin = role === 'SUPER_ADMIN';
    const unitSelect = unitField.querySelector('[data-unit-select]');
    const hintSuper = unitField.querySelector('[data-unit-hint-super]');
    const hintRequired = unitField.querySelector('[data-unit-hint-required]');

    if (unitSelect) {
      unitSelect.required = !isSuperAdmin;
      if (isSuperAdmin) {
        unitSelect.setCustomValidity('');
      }
    }
    if (hintSuper) hintSuper.hidden = !isSuperAdmin;
    if (hintRequired) hintRequired.hidden = isSuperAdmin;
  }

  function initUnitRoleHandlers() {
    document.querySelectorAll('[data-unit-role-select]').forEach((select) => {
      const root = select.closest('form') || select.closest('.modal');
      if (!root) return;
      select.addEventListener('change', () => syncUnitRequirement(root));
      syncUnitRequirement(root);
    });
  }

  document.querySelectorAll('[data-open-modal]').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      const modalId = btn.getAttribute('data-open-modal');
      openModal(modalId);

      if (modalId === 'modal-reset-pw') {
        const form = document.getElementById('form-reset-pw');
        const label = document.getElementById('reset-pw-username');
        const emailHint = document.getElementById('reset-pw-email');
        if (form && btn.dataset.resetUrl) {
          form.action = btn.dataset.resetUrl;
        }
        if (label && btn.dataset.username) {
          label.textContent = 'Benutzer: ' + btn.dataset.username;
        }
        if (emailHint) {
          const mail = btn.dataset.loginEmail;
          emailHint.textContent =
            mail && mail.trim() ? 'E-Mail: ' + mail : 'Keine E-Mail hinterlegt — Versand nicht möglich.';
        }
        const pw = document.getElementById('reset-pw-value');
        if (pw) pw.value = '';
      }

      if (modalId === 'modal-edit-unit') {
        const form = document.getElementById('form-edit-unit');
        if (!form) return;
        const idInput = form.querySelector('[name="unitId"]');
        const nameInput = form.querySelector('[name="name"]');
        const activeInput = form.querySelector('[name="active"]');
        if (idInput) idInput.value = btn.dataset.unitId || '';
        if (nameInput) nameInput.value = btn.dataset.unitName || '';
        if (activeInput) activeInput.checked = btn.dataset.unitActive === 'true';
        const title = document.getElementById('edit-unit-title');
        if (title && btn.dataset.unitName) {
          title.textContent = btn.dataset.unitName;
        }
      }

      if (modalId === 'modal-edit-user') {
        const userId = btn.dataset.userId;
        const form = document.getElementById('form-edit-user');
        const rfidForm = document.getElementById('form-edit-rfid');
        const title = document.getElementById('edit-user-title');
        if (form && userId) {
          form.action = form.action.replace(/\/users\/\d+/, '/users/' + userId);
          if (!form.action.includes('/users/' + userId)) {
            form.action = '/admin/users/' + userId;
          }
        }
        if (rfidForm && userId) {
          rfidForm.action = '/admin/users/' + userId + '/rfid';
        }
        if (title) title.textContent = btn.dataset.username || '—';
        const setVal = (id, val) => {
          const el = document.getElementById(id);
          if (el) el.value = val != null ? val : '';
        };
        setVal('editUsername', btn.dataset.username);
        setVal('editDisplayName', btn.dataset.displayName);
        setVal('editLoginEmail', btn.dataset.loginEmail || '');
        const roleEl = document.getElementById('editRole');
        if (roleEl && btn.dataset.role) roleEl.value = btn.dataset.role;
        const unitEl = document.getElementById('editUnitIdForm');
        if (unitEl) unitEl.value = btn.dataset.unitId || '';
        const activeEl = document.getElementById('editActive');
        if (activeEl) activeEl.checked = btn.dataset.active === 'true';
        const rfidList = document.getElementById('edit-user-rfid-list');
        const src = document.getElementById('edit-user-rfid-src-' + userId);
        if (rfidList && src) {
          rfidList.innerHTML = src.innerHTML;
        }
        syncUnitRequirement(document.getElementById('modal-edit-user') || form);
      }

      if (modalId === 'modal-new-user') {
        const form = document.getElementById('form-new-user');
        if (form) syncUnitRequirement(form);
      }
    });
  });

  document.querySelectorAll('[data-close-modal]').forEach((btn) => {
    btn.addEventListener('click', () => closeModal(btn.closest('.modal-overlay')));
  });

  document.querySelectorAll('.modal-overlay').forEach((overlay) => {
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) closeModal(overlay);
    });
  });

  document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape') return;
    document.querySelectorAll('.modal-overlay.active').forEach(closeModal);
  });

  initUnitRoleHandlers();
})();
