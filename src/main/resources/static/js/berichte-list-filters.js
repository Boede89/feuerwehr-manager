(function () {
  'use strict';

  var PREFIX = 'feuerwehr.berichte.filters.';

  function storageKey(unitId, tab) {
    return PREFIX + String(unitId || '') + '.' + String(tab || '');
  }

  function isBerichtePath(pathname) {
    if (!pathname) {
      return false;
    }
    return pathname === '/berichte' || pathname.indexOf('/berichte/') === 0;
  }

  function pathFromHref(href) {
    if (!href || href.charAt(0) === '#' || href.indexOf('javascript:') === 0) {
      return null;
    }
    try {
      return new URL(href, window.location.origin).pathname;
    } catch (e) {
      return null;
    }
  }

  function load(unitId, tab, defaults) {
    var base = Object.assign({}, defaults || {});
    try {
      var raw = sessionStorage.getItem(storageKey(unitId, tab));
      if (!raw) {
        return base;
      }
      var parsed = JSON.parse(raw);
      if (!parsed || typeof parsed !== 'object') {
        return base;
      }
      return Object.assign(base, parsed);
    } catch (e) {
      return base;
    }
  }

  function save(unitId, tab, filters) {
    try {
      sessionStorage.setItem(storageKey(unitId, tab), JSON.stringify(filters || {}));
    } catch (e) {
      // ignore quota / private mode
    }
  }

  function clearAll() {
    try {
      var keys = [];
      for (var i = 0; i < sessionStorage.length; i++) {
        var key = sessionStorage.key(i);
        if (key && key.indexOf(PREFIX) === 0) {
          keys.push(key);
        }
      }
      keys.forEach(function (key) {
        sessionStorage.removeItem(key);
      });
    } catch (e) {
      // ignore
    }
  }

  function bindClearOnLeave() {
    document.addEventListener('click', function (event) {
      var link = event.target && event.target.closest ? event.target.closest('a[href]') : null;
      if (!link) {
        return;
      }
      var targetPath = pathFromHref(link.getAttribute('href'));
      if (!targetPath) {
        return;
      }
      if (isBerichtePath(window.location.pathname) && !isBerichtePath(targetPath)) {
        clearAll();
      }
    }, true);

    document.querySelectorAll('form[action*="logout"]').forEach(function (form) {
      form.addEventListener('submit', function () {
        clearAll();
      });
    });
  }

  window.BerichteListFilters = {
    load: load,
    save: save,
    clearAll: clearAll
  };

  document.addEventListener('DOMContentLoaded', bindClearOnLeave);
})();
