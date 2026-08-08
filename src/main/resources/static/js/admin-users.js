(function () {
  var meta = document.getElementById('functions-modal-meta');
  if (!meta) return;

  var permissionsUserId = null;

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

  function submitDienstgradChange(selectEl) {
    var userId = selectEl.getAttribute('data-user-id');
    if (!userId) return;
    var previous = selectEl.getAttribute('data-previous-value');
    if (previous === null || previous === undefined) {
      previous = '';
    }
    var headers = {
      'Content-Type': 'application/x-www-form-urlencoded',
      'X-Requested-With': 'XMLHttpRequest',
    };
    var csrf = getCsrfToken();
    if (csrf) {
      headers['X-XSRF-TOKEN'] = csrf;
    }
    var body = new URLSearchParams();
    body.set('dienstgradRoleId', selectEl.value || '');
    selectEl.disabled = true;
    fetch('/admin/users/' + encodeURIComponent(userId) + '/dienstgrad', {
      method: 'POST',
      headers: headers,
      body: body,
      credentials: 'same-origin',
    })
      .then(function (res) {
        if (!res.ok) {
          return res.json().then(function (data) {
            throw new Error(data.message || 'Dienstgrad konnte nicht gespeichert werden');
          });
        }
        return res.json();
      })
      .then(function (data) {
        selectEl.setAttribute('data-previous-value', selectEl.value || '');
        if (typeof window.toast === 'function') {
          window.toast(data.message || 'Dienstgrad gespeichert');
        }
      })
      .catch(function (err) {
        selectEl.value = previous;
        if (typeof window.toast === 'function') {
          window.toast(err.message || 'Fehler', 'error');
        }
      })
      .finally(function () {
        selectEl.disabled = false;
      });
  }

  document.querySelectorAll('[data-dienstgrad-select]').forEach(function (sel) {
    sel.setAttribute('data-previous-value', sel.value || '');
    sel.addEventListener('change', function () {
      submitDienstgradChange(sel);
    });
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

  function esc(text) {
    var div = document.createElement('div');
    div.textContent = text == null ? '' : String(text);
    return div.innerHTML;
  }

  function renderPermissionsMatrix(options) {
    var container = document.getElementById('permissions-matrix');
    if (!container) return;
    if (!options || !options.length) {
      container.innerHTML = '<p class="hint">Keine Modulrechte verfügbar.</p>';
      return;
    }
    var rows = options.map(function (opt) {
      var effect = opt.effect || '';
      var fromRole = !!opt.fromRole;
      var roleLabel = fromRole ? 'erlaubt' : 'kein Recht';
      var roleClass = fromRole
        ? 'admin-permissions-matrix__role admin-permissions-matrix__role--allow'
        : 'admin-permissions-matrix__role admin-permissions-matrix__role--deny';
      return '<div class="admin-permissions-matrix__row" data-permission="' + esc(opt.value) + '">' +
        '<div class="admin-permissions-matrix__label">' +
        '<strong>' + esc(opt.label) + '</strong>' +
        '<span class="' + roleClass + '">Rolle: ' + roleLabel + '</span></div>' +
        '<select class="field field--sm permission-effect-select">' +
        '<option value=""' + (effect === '' ? ' selected' : '') + '>Wie Rolle (' + roleLabel + ')</option>' +
        '<option value="GRANT"' + (effect === 'GRANT' ? ' selected' : '') + '>Zusätzlich erlauben</option>' +
        '<option value="DENY"' + (effect === 'DENY' ? ' selected' : '') + '>Entziehen</option>' +
        '</select></div>';
    });
    container.innerHTML = rows.join('');
  }

  function openPermissionsModal(userId, username) {
    var modal = document.getElementById('modal-permissions');
    var title = document.getElementById('modal-permissions-username');
    var loading = document.getElementById('permissions-matrix-loading');
    var error = document.getElementById('permissions-matrix-error');
    var matrix = document.getElementById('permissions-matrix');
    if (!modal) return;
    permissionsUserId = userId;
    if (title) title.textContent = username || '—';
    if (error) {
      error.hidden = true;
      error.textContent = '';
    }
    if (matrix) matrix.innerHTML = '';
    if (loading) loading.hidden = false;
    modal.classList.add('active');
    document.body.classList.add('modal-open');

    fetch('/admin/users/' + encodeURIComponent(userId) + '/permissions', {
      credentials: 'same-origin',
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    })
      .then(function (res) {
        if (!res.ok) {
          return res.json().then(function (data) {
            throw new Error(data.message || 'Rechte konnten nicht geladen werden');
          });
        }
        return res.json();
      })
      .then(function (data) {
        if (loading) loading.hidden = true;
        renderPermissionsMatrix(data.options || []);
      })
      .catch(function (err) {
        if (loading) loading.hidden = true;
        if (error) {
          error.hidden = false;
          error.textContent = err.message || 'Fehler beim Laden';
        }
      });
  }

  function savePermissions() {
    if (!permissionsUserId) return;
    var matrix = document.getElementById('permissions-matrix');
    var saveBtn = document.getElementById('permissions-save-btn');
    if (!matrix) return;
    var payload = {};
    matrix.querySelectorAll('.admin-permissions-matrix__row').forEach(function (row) {
      var key = row.getAttribute('data-permission');
      var select = row.querySelector('.permission-effect-select');
      if (key && select) {
        payload[key] = select.value || '';
      }
    });
    var headers = {
      'Content-Type': 'application/json',
      'X-Requested-With': 'XMLHttpRequest',
    };
    var csrf = getCsrfToken();
    if (csrf) {
      headers['X-XSRF-TOKEN'] = csrf;
    }
    if (saveBtn) saveBtn.disabled = true;
    fetch('/admin/users/' + encodeURIComponent(permissionsUserId) + '/permissions', {
      method: 'POST',
      headers: headers,
      body: JSON.stringify(payload),
      credentials: 'same-origin',
    })
      .then(function (res) {
        if (!res.ok) {
          return res.json().then(function (data) {
            throw new Error(data.message || 'Speichern fehlgeschlagen');
          });
        }
        return res.json();
      })
      .then(function (data) {
        if (typeof window.toast === 'function') {
          window.toast(data.message || 'Individuelle Rechte gespeichert');
        }
        var modal = document.getElementById('modal-permissions');
        if (modal) modal.classList.remove('active');
        document.body.classList.remove('modal-open');
      })
      .catch(function (err) {
        if (typeof window.toast === 'function') {
          window.toast(err.message || 'Fehler', 'error');
        }
      })
      .finally(function () {
        if (saveBtn) saveBtn.disabled = false;
      });
  }

  document.querySelectorAll('[data-open-functions-modal]').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      openFunctionsModal(btn.getAttribute('data-user-id'), btn.getAttribute('data-username'));
    });
  });

  document.querySelectorAll('[data-open-permissions-modal]').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      openPermissionsModal(btn.getAttribute('data-user-id'), btn.getAttribute('data-username'));
    });
  });

  var saveBtn = document.getElementById('permissions-save-btn');
  if (saveBtn) {
    saveBtn.addEventListener('click', function (e) {
      e.preventDefault();
      savePermissions();
    });
  }
})();
