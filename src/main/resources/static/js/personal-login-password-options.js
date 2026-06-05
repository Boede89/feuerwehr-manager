(function () {
  function initLoginPasswordOptions(allowLoginEl, form) {
    var options = document.getElementById('login-password-options');
    var manualField = document.getElementById('manual-password-field');
    var initialPassword = document.getElementById('initialPassword');
    if (!allowLoginEl || !options) return;

    function selectedDelivery() {
      var checked = options.querySelector('input[name="passwordDelivery"]:checked');
      return checked ? checked.value : 'manual';
    }

    function syncManualField() {
      var showManual = allowLoginEl.checked && selectedDelivery() === 'manual';
      if (manualField) manualField.hidden = !showManual;
      if (initialPassword) {
        initialPassword.required = showManual;
        if (!showManual) initialPassword.value = '';
      }
    }

    function syncOptionsVisibility() {
      options.hidden = !allowLoginEl.checked;
      syncManualField();
    }

    allowLoginEl.addEventListener('change', syncOptionsVisibility);
    options.querySelectorAll('input[name="passwordDelivery"]').forEach(function (radio) {
      radio.addEventListener('change', syncManualField);
    });
    syncOptionsVisibility();

    if (form) {
      form.addEventListener('submit', function (e) {
        if (!allowLoginEl.checked) return;
        if (selectedDelivery() === 'manual') {
          var minLen = initialPassword ? parseInt(initialPassword.getAttribute('minlength') || '8', 10) : 8;
          var value = initialPassword ? initialPassword.value : '';
          if (!value || value.length < minLen) {
            e.preventDefault();
            if (initialPassword) initialPassword.focus();
            window.alert('Bitte ein Initialpasswort mit mindestens ' + minLen + ' Zeichen eingeben.');
          }
        }
      });
    }
  }

  window.initLoginPasswordOptions = initLoginPasswordOptions;
})();
