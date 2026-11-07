(function () {
  'use strict';

  var form = document.getElementById('auswertung-dienste-filter-form');
  if (!form) return;

  form.querySelectorAll('[data-dienste-filter]').forEach(function (input) {
    input.addEventListener('change', function () {
      var chip = input.closest('.auswertung-dienste-chip');
      if (chip) {
        chip.classList.toggle('is-active', input.checked);
      }
      form.submit();
    });
  });
})();
