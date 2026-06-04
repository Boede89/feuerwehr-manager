(function () {
  function onReady(fn) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', fn);
    } else {
      fn();
    }
  }

  function reopenModalIfRequested(openModalKey, modalId, focusElId) {
    var urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('openModal') !== openModalKey) return;
    var overlay = document.getElementById(modalId);
    if (overlay) {
      overlay.classList.add('active');
      document.body.classList.add('modal-open');
      if (focusElId) {
        var focusEl = document.getElementById(focusElId);
        if (focusEl) focusEl.focus();
      }
    }
    urlParams.delete('openModal');
    var qs = urlParams.toString();
    window.history.replaceState(
      null,
      '',
      window.location.pathname + (qs ? '?' + qs : '') + window.location.hash
    );
  }

  function initTableSort(tableId, tbodyId, rowClass) {
    var table = document.getElementById(tableId);
    var tbody = document.getElementById(tbodyId);
    if (!table || !tbody) return;
    var headers = table.querySelectorAll('th[data-sort-key]');
    var sortState = { key: null, dir: 1 };

    function applySort(key) {
      if (sortState.key === key) {
        sortState.dir *= -1;
      } else {
        sortState.key = key;
        sortState.dir = 1;
      }
      var rows = Array.prototype.slice.call(tbody.querySelectorAll('.' + rowClass));
      rows.sort(function (a, b) {
        var av = (a.getAttribute('data-sort-' + key) || '').toLowerCase();
        var bv = (b.getAttribute('data-sort-' + key) || '').toLowerCase();
        if (av < bv) return -1 * sortState.dir;
        if (av > bv) return 1 * sortState.dir;
        return 0;
      });
      rows.forEach(function (r) {
        tbody.appendChild(r);
      });
      headers.forEach(function (h) {
        var active = h.getAttribute('data-sort-key') === key;
        h.classList.remove('table__th-sort--asc', 'table__th-sort--desc');
        h.setAttribute('aria-sort', active ? (sortState.dir === 1 ? 'ascending' : 'descending') : 'none');
        if (active) {
          h.classList.add(sortState.dir === 1 ? 'table__th-sort--asc' : 'table__th-sort--desc');
        }
      });
    }

    headers.forEach(function (th) {
      function activate() {
        applySort(th.getAttribute('data-sort-key'));
      }
      th.addEventListener('click', activate);
      th.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          activate();
        }
      });
    });
  }

  onReady(function () {
    document.querySelectorAll('form[data-confirm]').forEach(function (form) {
      form.addEventListener('submit', function (e) {
        var msg = form.getAttribute('data-confirm');
        if (msg && !window.confirm(msg)) e.preventDefault();
      });
    });

    initTableSort('qualification-table', 'qualification-tbody', 'qualification-row');
    initTableSort('course-table', 'course-tbody', 'course-row');

    document.querySelectorAll('[data-open-modal="modal-qualification-new"]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var input = document.getElementById('qualNameNew');
        if (input) input.value = '';
      });
    });

    document.querySelectorAll('[data-open-modal="modal-course-new"]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var name = document.getElementById('courseNameNew');
        var qual = document.getElementById('courseQualNew');
        if (name) name.value = '';
        if (qual) qual.value = '';
      });
    });

    document.querySelectorAll('[data-open-modal="modal-qualification-edit"]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        document.getElementById('edit-qualification-id').value = btn.getAttribute('data-id') || '';
        document.getElementById('qualNameEdit').value = btn.getAttribute('data-name') || '';
        document.getElementById('qualActiveEdit').checked = btn.getAttribute('data-active') === 'true';
      });
    });

    document.querySelectorAll('[data-open-modal="modal-course-edit"]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        document.getElementById('edit-course-id').value = btn.getAttribute('data-id') || '';
        document.getElementById('courseNameEdit').value = btn.getAttribute('data-name') || '';
        document.getElementById('courseActiveEdit').checked = btn.getAttribute('data-active') === 'true';
        var qualId = btn.getAttribute('data-qualification-id') || '';
        var sel = document.getElementById('courseQualEdit');
        if (sel) sel.value = qualId;
      });
    });

    reopenModalIfRequested('qualification-new', 'modal-qualification-new', 'qualNameNew');
    reopenModalIfRequested('course-new', 'modal-course-new', 'courseNameNew');
  });
})();
