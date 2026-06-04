(function (global) {
  function showFromSource(el) {
    if (!el || !global.toast) return;
    var text = (el.textContent || '').trim();
    if (!text) return;
    var type = 'success';
    if (el.classList.contains('flash-toast-source--error')) {
      type = 'error';
    } else if (el.classList.contains('flash-toast-source--warning')) {
      type = 'warning';
    }
    global.toast(text, type);
    el.remove();
  }

  function initFlashToasts() {
    document.querySelectorAll('.flash-toast-source').forEach(showFromSource);

    // Abwärtskompatibilität: alte .ok / .err ohne persistente Hinweise
    document.querySelectorAll('.ok:not([data-keep-alert]), .err:not([data-keep-alert])').forEach(function (el) {
      if (el.closest('.flash-toast-source')) return;
      if (el.classList.contains('flash-toast-source')) return;
      var text = (el.textContent || '').trim();
      if (!text) return;
      var type = el.classList.contains('err') ? 'error' : 'success';
      if (global.toast) global.toast(text, type);
      el.remove();
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initFlashToasts);
  } else {
    initFlashToasts();
  }
})(typeof window !== 'undefined' ? window : globalThis);
