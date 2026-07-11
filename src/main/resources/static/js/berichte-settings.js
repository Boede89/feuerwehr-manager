(function () {
  'use strict';

  function openOverlay(overlay) {
    if (!overlay) return;
    overlay.classList.add('active');
    document.body.classList.add('modal-open');
    overlay.setAttribute('aria-hidden', 'false');
  }

  function closeOverlay(overlay) {
    if (!overlay) return;
    overlay.classList.remove('active');
    if (!document.querySelector('.modal-overlay.active')) {
      document.body.classList.remove('modal-open');
    }
    overlay.setAttribute('aria-hidden', 'true');
  }

  function updateRecipientCount(modal) {
    if (!modal) return;
    var personCount = modal.querySelectorAll('input[type="checkbox"]:checked').length;
    var manualField = modal.querySelector('textarea[name$="ManualEmails"]');
    var manualCount = 0;
    if (manualField && manualField.value.trim()) {
      manualField.value.split(/[,;\n\r]+/).forEach(function (part) {
        if (part.trim()) manualCount++;
      });
    }
    var block = modal.closest('.berichte-email-settings-block');
    if (!block) return;
    var countEl = block.querySelector('.berichte-recipient-count');
    if (countEl) countEl.textContent = String(personCount + manualCount);
  }

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
      if (emptyHint) emptyHint.hidden = visible > 0;
    });
  }

  function syncDiveraPersonnelToggles() {
    var importCb = document.getElementById('importPersonnelFromDivera');
    var autoCb = document.getElementById('autoAssignDiveraPersonnelToAnwesenheit');
    var autoRow = document.getElementById('autoAssignDiveraRow');
    if (!importCb || !autoCb || !autoRow) return;
    if (!importCb.checked) {
      autoCb.checked = false;
      autoCb.disabled = true;
      autoRow.classList.add('toggle-row--disabled');
    } else {
      autoCb.disabled = false;
      autoRow.classList.remove('toggle-row--disabled');
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    var importCb = document.getElementById('importPersonnelFromDivera');
    if (importCb) {
      importCb.addEventListener('change', syncDiveraPersonnelToggles);
      syncDiveraPersonnelToggles();
    }

    document.querySelectorAll('.berichte-email-modal').forEach(function (modal) {
      initPersonSearch(modal);
      modal.querySelectorAll('input[type="checkbox"]').forEach(function (cb) {
        cb.addEventListener('change', function () { updateRecipientCount(modal); });
      });
      var manualField = modal.querySelector('textarea[name$="ManualEmails"]');
      if (manualField) {
        manualField.addEventListener('input', function () { updateRecipientCount(modal); });
      }
    });

    document.querySelectorAll('.berichte-open-recipients').forEach(function (btn) {
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

    document.querySelectorAll('.berichte-close-recipients').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var modal = btn.closest('.berichte-email-modal');
        if (modal) updateRecipientCount(modal);
        closeOverlay(modal);
      });
    });

    document.querySelectorAll('.berichte-email-modal').forEach(function (modal) {
      modal.addEventListener('click', function (e) {
        if (e.target === modal) {
          updateRecipientCount(modal);
          closeOverlay(modal);
        }
      });
    });
  });
})();
