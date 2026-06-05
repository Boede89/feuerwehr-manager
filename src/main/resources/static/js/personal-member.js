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

  var toggleAtt = document.getElementById('btn-toggle-attendance-form');
  var attForm = document.getElementById('add-attendance-form');
  var cancelAtt = document.getElementById('btn-cancel-attendance-form');
  if (toggleAtt && attForm) {
    toggleAtt.addEventListener('click', function () {
      attForm.style.display = attForm.style.display === 'none' ? 'block' : 'none';
    });
  }
  if (cancelAtt && attForm) {
    cancelAtt.addEventListener('click', function () {
      attForm.style.display = 'none';
    });
  }
})();
