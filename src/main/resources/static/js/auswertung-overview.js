(function () {
  'use strict';

  var jsonEl = document.getElementById('auswertung-detail-rows-data');
  var modal = document.getElementById('auswertung-detail-modal');
  if (!jsonEl || !modal) {
    return;
  }

  var rows = [];
  try {
    rows = JSON.parse(jsonEl.getAttribute('data-json') || '[]');
  } catch (e) {
    rows = [];
  }

  var titleEl = document.getElementById('auswertung-detail-modal-title');
  var stichwortEl = document.getElementById('adm-stichwort');
  var datumEl = document.getElementById('adm-datum');
  var alarmEl = document.getElementById('adm-alarmzeit');
  var endeEl = document.getElementById('adm-ende');
  var leitungLabelEl = document.getElementById('adm-leitung-label');
  var leitungEl = document.getElementById('adm-leitung');
  var personenEl = document.getElementById('adm-personen');
  var personenTitleEl = document.getElementById('adm-personen-title');
  var paSection = document.getElementById('adm-pa-section');
  var paTitleEl = document.getElementById('adm-pa-title');
  var paEl = document.getElementById('adm-pa');
  var fahrzeugeEl = document.getElementById('adm-fahrzeuge');
  var fahrzeugeTitleEl = document.getElementById('adm-fahrzeuge-title');
  var openBtn = document.getElementById('adm-open-report');

  function esc(text) {
    return String(text == null ? '' : text)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function formatDatum(iso) {
    if (!iso) {
      return '—';
    }
    var parts = String(iso).split('-');
    if (parts.length !== 3) {
      return String(iso);
    }
    return parts[2] + '.' + parts[1] + '.' + parts[0];
  }

  function fillList(ul, items, emptyText, itemClass) {
    if (!ul) {
      return;
    }
    if (!items || !items.length) {
      ul.innerHTML = '<li class="auswertung-detail-modal__empty">' + esc(emptyText) + '</li>';
      return;
    }
    var cls = itemClass ? ' class="' + esc(itemClass) + '"' : '';
    ul.innerHTML = items.map(function (item) {
      return '<li' + cls + '>' + esc(item) + '</li>';
    }).join('');
  }

  function setSectionTitle(el, label, count) {
    if (!el) {
      return;
    }
    var n = Number.isFinite(count) ? count : 0;
    el.textContent = label + ' (' + n + ')';
  }

  function openModal(row) {
    if (!row) {
      return;
    }
    if (titleEl) {
      titleEl.textContent = row.stichwort || 'Details';
    }
    if (stichwortEl) {
      stichwortEl.textContent = row.stichwort || '—';
    }
    if (datumEl) {
      datumEl.textContent = formatDatum(row.datum);
    }
    if (alarmEl) {
      alarmEl.textContent = row.alarmzeit || '—';
    }
    if (endeEl) {
      endeEl.textContent = row.einsatzende || '—';
    }
    if (leitungLabelEl) {
      leitungLabelEl.textContent = row.leitungLabel || (row.kind === 'uebung' ? 'Ausbilder' : 'Einsatzleiter');
    }
    if (leitungEl) {
      leitungEl.textContent = row.leitung || '—';
    }
    var personen = row.personen || [];
    setSectionTitle(personenTitleEl, 'Personal', personen.length);
    fillList(personenEl, personen, 'Keine Personen', 'auswertung-person-item');
    var pa = row.paTraeger || [];
    if (paSection) {
      if (pa.length) {
        paSection.hidden = false;
        setSectionTitle(paTitleEl, 'PA-Träger', pa.length);
        fillList(paEl, pa, '', 'auswertung-person-item');
      } else {
        paSection.hidden = true;
        if (paEl) {
          paEl.innerHTML = '';
        }
      }
    }
    var fahrzeuge = row.fahrzeuge || [];
    setSectionTitle(fahrzeugeTitleEl, 'Fahrzeuge', fahrzeuge.length);
    fillList(fahrzeugeEl, fahrzeuge, 'Keine Fahrzeuge');
    if (openBtn) {
      openBtn.href = row.viewUrl || '#';
      openBtn.textContent = row.openButtonLabel || 'Öffnen';
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

  document.querySelectorAll('.auswertung-detail-row').forEach(function (tr) {
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

  modal.querySelectorAll('[data-auswertung-modal-close]').forEach(function (el) {
    el.addEventListener('click', closeModal);
  });

  document.addEventListener('keydown', function (ev) {
    if (ev.key === 'Escape' && modal.style.display === 'flex') {
      closeModal();
    }
  });
})();
