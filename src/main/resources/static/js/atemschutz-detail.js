(function () {
  document.querySelectorAll('form[data-confirm]').forEach(function (form) {
    form.addEventListener('submit', function (e) {
      var msg = form.getAttribute('data-confirm');
      if (msg && !window.confirm(msg)) e.preventDefault();
    });
  });

  var modal = document.getElementById('modal-add-record');
  if (!modal) return;

  var typeSelect = document.getElementById('record-type');
  var validFromInput = document.getElementById('record-valid-from');
  var validUntilInput = document.getElementById('record-valid-until');
  if (!typeSelect || !validFromInput || !validUntilInput) return;

  var birthdate = modal.getAttribute('data-birthdate') || '';

  function parseIsoDate(value) {
    if (!value) return null;
    var parts = value.split('-');
    if (parts.length !== 3) return null;
    return new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
  }

  function formatIsoDate(date) {
    var y = date.getFullYear();
    var m = String(date.getMonth() + 1).padStart(2, '0');
    var d = String(date.getDate()).padStart(2, '0');
    return y + '-' + m + '-' + d;
  }

  function ageAt(birth, onDate) {
    var years = onDate.getFullYear() - birth.getFullYear();
    var monthDiff = onDate.getMonth() - birth.getMonth();
    if (monthDiff < 0 || (monthDiff === 0 && onDate.getDate() < birth.getDate())) {
      years--;
    }
    return years;
  }

  function computeValidUntil(type, validFromValue) {
    var from = parseIsoDate(validFromValue);
    if (!from) {
      validUntilInput.value = '';
      return;
    }
    var years = 1;
    if (type === 'G26_UNTERSUCHUNG' && birthdate) {
      var birth = parseIsoDate(birthdate);
      if (birth) {
        years = ageAt(birth, from) < 50 ? 3 : 1;
      }
    }
    var until = new Date(from.getTime());
    until.setFullYear(until.getFullYear() + years);
    validUntilInput.value = formatIsoDate(until);
  }

  function refreshValidUntil() {
    computeValidUntil(typeSelect.value, validFromInput.value);
  }

  typeSelect.addEventListener('change', refreshValidUntil);
  validFromInput.addEventListener('change', refreshValidUntil);
  validFromInput.addEventListener('input', refreshValidUntil);

  if (!validFromInput.value) {
    validFromInput.value = formatIsoDate(new Date());
  }
  refreshValidUntil();
})();
