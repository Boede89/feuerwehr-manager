(function () {
  const disableBtn = document.getElementById('btn-disable-totp');
  const cancelDisableBtn = document.getElementById('btn-cancel-disable-totp');
  const disableArea = document.getElementById('totp-disable-area');
  const disableConfirm = document.getElementById('totp-disable-confirm');
  const disableCode = document.getElementById('totp-disable-code');
  const setupCode = document.getElementById('totp-code');

  if (setupCode) {
    setupCode.focus();
  }

  if (disableBtn && disableArea && disableConfirm) {
    disableBtn.addEventListener('click', () => {
      disableArea.style.display = 'none';
      disableConfirm.style.display = 'block';
      if (disableCode) {
        disableCode.focus();
      }
    });
  }

  if (cancelDisableBtn && disableArea && disableConfirm) {
    cancelDisableBtn.addEventListener('click', () => {
      disableArea.style.display = 'block';
      disableConfirm.style.display = 'none';
      if (disableCode) {
        disableCode.value = '';
      }
    });
  }
})();
