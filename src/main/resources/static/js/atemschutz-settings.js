(function () {
  var page = document.querySelector('.atemschutz-settings-page');
  if (!page) return;

  function openOverlay(overlay) {
    if (!overlay) return;
    overlay.classList.add('active');
    document.body.classList.add('modal-open');
  }

  function closeOverlay(overlay) {
    if (!overlay) return;
    overlay.classList.remove('active');
    if (!document.querySelector('.modal-overlay.active')) {
      document.body.classList.remove('modal-open');
    }
  }

  function updateCcCount(modal) {
    if (!modal) return;
    var checked = modal.querySelectorAll('input[type="checkbox"]:checked').length;
    var section = modal.closest('.atemschutz-notify-section');
    if (!section) return;
    var btn = section.querySelector('.atemschutz-open-cc');
    if (!btn) return;
    var countEl = btn.querySelector('.atemschutz-cc-count');
    if (countEl) countEl.textContent = String(checked);
  }

  var instructorsBtn = document.getElementById('open-instructors-modal');
  var instructorsModal = document.getElementById('modal-instructors');
  if (instructorsBtn && instructorsModal) {
    instructorsBtn.addEventListener('click', function () {
      openOverlay(instructorsModal);
    });
  }

  document.querySelectorAll('[data-close-instructors]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      closeOverlay(instructorsModal);
    });
  });

  document.querySelectorAll('.atemschutz-open-cc').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var modalId = btn.getAttribute('data-modal');
      openOverlay(document.getElementById(modalId));
    });
  });

  document.querySelectorAll('.atemschutz-close-cc').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var modal = btn.closest('.atemschutz-cc-modal');
      updateCcCount(modal);
      closeOverlay(modal);
    });
  });

  document.querySelectorAll('.atemschutz-cc-modal').forEach(function (overlay) {
    overlay.addEventListener('click', function (e) {
      if (e.target === overlay) {
        updateCcCount(overlay);
        closeOverlay(overlay);
      }
    });
  });

  document.querySelectorAll('.atemschutz-toggle-template').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var targetId = btn.getAttribute('data-target');
      var panel = document.getElementById(targetId);
      if (!panel) return;
      var open = panel.hasAttribute('hidden');
      if (open) {
        panel.removeAttribute('hidden');
        btn.classList.add('btn--primary');
        btn.textContent = 'Vorlage ausblenden';
      } else {
        panel.setAttribute('hidden', 'hidden');
        btn.classList.remove('btn--primary');
        btn.textContent = 'E-Mail-Vorlage';
      }
    });
  });

  if (instructorsModal) {
    instructorsModal.addEventListener('click', function (e) {
      if (e.target === instructorsModal) closeOverlay(instructorsModal);
    });
  }
})();
