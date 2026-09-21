(function () {
  var meta = document.querySelector('meta[name="user-theme"]');
  var theme = meta && meta.getAttribute('content');
  if (theme === 'light' || theme === 'dark') {
    if (theme === 'dark') {
      document.documentElement.setAttribute('data-theme', 'dark');
      try { localStorage.setItem('ff_theme', 'dark'); } catch (e) { /* ignore */ }
    } else {
      document.documentElement.removeAttribute('data-theme');
      try { localStorage.setItem('ff_theme', 'light'); } catch (e) { /* ignore */ }
    }
    return;
  }
  var stored = null;
  try { stored = localStorage.getItem('ff_theme'); } catch (e) { /* ignore */ }
  if (stored === 'light') {
    document.documentElement.removeAttribute('data-theme');
    return;
  }
  // Standard ohne gespeicherte Präferenz: dunkel
  document.documentElement.setAttribute('data-theme', 'dark');
  try { localStorage.setItem('ff_theme', 'dark'); } catch (e) { /* ignore */ }
})();
