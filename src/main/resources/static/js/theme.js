(function () {
  function applyTheme(theme) {
    if (theme === 'dark') {
      document.documentElement.setAttribute('data-theme', 'dark');
      localStorage.setItem('ff_theme', 'dark');
    } else {
      document.documentElement.removeAttribute('data-theme');
      localStorage.removeItem('ff_theme');
    }
    syncToggleButtons(theme);
  }

  function currentTheme() {
    return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
  }

  function syncToggleButtons(theme) {
    document.querySelectorAll('[data-theme-choice]').forEach(function (btn) {
      var choice = btn.getAttribute('data-theme-choice');
      var active = choice === theme;
      btn.classList.toggle('btn--primary', active);
      btn.classList.toggle('btn--outline', !active);
    });
  }

  function persistTheme(theme) {
    var tokenMeta = document.querySelector('meta[name="csrf-token"]');
    var headerMeta = document.querySelector('meta[name="csrf-header"]');
    var paramMeta = document.querySelector('meta[name="csrf-param"]');
    if (!tokenMeta || !headerMeta || !paramMeta) {
      return;
    }
    var headers = {};
    headers[headerMeta.getAttribute('content')] = tokenMeta.getAttribute('content');
    headers['Content-Type'] = 'application/x-www-form-urlencoded';
    var body = encodeURIComponent(paramMeta.getAttribute('content')) + '='
      + encodeURIComponent(tokenMeta.getAttribute('content'))
      + '&theme=' + encodeURIComponent(theme);
    fetch('/settings/theme', { method: 'POST', headers: headers, body: body, credentials: 'same-origin' })
      .catch(function () { /* still applied locally */ });
  }

  document.addEventListener('DOMContentLoaded', function () {
    syncToggleButtons(currentTheme());
    document.querySelectorAll('[data-theme-choice]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var theme = btn.getAttribute('data-theme-choice');
        applyTheme(theme);
        persistTheme(theme);
      });
    });
  });
})();
