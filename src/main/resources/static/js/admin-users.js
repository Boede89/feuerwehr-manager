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

  function initSelfRegistrationToggle() {
    var form = document.getElementById('self-reg-toggle-form');
    var checkbox = document.getElementById('self-reg-toggle-checkbox');
    var hidden = document.getElementById('self-reg-enabled-param');
    if (!form || !checkbox || !hidden) return;
    checkbox.addEventListener('change', function () {
      hidden.value = checkbox.checked ? 'true' : 'false';
      if (typeof form.requestSubmit === 'function') {
        form.requestSubmit();
      } else {
        form.submit();
      }
    });
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  var regApproveState = {
    persons: [],
    matchedPersonId: null,
  };

  function regApproveFormValues() {
    return {
      firstName: (document.getElementById('reg-approve-first-name') || {}).value || '',
      lastName: (document.getElementById('reg-approve-last-name') || {}).value || '',
      email: (document.getElementById('reg-approve-email-input') || {}).value || '',
      birthdate: (document.getElementById('reg-approve-birthdate-input') || {}).value || '',
    };
  }

  function selectedRegPerson() {
    var select = document.getElementById('reg-approve-person-select');
    if (!select || !select.value) return null;
    var id = String(select.value);
    for (var i = 0; i < regApproveState.persons.length; i++) {
      if (String(regApproveState.persons[i].id) === id) {
        return regApproveState.persons[i];
      }
    }
    return null;
  }

  function normalizeCompare(value) {
    return String(value == null ? '' : value)
      .trim()
      .toLowerCase();
  }

  function buildRegDiffs(person) {
    if (!person) return [];
    var form = regApproveFormValues();
    var fields = [
      { field: 'firstName', label: 'Vorname', reg: form.firstName, per: person.firstName },
      { field: 'lastName', label: 'Nachname', reg: form.lastName, per: person.lastName },
      { field: 'email', label: 'E-Mail', reg: form.email, per: person.email },
      { field: 'birthdate', label: 'Geburtsdatum', reg: form.birthdate, per: person.birthdate },
    ];
    return fields.filter(function (f) {
      return normalizeCompare(f.reg) !== normalizeCompare(f.per);
    });
  }

  function renderRegDiffs() {
    var diffsBox = document.getElementById('reg-approve-diffs');
    var diffRows = document.getElementById('reg-approve-diff-rows');
    var person = selectedRegPerson();
    var submitBtn = document.getElementById('reg-approve-submit');
    if (submitBtn) {
      submitBtn.disabled = !!(person && person.alreadyLinked);
    }
    if (!diffsBox || !diffRows) return;
    if (!person || person.alreadyLinked) {
      diffsBox.hidden = true;
      diffRows.innerHTML = '';
      return;
    }
    var diffs = buildRegDiffs(person);
    if (!diffs.length) {
      diffsBox.hidden = true;
      diffRows.innerHTML = '';
      return;
    }
    diffsBox.hidden = false;
    diffRows.innerHTML = diffs
      .map(function (diff) {
        var field = escapeHtml(diff.field);
        var regVal = diff.reg ? escapeHtml(diff.reg) : '—';
        var perVal = diff.per ? escapeHtml(diff.per) : '—';
        return (
          '<div class="reg-approve-diff">' +
          '<div class="reg-approve-diff__label">' +
          escapeHtml(diff.label) +
          '</div>' +
          '<label class="radio-row">' +
          '<input type="radio" name="choice_' +
          field +
          '" value="registration" checked/>' +
          '<span>Registrierung: <strong>' +
          regVal +
          '</strong></span>' +
          '</label>' +
          '<label class="radio-row">' +
          '<input type="radio" name="choice_' +
          field +
          '" value="person"/>' +
          '<span>Personal: <strong>' +
          perVal +
          '</strong></span>' +
          '</label>' +
          '</div>'
        );
      })
      .join('');
  }

  function personOptionLabel(person) {
    var parts = [person.displayName || [person.lastName, person.firstName].filter(Boolean).join(', ')];
    if (person.email) parts.push(person.email);
    if (person.birthdate) parts.push(person.birthdate);
    if (person.alreadyLinked) parts.push('(bereits verknüpft)');
    return parts.join(' · ');
  }

  function fillPersonSelect(preferredId) {
    var select = document.getElementById('reg-approve-person-select');
    var filter = document.getElementById('reg-approve-person-filter');
    if (!select) return;
    var q = ((filter && filter.value) || '').trim().toLowerCase();
    var current = preferredId != null ? String(preferredId) : select.value || '';
    select.innerHTML = '';
    var empty = document.createElement('option');
    empty.value = '';
    empty.textContent = '— Neue Person anlegen —';
    select.appendChild(empty);

    regApproveState.persons.forEach(function (person) {
      var label = personOptionLabel(person);
      if (q && label.toLowerCase().indexOf(q) < 0) {
        return;
      }
      var opt = document.createElement('option');
      opt.value = String(person.id);
      opt.textContent = label;
      opt.disabled = !!person.alreadyLinked;
      select.appendChild(opt);
    });

    if (current) {
      select.value = current;
      if (select.value !== current) {
        select.value = '';
      }
    } else {
      select.value = '';
    }
    renderRegDiffs();
  }

  function openRegistrationApprove(userId) {
    var overlay = document.getElementById('modal-registration-approve');
    var loading = document.getElementById('reg-approve-loading');
    var errorEl = document.getElementById('reg-approve-error');
    var content = document.getElementById('reg-approve-content');
    var footer = document.getElementById('reg-approve-footer');
    var approveForm = document.getElementById('form-registration-approve');
    var rejectForm = document.getElementById('form-registration-reject');
    if (!overlay || !approveForm) return;

    if (loading) loading.hidden = false;
    if (errorEl) {
      errorEl.hidden = true;
      errorEl.textContent = '';
    }
    if (content) content.hidden = true;
    if (footer) footer.hidden = true;

    approveForm.action = '/admin/users/' + encodeURIComponent(userId) + '/registration-approve';
    if (rejectForm) {
      rejectForm.action = '/admin/users/' + encodeURIComponent(userId) + '/registration-reject';
    }

    overlay.classList.add('active');
    document.body.classList.add('modal-open');

    fetch('/admin/users/' + encodeURIComponent(userId) + '/registration-preview', {
      credentials: 'same-origin',
      headers: { Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
    })
      .then(function (res) {
        if (!res.ok) {
          return res
            .json()
            .then(function (data) {
              throw new Error((data && data.message) || 'Vorschau konnte nicht geladen werden.');
            })
            .catch(function (err) {
              if (err && err.message) throw err;
              throw new Error('Vorschau konnte nicht geladen werden.');
            });
        }
        return res.json();
      })
      .then(function (data) {
        if (loading) loading.hidden = true;
        if (content) content.hidden = false;
        if (footer) footer.hidden = false;

        var userEl = document.getElementById('reg-approve-username');
        var warnEl = document.getElementById('reg-approve-warning');
        var firstInput = document.getElementById('reg-approve-first-name');
        var lastInput = document.getElementById('reg-approve-last-name');
        var emailInput = document.getElementById('reg-approve-email-input');
        var birthInput = document.getElementById('reg-approve-birthdate-input');
        var filterInput = document.getElementById('reg-approve-person-filter');

        if (userEl) userEl.textContent = data.username || '—';
        if (firstInput) firstInput.value = data.firstName || '';
        if (lastInput) lastInput.value = data.lastName || '';
        if (emailInput) emailInput.value = data.email || '';
        if (birthInput) birthInput.value = data.birthdate || '';
        if (filterInput) filterInput.value = '';

        if (warnEl) {
          if (data.warning) {
            warnEl.textContent = data.warning;
            warnEl.hidden = false;
          } else {
            warnEl.textContent = '';
            warnEl.hidden = true;
          }
        }

        regApproveState.persons = data.persons || [];
        var preferred = null;
        if (data.person && data.person.id && !data.personAlreadyLinked) {
          preferred = String(data.person.id);
          regApproveState.matchedPersonId = preferred;
        } else {
          regApproveState.matchedPersonId = null;
        }
        fillPersonSelect(preferred);
      })
      .catch(function (err) {
        if (loading) loading.hidden = true;
        if (errorEl) {
          errorEl.textContent = err.message || 'Fehler beim Laden.';
          errorEl.hidden = false;
        }
      });
  }

  document.querySelectorAll('[data-open-registration-approve]').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      openRegistrationApprove(btn.getAttribute('data-user-id'));
    });
  });

  var personSelect = document.getElementById('reg-approve-person-select');
  if (personSelect) {
    personSelect.addEventListener('change', renderRegDiffs);
  }
  var personFilter = document.getElementById('reg-approve-person-filter');
  if (personFilter) {
    personFilter.addEventListener('input', function () {
      fillPersonSelect(personSelect ? personSelect.value : '');
    });
  }
  ['reg-approve-first-name', 'reg-approve-last-name', 'reg-approve-email-input', 'reg-approve-birthdate-input'].forEach(
    function (id) {
      var el = document.getElementById(id);
      if (el) {
        el.addEventListener('input', renderRegDiffs);
        el.addEventListener('change', renderRegDiffs);
      }
    }
  );

  var rejectBtn = document.getElementById('reg-approve-reject');
  if (rejectBtn) {
    rejectBtn.addEventListener('click', function () {
      var rejectForm = document.getElementById('form-registration-reject');
      if (!rejectForm || !rejectForm.action || rejectForm.action.indexOf('registration-reject') < 0) {
        return;
      }
      if (
        !window.confirm(
          'Registrierung wirklich ablehnen? Das Konto wird gelöscht und der Antragsteller per E-Mail informiert.'
        )
      ) {
        return;
      }
      rejectForm.submit();
    });
  }

  initSelfRegistrationToggle();
})();
