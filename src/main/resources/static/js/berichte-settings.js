(function () {
  'use strict';

  function syncDiveraPersonnelToggles() {
    var importCb = document.getElementById('importPersonnelFromDivera');
    var autoCb = document.getElementById('autoAssignDiveraPersonnelToAnwesenheit');
    var autoRow = document.getElementById('autoAssignDiveraRow');
    if (!importCb || !autoCb || !autoRow) {
      return;
    }
    if (!importCb.checked) {
      autoCb.checked = false;
      autoCb.disabled = true;
      autoRow.classList.add('toggle-row--disabled');
    } else {
      autoCb.disabled = false;
      autoRow.classList.remove('toggle-row--disabled');
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    var importCb = document.getElementById('importPersonnelFromDivera');
    if (!importCb) {
      return;
    }
    importCb.addEventListener('change', syncDiveraPersonnelToggles);
    syncDiveraPersonnelToggles();
  });
})();
