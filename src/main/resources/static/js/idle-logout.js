(function () {
  var minutes = Number(window.__FW_IDLE_LOGOUT_MINUTES__ || 0);
  if (!Number.isFinite(minutes) || minutes <= 0) {
    return;
  }

  var timeoutMs = minutes * 60 * 1000;
  var timer = null;
  var loggingOut = false;

  function logout() {
    if (loggingOut) {
      return;
    }
    loggingOut = true;
    var form = document.getElementById('idle-logout-form');
    if (form) {
      if (typeof form.requestSubmit === 'function') {
        form.requestSubmit();
      } else {
        form.submit();
      }
      return;
    }
    window.location.href = '/login?expired=1';
  }

  function resetTimer() {
    if (loggingOut) {
      return;
    }
    if (timer) {
      clearTimeout(timer);
    }
    timer = setTimeout(logout, timeoutMs);
  }

  ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart', 'wheel', 'click'].forEach(function (evt) {
    document.addEventListener(evt, resetTimer, { passive: true, capture: true });
  });
  document.addEventListener('visibilitychange', function () {
    if (!document.hidden) {
      resetTimer();
    }
  });

  resetTimer();
})();
