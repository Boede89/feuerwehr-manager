(function () {
  'use strict';

  var root = document.querySelector('.checkin-page');
  if (!root) {
    return;
  }

  var unitId = root.getAttribute('data-unit-id');
  var reportId = root.getAttribute('data-report-id');

  function getCsrfToken() {
    var meta = document.querySelector('meta[name="csrf-token"]');
    return meta ? meta.getAttribute('content') : '';
  }

  function getCsrfHeader() {
    var meta = document.querySelector('meta[name="csrf-header"]');
    return meta ? meta.getAttribute('content') : 'X-CSRF-TOKEN';
  }

  function api(path, method, body) {
    var url = path + (path.indexOf('?') >= 0 ? '&' : '?') + 'unit=' + encodeURIComponent(unitId);
    var opts = {
      method: method || 'GET',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json'
      }
    };
    var csrf = getCsrfToken();
    if (csrf) {
      opts.headers[getCsrfHeader()] = csrf;
    }
    if (body != null) {
      opts.body = JSON.stringify(body);
    }
    return fetch(url, opts).then(function (res) {
      return res.json().then(function (data) {
        if (!res.ok || data.ok === false) {
          throw new Error(data.message || 'Aktion fehlgeschlagen.');
        }
        return data;
      });
    });
  }

  function esc(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function render(page) {
    var themeEl = document.getElementById('checkin-theme-value');
    if (themeEl) {
      themeEl.textContent = page.theme || '';
    }
    var themeInput = document.getElementById('checkin-theme-input');
    if (themeInput) {
      themeInput.value = page.theme || '';
    }

    var available = page.availablePersons || [];
    var checked = page.checkedInPersons || [];
    var availableCount = document.getElementById('checkin-available-count');
    var checkedCount = document.getElementById('checkin-checked-count');
    if (availableCount) {
      availableCount.textContent = String(available.length);
    }
    if (checkedCount) {
      checkedCount.textContent = String(checked.length);
    }

    var availableList = document.getElementById('checkin-available-list');
    if (availableList) {
      if (available.length === 0) {
        availableList.innerHTML = '<p class="hint checkin-empty" id="checkin-available-empty">Alle Personen sind eingecheckt.</p>';
      } else {
        availableList.innerHTML = available.map(function (p) {
          return '<button type="button" class="checkin-tile" data-person-id="' + esc(p.id) + '">' +
            esc(p.displayName) + '</button>';
        }).join('');
      }
    }

    var checkedList = document.getElementById('checkin-checked-list');
    if (checkedList) {
      if (checked.length === 0) {
        checkedList.innerHTML = '<p class="hint checkin-empty" id="checkin-checked-empty">Noch niemand eingecheckt.</p>';
      } else {
        checkedList.innerHTML = checked.map(function (p) {
          return '<button type="button" class="checkin-checked-item" data-person-id="' + esc(p.id) + '">' +
            esc(p.displayName) + '</button>';
        }).join('');
      }
    }
  }

  function basePath() {
    return '/berichte/anwesenheitslisten/' + encodeURIComponent(reportId) + '/check-in';
  }

  function tickClock() {
    var el = document.getElementById('checkin-clock');
    if (!el) {
      return;
    }
    var now = new Date();
    var hh = String(now.getHours()).padStart(2, '0');
    var mm = String(now.getMinutes()).padStart(2, '0');
    var ss = String(now.getSeconds()).padStart(2, '0');
    el.textContent = hh + ':' + mm + ':' + ss;
  }

  function openThemeModal() {
    var modal = document.getElementById('checkin-theme-modal');
    var input = document.getElementById('checkin-theme-input');
    if (!modal) {
      return;
    }
    modal.hidden = false;
    modal.classList.add('active');
    document.body.classList.add('modal-open');
    if (input) {
      input.focus();
      input.select();
    }
  }

  function closeThemeModal() {
    var modal = document.getElementById('checkin-theme-modal');
    if (!modal) {
      return;
    }
    modal.hidden = true;
    modal.classList.remove('active');
    document.body.classList.remove('modal-open');
  }

  function saveTheme() {
    var input = document.getElementById('checkin-theme-input');
    var theme = input ? input.value.trim() : '';
    if (!theme) {
      window.alert('Bitte ein Thema angeben.');
      return;
    }
    api(basePath() + '/theme', 'POST', { theme: theme })
      .then(function (page) {
        render(page);
        closeThemeModal();
      })
      .catch(function (err) {
        window.alert(err.message || 'Thema konnte nicht gespeichert werden.');
      });
  }

  function askCheckout(name) {
    var message = 'Diese Person auschecken' + (name ? ' („' + name + '“)' : '') + '?';
    if (window.FwConfirm && window.FwConfirm.ask) {
      return window.FwConfirm.ask(message, 'Person auschecken');
    }
    return Promise.resolve(window.confirm(message));
  }

  document.getElementById('checkin-available-list')?.addEventListener('click', function (e) {
    var btn = e.target.closest('.checkin-tile');
    if (!btn) {
      return;
    }
    var personId = btn.getAttribute('data-person-id');
    api(basePath() + '/person/' + encodeURIComponent(personId), 'POST')
      .then(render)
      .catch(function (err) {
        window.alert(err.message || 'Check-In fehlgeschlagen.');
      });
  });

  document.getElementById('checkin-checked-list')?.addEventListener('click', function (e) {
    var btn = e.target.closest('.checkin-checked-item');
    if (!btn) {
      return;
    }
    var personId = btn.getAttribute('data-person-id');
    var name = btn.textContent.trim();
    askCheckout(name).then(function (ok) {
      if (!ok) {
        return;
      }
      api(basePath() + '/person/' + encodeURIComponent(personId), 'DELETE')
        .then(render)
        .catch(function (err) {
          window.alert(err.message || 'Checkout fehlgeschlagen.');
        });
    });
  });

  document.getElementById('checkin-theme-edit-btn')?.addEventListener('click', openThemeModal);
  document.getElementById('checkin-theme-modal-close')?.addEventListener('click', closeThemeModal);
  document.getElementById('checkin-theme-cancel-btn')?.addEventListener('click', closeThemeModal);
  document.getElementById('checkin-theme-save-btn')?.addEventListener('click', saveTheme);
  document.getElementById('checkin-theme-input')?.addEventListener('keydown', function (e) {
    if (e.key === 'Enter') {
      e.preventDefault();
      saveTheme();
    }
  });

  tickClock();
  window.setInterval(tickClock, 1000);
})();
