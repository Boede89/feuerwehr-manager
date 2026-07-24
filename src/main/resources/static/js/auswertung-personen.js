(function () {
  'use strict';

  var jsonEl = document.getElementById('auswertung-person-rows-data');
  var modal = document.getElementById('auswertung-person-modal');
  if (!jsonEl || !modal) {
    return;
  }

  var rows = [];
  try {
    rows = JSON.parse(jsonEl.getAttribute('data-json') || '[]');
  } catch (e) {
    rows = [];
  }

  var titleEl = document.getElementById('auswertung-person-modal-title');
  var nameEl = document.getElementById('apm-name');
  var dienstEl = document.getElementById('apm-dienst');
  var einsatzEl = document.getElementById('apm-einsatz');

  function openModal(row) {
    if (!row) {
      return;
    }
    if (titleEl) {
      titleEl.textContent = row.name || 'Person';
    }
    if (nameEl) {
      nameEl.textContent = row.name || '—';
    }
    if (dienstEl) {
      dienstEl.textContent = row.dienstbeteiligung || '—';
    }
    if (einsatzEl) {
      einsatzEl.textContent = row.einsatzbeteiligung || '—';
    }
    modal.style.display = 'flex';
    modal.setAttribute('aria-hidden', 'false');
    document.body.classList.add('modal-open');
  }

  function closeModal() {
    modal.style.display = 'none';
    modal.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('modal-open');
  }

  document.querySelectorAll('.auswertung-person-row').forEach(function (tr) {
    function activate() {
      var idx = Number(tr.getAttribute('data-row-index'));
      if (!Number.isFinite(idx) || idx < 0 || idx >= rows.length) {
        return;
      }
      openModal(rows[idx]);
    }
    tr.addEventListener('click', activate);
    tr.addEventListener('keydown', function (ev) {
      if (ev.key === 'Enter' || ev.key === ' ') {
        ev.preventDefault();
        activate();
      }
    });
  });

  modal.querySelectorAll('[data-auswertung-person-modal-close]').forEach(function (el) {
    el.addEventListener('click', closeModal);
  });

  document.addEventListener('keydown', function (ev) {
    if (ev.key === 'Escape' && modal.style.display === 'flex') {
      closeModal();
    }
  });
})();
