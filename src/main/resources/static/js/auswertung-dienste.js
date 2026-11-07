(function () {
  'use strict';

  var form = document.getElementById('auswertung-dienste-filter-form');
  if (!form) return;

  form.querySelectorAll('[data-dienste-filter]').forEach(function (input) {
    input.addEventListener('change', function () {
      var chip = input.closest('.auswertung-dienste-chip');
      if (chip) {
        chip.classList.toggle('is-active', input.checked);
      }
      form.submit();
    });
  });

  var table = document.getElementById('auswertung-stunden-table');
  if (!table) {
    return;
  }
  var tbody = table.querySelector('tbody');
  var sortButtons = table.querySelectorAll('.auswertung-sort-btn');
  if (!tbody || !sortButtons.length) {
    return;
  }

  var sortKey = 'summe';
  var sortDir = 'desc';

  function compareRows(a, b) {
    if (sortKey === 'name') {
      var nameCmp = (a.getAttribute('data-name') || '').localeCompare(
        b.getAttribute('data-name') || '',
        'de',
        { sensitivity: 'base' }
      );
      return sortDir === 'asc' ? nameCmp : -nameCmp;
    }
    var av = Number(a.getAttribute('data-' + sortKey) || 0);
    var bv = Number(b.getAttribute('data-' + sortKey) || 0);
    if (av !== bv) {
      return sortDir === 'asc' ? av - bv : bv - av;
    }
    return (a.getAttribute('data-name') || '').localeCompare(
      b.getAttribute('data-name') || '',
      'de',
      { sensitivity: 'base' }
    );
  }

  function applySort() {
    var rows = Array.prototype.slice.call(tbody.querySelectorAll('[data-stunden-row]'));
    rows.sort(compareRows);
    rows.forEach(function (row) {
      tbody.appendChild(row);
    });
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
      applySort();
    });
  });
})();
