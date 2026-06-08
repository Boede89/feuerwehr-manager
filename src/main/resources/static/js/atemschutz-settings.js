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

  function initPersonSearch(modal) {
    if (!modal) return;
    var search = modal.querySelector('.atemschutz-person-search');
    var picker = modal.querySelector('.user-picker');
    var emptyHint = modal.querySelector('.atemschutz-person-search-empty');
    if (!search || !picker) return;
    search.addEventListener('input', function () {
      var query = search.value.trim().toLowerCase();
      var visible = 0;
      picker.querySelectorAll('.user-picker__item').forEach(function (item) {
        var haystack = (item.getAttribute('data-search') || '').toLowerCase();
        var match = !query || haystack.indexOf(query) !== -1;
        item.style.display = match ? '' : 'none';
        if (match) visible++;
      });
      if (emptyHint) {
        emptyHint.hidden = visible > 0;
      }
    });
  }

  document.querySelectorAll('.atemschutz-open-cc').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var modalId = btn.getAttribute('data-modal');
      var modal = document.getElementById(modalId);
      openOverlay(modal);
      if (modal) {
        var search = modal.querySelector('.atemschutz-person-search');
        if (search) {
          search.value = '';
          search.dispatchEvent(new Event('input'));
          setTimeout(function () { search.focus(); }, 50);
        }
      }
    });
  });

  document.querySelectorAll('.atemschutz-cc-modal').forEach(initPersonSearch);

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
