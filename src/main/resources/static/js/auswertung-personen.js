(function () {
  'use strict';

  var jsonEl = document.getElementById('auswertung-person-rows-data');
  var modal = document.getElementById('auswertung-person-modal');
  var tbody = document.getElementById('auswertung-personen-tbody');
  if (!jsonEl || !modal || !tbody) {
    return;
  }

  var rows = [];
  try {
    var raw = (jsonEl.textContent || jsonEl.getAttribute('data-json') || '').trim();
    rows = JSON.parse(raw || '[]');
  } catch (e) {
    rows = [];
  }

  var titleEl = document.getElementById('auswertung-person-modal-title');
  var nameEl = document.getElementById('apm-name');
  var dienstEl = document.getElementById('apm-dienst');
  var einsatzEl = document.getElementById('apm-einsatz');
  var diensteListEl = document.getElementById('apm-dienste');
  var einsaetzeListEl = document.getElementById('apm-einsaetze');
  var sortButtons = document.querySelectorAll('.auswertung-sort-btn');

  var sortKey = 'name';
  var sortDir = 'asc';

  function esc(text) {
    return String(text == null ? '' : text)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function fillTeilnahmen(ul, items, emptyText) {
    if (!ul) {
      return;
    }
    if (!items || !items.length) {
      ul.innerHTML = '<li class="auswertung-detail-modal__empty">' + esc(emptyText) + '</li>';
      return;
    }
    ul.innerHTML = items.map(function (item) {
      var date = item && item.date ? item.date : '—';
      var label = item && item.label ? item.label : '—';
      var pa = item && (item.pa === true || item.usesPa === true)
        ? ' <span class="auswertung-teilnahme-pa" title="PA getragen">PA</span>'
        : '';
      return '<li><span class="auswertung-teilnahme-date">' + esc(date) + '</span> · '
        + esc(label) + pa + '</li>';
    }).join('');
  }

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
    fillTeilnahmen(diensteListEl, row.dienste, 'Keine Dienste');
    fillTeilnahmen(einsaetzeListEl, row.einsaetze, 'Keine Einsätze');
    modal.style.display = 'flex';
    modal.setAttribute('aria-hidden', 'false');
    document.body.classList.add('modal-open');
  }

  function closeModal() {
    modal.style.display = 'none';
    modal.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('modal-open');
  }

  function compareRows(a, b) {
    var av;
    var bv;
    if (sortKey === 'dienstPct' || sortKey === 'einsatzPct') {
      av = Number(a[sortKey]) || 0;
      bv = Number(b[sortKey]) || 0;
      if (av !== bv) {
        return av < bv ? -1 : 1;
      }
      return String(a.name || '').localeCompare(String(b.name || ''), 'de', { sensitivity: 'base' });
    }
    av = String(a.name || '');
    bv = String(b.name || '');
    return av.localeCompare(bv, 'de', { sensitivity: 'base' });
  }

  function sortedRows() {
    var copy = rows.slice();
    copy.sort(function (a, b) {
      var cmp = compareRows(a, b);
      return sortDir === 'asc' ? cmp : -cmp;
    });
    return copy;
  }

  function bindRow(tr) {
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
  }

  function renderTable() {
    var ordered = sortedRows();
    tbody.innerHTML = ordered.map(function (row) {
      var originalIndex = rows.indexOf(row);
      return (
        '<tr class="auswertung-person-row" tabindex="0" role="button"' +
        ' data-row-index="' + originalIndex + '"' +
        ' aria-label="Details zu ' + esc(row.name || 'Person') + '">' +
        '<td><span class="auswertung-person-name">' + esc(row.name || '—') + '</span></td>' +
        '<td>' + esc(row.dienstbeteiligung || '—') + '</td>' +
        '<td>' + esc(row.einsatzbeteiligung || '—') + '</td>' +
        '</tr>'
      );
    }).join('');
    tbody.querySelectorAll('.auswertung-person-row').forEach(bindRow);
  }

  function updateSortButtons() {
    sortButtons.forEach(function (btn) {
      var key = btn.getAttribute('data-sort');
      if (key === sortKey) {
        btn.setAttribute('aria-sort', sortDir === 'asc' ? 'ascending' : 'descending');
      } else {
        btn.setAttribute('aria-sort', 'none');
      }
    });
  }

  sortButtons.forEach(function (btn) {
    btn.addEventListener('click', function () {
      var key = btn.getAttribute('data-sort') || 'name';
      if (sortKey === key) {
        sortDir = sortDir === 'asc' ? 'desc' : 'asc';
      } else {
        sortKey = key;
        sortDir = key === 'name' ? 'asc' : 'desc';
      }
      updateSortButtons();
      renderTable();
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

  updateSortButtons();
  tbody.querySelectorAll('.auswertung-person-row').forEach(bindRow);
})();
