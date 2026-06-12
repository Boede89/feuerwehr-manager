(function () {
  'use strict';

  function readBase() {
    var meta = document.querySelector('meta[name="berichte-api-base"]');
    if (meta && meta.content) {
      return meta.content.replace(/\/$/, '');
    }
    return '/berichte/einsatzberichte';
  }

  window.BerichteApiBase = {
    path: readBase
  };
})();
