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
    const focusable = overlay.querySelector('input:not([type="hidden"]), select, textarea, button');
    if (focusable) {
      focusable.focus();
    }
  }

  document.querySelectorAll('[data-open-modal]').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      const modalId = btn.getAttribute('data-open-modal');
      openModal(modalId);

      if (modalId === 'modal-reset-pw') {
        const form = document.getElementById('form-reset-pw');
        const label = document.getElementById('reset-pw-username');
        if (form && btn.dataset.resetUrl) {
          form.action = btn.dataset.resetUrl;
        }
        if (label && btn.dataset.username) {
          label.textContent = 'Benutzer: ' + btn.dataset.username;
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
})();
