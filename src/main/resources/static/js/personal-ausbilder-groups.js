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

  function resetMemberCheckboxes() {
    document.querySelectorAll('#form-instructor-group input[name="personIds"]').forEach(function (cb) {
      cb.checked = false;
    });
  }

  function setMemberCheckboxes(memberIds) {
    resetMemberCheckboxes();
    if (!memberIds) return;
    memberIds.split(',').forEach(function (id) {
      id = id.trim();
      if (!id) return;
      var cb = document.querySelector('#form-instructor-group input[name="personIds"][value="' + id + '"]');
      if (cb) cb.checked = true;
    });
  }

  function prepareCreateForm() {
    var form = document.getElementById('form-instructor-group');
    var title = document.getElementById('modal-instructor-group-title');
    if (!form) return;
    form.action = form.getAttribute('data-create-action') || form.action;
    if (title) title.textContent = 'Ausbildergruppe anlegen';
    var themaInput = document.getElementById('instructor-group-thema');
    if (themaInput) themaInput.value = '';
    resetMemberCheckboxes();
  }

  function prepareEditForm(row) {
    var form = document.getElementById('form-instructor-group');
    var title = document.getElementById('modal-instructor-group-title');
    if (!form || !row) return;
    var groupId = row.getAttribute('data-id');
    var base = form.getAttribute('data-create-action') || '/personal/ausbilder-groups';
    form.action = base.replace(/\/ausbilder-groups\/?$/, '/ausbilder-groups/' + groupId);
    if (title) title.textContent = 'Ausbildergruppe bearbeiten';
    var themaInput = document.getElementById('instructor-group-thema');
    if (themaInput) themaInput.value = row.getAttribute('data-thema') || '';
    setMemberCheckboxes(row.getAttribute('data-member-ids'));
  }

  document.querySelectorAll('[data-open-modal="modal-instructor-group-form"]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      prepareCreateForm();
      openModal('modal-instructor-group-form');
    });
  });

  document.querySelectorAll('[data-edit-instructor-group]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var row = btn.closest('.instructor-group-row');
      prepareEditForm(row);
      openModal('modal-instructor-group-form');
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
})();
