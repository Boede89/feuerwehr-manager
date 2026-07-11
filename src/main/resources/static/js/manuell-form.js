(function () {
  'use strict';

  function syncRoutePlanVisibility() {
    var checkbox = document.getElementById('routePlanUseGeraetehaus');
    var group = document.getElementById('route-plan-start-group');
    var addressInput = document.getElementById('routePlanStartAddress');
    if (!checkbox || !group) {
      return;
    }
    var useGeraetehaus = checkbox.checked;
    group.hidden = useGeraetehaus;
    if (addressInput) {
      addressInput.required = !useGeraetehaus;
      if (useGeraetehaus) {
        addressInput.value = '';
      }
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    var form = document.getElementById('manual-alarm-form');
    var numberInput = document.getElementById('alarmNumber');
    var routeCheckbox = document.getElementById('routePlanUseGeraetehaus');

    if (routeCheckbox) {
      routeCheckbox.addEventListener('change', syncRoutePlanVisibility);
      syncRoutePlanVisibility();
    }

    if (!numberInput || !numberInput.dataset.unitId) {
      return;
    }
    if (form && form.dataset.editMode === 'true') {
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
