(function () {
  function closeAll() {
    document.querySelectorAll('.topnav__dropdown.open').forEach(function (panel) {
      panel.classList.remove('open');
    });
    document.querySelectorAll('.topnav__arrow.open').forEach(function (btn) {
      btn.classList.remove('open');
    });
  }

  document.querySelectorAll('[data-dd-toggle]').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      e.stopPropagation();
      var key = btn.getAttribute('data-dd-toggle');
      var panel = document.querySelector('[data-dropdown-panel="' + key + '"]');
      if (!panel) return;
      var willOpen = !panel.classList.contains('open');
      closeAll();
      if (willOpen) {
        panel.classList.add('open');
        btn.classList.add('open');
      }
    });
  });

  document.addEventListener('click', function () {
    closeAll();
  });

  document.querySelectorAll('.topnav__dropdown-wrap').forEach(function (wrap) {
    wrap.addEventListener('click', function (e) {
      e.stopPropagation();
    });
  });
})();
