(function () {
  var meta = document.querySelector('meta[name="user-theme"]');
  var theme = meta && meta.getAttribute('content');
  if (theme === 'light' || theme === 'dark') {
    if (theme === 'dark') {
      document.documentElement.setAttribute('data-theme', 'dark');
      try { localStorage.setItem('ff_theme', 'dark'); } catch (e) { /* ignore */ }
    } else {
      document.documentElement.removeAttribute('data-theme');
      try { localStorage.removeItem('ff_theme'); } catch (e) { /* ignore */ }
    }
    return;
  }
  if (localStorage.getItem('ff_theme') === 'dark') {
    document.documentElement.setAttribute('data-theme', 'dark');
  }
})();
