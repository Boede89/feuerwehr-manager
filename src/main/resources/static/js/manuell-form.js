(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', function () {
    var numberInput = document.getElementById('alarmNumber');
    if (!numberInput || !numberInput.dataset.unitId) {
      return;
    }
    fetch('/einsatz/manuell/suggest-number?unit=' + encodeURIComponent(numberInput.dataset.unitId), {
      credentials: 'same-origin'
    })
      .then(function (res) {
        if (!res.ok) throw new Error('failed');
        return res.text();
      })
      .then(function (suggested) {
        if (!numberInput.value) {
          numberInput.value = suggested;
        }
      })
      .catch(function () {
        /* Vorschlag bleibt aus Server-Rendering */
      });
  });
})();
