(function () {
  var meta = document.getElementById('functions-modal-meta');
  if (!meta) return;

  function getCsrfToken() {
    var fromMeta = meta.getAttribute('data-csrf-token');
    if (fromMeta) return fromMeta;
    var match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : '';
  }

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

  function updateAssignedInDom(userId, roleId, assign) {
    var src = document.getElementById('user-functions-src-' + userId);
    if (!src) return;
    if (assign) {
      var exists = src.querySelector('.user-fn-assigned[data-role-id="' + roleId + '"]');
      if (!exists) {
        var span = document.createElement('span');
        span.className = 'user-fn-assigned';
        span.setAttribute('data-role-id', roleId);
        src.appendChild(span);
      }
    } else {
      src.querySelectorAll('.user-fn-assigned').forEach(function (el) {
        if (el.getAttribute('data-role-id') === String(roleId)) {
          el.remove();
        }
      });
    }
    var rowBtn = document.querySelector('[data-open-functions-modal][data-user-id="' + userId + '"]');
    if (!rowBtn) return;
    var count = src.querySelectorAll('.user-fn-assigned').length;
    var sibling = rowBtn.nextElementSibling;
    if (count > 0) {
      if (sibling && sibling.classList.contains('user-fn-count')) {
        sibling.textContent = '(' + count + ')';
      } else {
        var badge = document.createElement('span');
        badge.className = 'text-muted text-xs user-fn-count';
        badge.textContent = '(' + count + ')';
        rowBtn.parentNode.insertBefore(badge, rowBtn.nextSibling);
      }
    } else if (sibling && sibling.classList.contains('user-fn-count')) {
      sibling.remove();
    }
  }

  function openFunctionsModal(userId, username) {
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
    var url = assign
      ? '/admin/users/' + userId + '/functions/assign'
      : '/admin/users/' + userId + '/functions/remove';
    var headers = {
      'Content-Type': 'application/x-www-form-urlencoded',
      'X-Requested-With': 'XMLHttpRequest',
    };
    var csrf = getCsrfToken();
    if (csrf) {
      headers['X-XSRF-TOKEN'] = csrf;
    }
    var body = new URLSearchParams();
    body.set('roleId', roleId);

    fetch(url, { method: 'POST', headers: headers, body: body, credentials: 'same-origin' })
      .then(function (res) {
        if (!res.ok) {
          return res.json().then(function (data) {
            throw new Error(data.message || data.error || 'Fehler beim Speichern');
          });
        }
        return res.json();
      })
      .then(function (data) {
        updateAssignedInDom(userId, roleId, assign);
        if (typeof window.toast === 'function') {
          window.toast(data.message || (assign ? 'Funktion zugewiesen' : 'Funktion entfernt'));
        }
      })
      .catch(function (err) {
        checkbox.checked = !assign;
        if (typeof window.toast === 'function') {
          window.toast(err.message || 'Fehler', 'error');
        }
      });
  }

  document.querySelectorAll('[data-open-functions-modal]').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      openFunctionsModal(btn.getAttribute('data-user-id'), btn.getAttribute('data-username'));
    });
  });
})();
