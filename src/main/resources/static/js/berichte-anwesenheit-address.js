(function () {
  'use strict';

  function setField(id, value) {
    var input = document.getElementById(id);
    if (!input || input.readOnly || input.disabled) {
      return;
    }
    input.value = value == null ? '' : String(value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function readAddress(scope) {
    var root = scope || document;
    var dataEl = root.querySelector('#unit-address-data');
    if (!dataEl || !dataEl.textContent) {
      return null;
    }
    try {
      return JSON.parse(dataEl.textContent);
    } catch (e) {
      return null;
    }
  }

  function init(scope) {
    var root = scope || document;
    var btn = root.querySelector('#btn-fill-geraetehaus-address');
    if (!btn || btn.dataset.bound === 'true') {
      return;
    }
    btn.dataset.bound = 'true';
    btn.addEventListener('click', function () {
      var address = readAddress(root);
      if (!address) {
        return;
      }
      var hasData = address.location || address.postalCode || address.street || address.houseNumber;
      if (!hasData) {
        window.alert('Für diese Einheit sind noch keine Adress-Stammdaten hinterlegt (Administration → Einheit).');
        return;
      }
      setField('location', address.location);
      setField('postalCode', address.postalCode);
      setField('street', address.street);
      setField('houseNumber', address.houseNumber);
    });
  }

  window.BerichteAnwesenheitAddress = { init: init };
})();
