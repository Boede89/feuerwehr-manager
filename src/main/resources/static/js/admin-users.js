(function () {
  var meta = document.getElementById('functions-modal-meta');
  if (!meta) return;

  var scope = meta.getAttribute('data-scope') || 'einheit';
  var unit = meta.getAttribute('data-unit') || '';
  var csrfParam = meta.getAttribute('data-csrf-param');
  var csrfToken = meta.getAttribute('data-csrf-token');
  var activeUserId = null;

  function syncDienstgradVisibility(selectEl) {
    var group = document.getElementById('adminDienstgradGroup');
    if (!group || !selectEl) return;
    var isUser = selectEl.value === 'USER';
    group.hidden = !isUser;
    var dg = document.getElementById('adminDienstgrad');
    if (dg) dg.disabled = !isUser;
  }

  document.querySelectorAll('[data-dienstgrad-toggle]').forEach(function (sel) {
    sel.addEventListener('change', function () {
      syncDienstgradVisibility(sel);
    });
    syncDienstgradVisibility(sel);
  });

  function openFunctionsModal(userId, username) {
    activeUserId = userId;
    var modal = document.getElementById('modal-functions');
    var title = document.getElementById('modal-functions-username');
    if (!modal) return;
    if (title) title.textContent = username || '—';

    var assigned = {};
    var src = document.getElementById('user-functions-src-' + userId);
    if (src) {
      src.querySelectorAll('.user-fn-assigned').forEach(function (el) {
        var id = el.getAttribute('data-role-id');
        if (id) assigned[id] = true;
      });
    }

    modal.querySelectorAll('.fn-check').forEach(function (cb) {
      var roleId = cb.getAttribute('data-role-id');
      cb.checked = !!assigned[roleId];
      cb.onchange = function () {
        submitFunctionChange(userId, roleId, cb.checked, cb);
      };
    });

    modal.classList.add('active');
    document.body.classList.add('modal-open');
  }

  function submitFunctionChange(userId, roleId, assign, checkbox) {
    var form = document.createElement('form');
    form.method = 'post';
    form.action = assign
      ? '/admin/users/' + userId + '/functions/assign'
      : '/admin/users/' + userId + '/functions/remove';
    if (csrfParam && csrfToken) {
      var csrf = document.createElement('input');
      csrf.type = 'hidden';
      csrf.name = csrfParam;
      csrf.value = csrfToken;
      form.appendChild(csrf);
    }
    var scopeInput = document.createElement('input');
    scopeInput.type = 'hidden';
    scopeInput.name = 'scope';
    scopeInput.value = scope;
    form.appendChild(scopeInput);
    if (unit) {
      var unitInput = document.createElement('input');
      unitInput.type = 'hidden';
      unitInput.name = 'unit';
      unitInput.value = unit;
      form.appendChild(unitInput);
    }
    var roleInput = document.createElement('input');
    roleInput.type = 'hidden';
    roleInput.name = 'roleId';
    roleInput.value = roleId;
    form.appendChild(roleInput);
    document.body.appendChild(form);
    form.submit();
  }

  document.querySelectorAll('[data-open-functions-modal]').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      openFunctionsModal(btn.getAttribute('data-user-id'), btn.getAttribute('data-username'));
    });
  });
})();
