/**
 * Formular-Jahresfilter per Change absenden (kein Inline-JS — CSP-kompatibel).
 * Markierung: select[data-year-filter-submit] innerhalb eines form.
 */
(function () {
  'use strict';

  function onReady(fn) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', fn);
    } else {
      fn();
    }
  }

  onReady(function () {
    document.querySelectorAll('select[data-year-filter-submit]').forEach(function (select) {
      if (select.dataset.yearFilterBound === 'true') {
        return;
      }
      select.dataset.yearFilterBound = 'true';
      select.addEventListener('change', function () {
        var form = select.form || select.closest('form');
        if (!form) {
          return;
        }
        if (typeof form.requestSubmit === 'function') {
          form.requestSubmit();
        } else {
          form.submit();
        }
      });
    });
  });
})();
