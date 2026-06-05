(function () {
  var allowLogin = document.getElementById('allowLogin');
  var email = document.getElementById('email');
  var mark = document.getElementById('email-required-mark');
  var form = document.getElementById('personCreateForm');
  if (!allowLogin || !email || !form) return;

  function syncEmailRequired() {
    var required = allowLogin.checked;
    email.required = required;
    if (mark) mark.style.display = required ? '' : 'none';
  }

  allowLogin.addEventListener('change', syncEmailRequired);
  syncEmailRequired();

  if (window.initLoginPasswordOptions) {
    window.initLoginPasswordOptions(allowLogin, form);
  }

  form.addEventListener('submit', function (e) {
    if (allowLogin.checked && !email.value.trim()) {
      e.preventDefault();
      email.focus();
      window.alert('Bitte eine E-Mail-Adresse eingeben, wenn Login erlauben aktiviert ist.');
    }
  });
})();
