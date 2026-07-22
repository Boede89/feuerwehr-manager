(function () {
  function openModal(id) {
    var overlay = document.getElementById(id);
    if (!overlay) return;
    overlay.classList.add('active');
    document.body.classList.add('modal-open');
  }

  function closeModal(overlay) {
    overlay.classList.remove('active');
    if (!document.querySelector('.modal-overlay.active')) {
      document.body.classList.remove('modal-open');
    }
  }

  document.querySelectorAll('[data-open-modal]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var modalId = btn.getAttribute('data-open-modal');
      var form = document.querySelector('#' + modalId + ' form');
      if (form && form.id === 'form-quali') {
        form.action = form.getAttribute('data-create-action') || form.action;
        document.getElementById('modal-quali-title').textContent = 'Qualifikation hinzufügen';
        form.reset();
      }
      if (form && form.id === 'form-equip') {
        form.action = form.getAttribute('data-create-action') || form.action;
        document.getElementById('modal-equip-title').textContent = 'Ausrüstung hinzufügen';
        form.reset();
      }
      if (form && form.id === 'form-honor') {
        form.action = form.getAttribute('data-create-action') || form.action;
        document.getElementById('modal-honor-title').textContent = 'Ehrung hinzufügen';
        form.reset();
      }
      if (form && form.id === 'form-course-completion') {
        form.action = form.getAttribute('data-create-action') || form.action;
        var title = document.getElementById('modal-course-completion-title');
        if (title) title.textContent = 'Lehrgang hinzufügen';
        form.reset();
        filterCourseOptions(false);
      }
      if (form && form.id === 'form-attendance') {
        form.action = form.getAttribute('data-create-action') || form.action;
        var attTitle = document.getElementById('modal-attendance-title');
        if (attTitle) attTitle.textContent = 'Teilnahme hinzufügen';
        form.reset();
      }
      if (form && form.id === 'form-ric') {
        form.reset();
      }
      openModal(modalId);
    });
  });

  document.querySelectorAll('[data-close-modal]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var overlay = btn.closest('.modal-overlay');
      if (overlay) closeModal(overlay);
    });
  });

  document.querySelectorAll('.modal-overlay').forEach(function (overlay) {
    overlay.addEventListener('click', function (e) {
      if (e.target === overlay) closeModal(overlay);
    });
  });

  document.querySelectorAll('[data-edit-quali]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var tr = btn.closest('tr');
      var form = document.getElementById('form-quali');
      if (!tr || !form) return;
      var base = form.getAttribute('data-create-action') || form.action;
      form.action = base.replace(/\/qualifications$/, '/qualifications/' + tr.getAttribute('data-id'));
      document.getElementById('modal-quali-title').textContent = 'Qualifikation bearbeiten';
      document.getElementById('q-name').value = tr.getAttribute('data-name') || '';
      document.getElementById('q-acquired').value = tr.getAttribute('data-acquired') || '';
      document.getElementById('q-expires').value = tr.getAttribute('data-expires') || '';
      document.getElementById('q-notes').value = tr.getAttribute('data-notes') || '';
      document.getElementById('q-health').checked = tr.getAttribute('data-health') === 'true';
      openModal('modal-quali');
    });
  });

  document.querySelectorAll('[data-edit-equip]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var tr = btn.closest('tr');
      var form = document.getElementById('form-equip');
      if (!tr || !form) return;
      var base = form.getAttribute('data-create-action') || form.action;
      form.action = base.replace(/\/equipment$/, '/equipment/' + tr.getAttribute('data-id'));
      document.getElementById('modal-equip-title').textContent = 'Ausrüstung bearbeiten';
      document.getElementById('e-type').value = tr.getAttribute('data-type') || 'PAGER';
      document.getElementById('e-identifier').value = tr.getAttribute('data-identifier') || '';
      document.getElementById('e-issued').value = tr.getAttribute('data-issued') || '';
      document.getElementById('e-expires').value = tr.getAttribute('data-expires') || '';
      document.getElementById('e-notes').value = tr.getAttribute('data-notes') || '';
      openModal('modal-equip');
    });
  });

  document.querySelectorAll('[data-edit-honor]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var tr = btn.closest('tr');
      var form = document.getElementById('form-honor');
      if (!tr || !form) return;
      var base = form.getAttribute('data-create-action') || form.action;
      form.action = base.replace(/\/honors$/, '/honors/' + tr.getAttribute('data-id'));
      document.getElementById('modal-honor-title').textContent = 'Ehrung bearbeiten';
      document.getElementById('h-name').value = tr.getAttribute('data-name') || '';
      document.getElementById('h-awarded').value = tr.getAttribute('data-awarded') || '';
      document.getElementById('h-status').value = tr.getAttribute('data-status') || 'aktiv';
      document.getElementById('h-notes').value = tr.getAttribute('data-notes') || '';
      openModal('modal-honor');
    });
  });

  var qualiForm = document.getElementById('form-quali');
  if (qualiForm) qualiForm.setAttribute('data-create-action', qualiForm.action);
  var equipForm = document.getElementById('form-equip');
  if (equipForm) equipForm.setAttribute('data-create-action', equipForm.action);
  var honorForm = document.getElementById('form-honor');
  if (honorForm) honorForm.setAttribute('data-create-action', honorForm.action);
  var courseForm = document.getElementById('form-course-completion');
  if (courseForm) courseForm.setAttribute('data-create-action', courseForm.action);
  var attendanceForm = document.getElementById('form-attendance');
  if (attendanceForm) attendanceForm.setAttribute('data-create-action', attendanceForm.action);

  document.querySelectorAll('[data-edit-attendance]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var tr = btn.closest('tr');
      var form = document.getElementById('form-attendance');
      if (!tr || !form) return;
      var base = form.getAttribute('data-create-action') || form.action;
      form.action = base.replace(/\/attendance$/, '/attendance/' + tr.getAttribute('data-id'));
      var attTitle = document.getElementById('modal-attendance-title');
      if (attTitle) attTitle.textContent = 'Teilnahme bearbeiten';
      document.getElementById('att-label').value = tr.getAttribute('data-service-label') || '';
      document.getElementById('att-type').value = tr.getAttribute('data-service-type') || '';
      document.getElementById('att-date').value = tr.getAttribute('data-service-date') || '';
      openModal('modal-attendance');
    });
  });

  function filterCourseOptions(editMode) {
    var select = document.getElementById('cc-course');
    if (!select) return;
    Array.prototype.forEach.call(select.options, function (opt) {
      if (!opt.value) return;
      var available = opt.getAttribute('data-available') === 'true';
      opt.hidden = !editMode && !available;
      opt.disabled = !editMode && !available;
    });
  }

  document.querySelectorAll('[data-edit-course-completion]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var tr = btn.closest('tr');
      var form = document.getElementById('form-course-completion');
      if (!tr || !form) return;
      var base = form.getAttribute('data-create-action') || form.action;
      form.action = base.replace(/\/course-completions$/, '/course-completions/' + tr.getAttribute('data-id'));
      var title = document.getElementById('modal-course-completion-title');
      if (title) title.textContent = 'Lehrgang bearbeiten';
      document.getElementById('cc-course').value = tr.getAttribute('data-course-id') || '';
      document.getElementById('cc-year').value = tr.getAttribute('data-year') || '';
      filterCourseOptions(true);
      openModal('modal-course-completion');
    });
  });

  // Bestätigung für form[data-confirm] übernimmt confirm-dialog.js (schönes Modal).

  var loginForm = document.getElementById('form-login-access');
  if (loginForm) {
    var allowLoginCheckbox = document.getElementById('allow-login');
    if (window.initLoginPasswordOptions && allowLoginCheckbox) {
      window.initLoginPasswordOptions(allowLoginCheckbox, loginForm);
    }
    loginForm.addEventListener('submit', function (e) {
      var allowLogin = document.getElementById('allow-login');
      var hadUser = loginForm.getAttribute('data-had-user') === 'true';
      var hasEmail = loginForm.getAttribute('data-has-email') === 'true';
      if (allowLogin && allowLogin.checked && !hadUser && !hasEmail) {
        e.preventDefault();
        window.alert('Bitte zuerst eine E-Mail-Adresse unter Kontaktdaten speichern.');
        return;
      }
      if (hadUser && allowLogin && !allowLogin.checked) {
        var msg = 'Das verknüpfte Benutzerkonto wird unwiderruflich gelöscht. Fortfahren?';
        if (!window.confirm(msg)) e.preventDefault();
      }
    });
  }

  (function initAttendanceTable() {
    var table = document.getElementById('attendance-table');
    var tbody = document.getElementById('attendance-tbody');
    var search = document.getElementById('attendance-search');
    var typeFilter = document.getElementById('attendance-type-filter');
    if (!table || !tbody) return;

    var headers = table.querySelectorAll('th[data-sort-key]');
    var sortState = { key: 'date', dir: -1 };

    function rows() {
      return Array.prototype.slice.call(tbody.querySelectorAll('.attendance-row'));
    }

    function applySort(key, forceDir) {
      if (key) {
        if (forceDir != null) {
          sortState.key = key;
          sortState.dir = forceDir;
        } else if (sortState.key === key) {
          sortState.dir *= -1;
        } else {
          sortState.key = key;
          sortState.dir = key === 'date' ? -1 : 1;
        }
      }
      var sorted = rows();
      sorted.sort(function (a, b) {
        var av = (a.getAttribute('data-sort-' + sortState.key) || '').toLowerCase();
        var bv = (b.getAttribute('data-sort-' + sortState.key) || '').toLowerCase();
        if (av < bv) return -1 * sortState.dir;
        if (av > bv) return 1 * sortState.dir;
        return 0;
      });
      sorted.forEach(function (r) {
        tbody.appendChild(r);
      });
      headers.forEach(function (h) {
        var active = h.getAttribute('data-sort-key') === sortState.key;
        h.classList.remove('table__th-sort--asc', 'table__th-sort--desc');
        h.setAttribute('aria-sort', active ? (sortState.dir === 1 ? 'ascending' : 'descending') : 'none');
        if (active) {
          h.classList.add(sortState.dir === 1 ? 'table__th-sort--asc' : 'table__th-sort--desc');
        }
      });
      applyFilters();
    }

    function applyFilters() {
      var q = search ? search.value.trim().toLowerCase() : '';
      var type = typeFilter ? typeFilter.value : '';
      rows().forEach(function (row) {
        var hay = (row.getAttribute('data-search') || '').toLowerCase();
        var rowType = row.getAttribute('data-type') || '';
        var matchSearch = !q || hay.indexOf(q) !== -1;
        var matchType = !type || rowType === type;
        row.style.display = matchSearch && matchType ? '' : 'none';
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

    if (search) search.addEventListener('input', applyFilters);
    if (typeFilter) typeFilter.addEventListener('change', applyFilters);

    applySort('date', -1);
  })();
})();
