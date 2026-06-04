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

  document.addEventListener('DOMContentLoaded', function () {
    syncToggleButtons(currentTheme());
    document.querySelectorAll('[data-theme-choice]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        applyTheme(btn.getAttribute('data-theme-choice'));
      });
    });
  });
})();
