(function () {
  'use strict';

  function bindRecordedPersonId(scope) {
    var hiddenPersonId = document.getElementById('recordedPersonId');
    if (!hiddenPersonId) {
      return;
    }
    scope.querySelectorAll('.combo-suggest__option[data-person-id]').forEach(function (option) {
      option.addEventListener('mousedown', function () {
        hiddenPersonId.value = option.getAttribute('data-person-id') || '';
      });
    });
    var nameInput = document.getElementById('recordedByName');
    if (nameInput) {
      nameInput.addEventListener('input', function () {
        hiddenPersonId.value = '';
      });
    }
  }

  function init(scope) {
    scope = scope || document;
    bindRecordedPersonId(scope);
  }

  window.BerichteMaengelForm = { init: init };

  document.addEventListener('DOMContentLoaded', function () {
    if (document.querySelector('[data-berichte-form="maengel"]')) {
      init();
    }
  });
})();
