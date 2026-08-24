(function () {
  'use strict';

  var overlay = document.getElementById('login-overlay');
  var openBtn = document.getElementById('start-login-open');
  var closeBtn = document.getElementById('start-login-close');
  var userInput = document.getElementById('username');

  if (!overlay) {
    return;
  }

  function isOpen() {
    return overlay.classList.contains('active');
  }

  function openLogin() {
    overlay.classList.add('active');
    overlay.setAttribute('aria-hidden', 'false');
    document.body.classList.add('modal-open');
    if (userInput) {
      window.setTimeout(function () {
        userInput.focus();
      }, 30);
    }
  }

  function closeLogin() {
    overlay.classList.remove('active');
    overlay.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('modal-open');
    if (openBtn) {
      openBtn.focus();
    }
  }

  if (openBtn) {
    openBtn.addEventListener('click', openLogin);
  }
  if (closeBtn) {
    closeBtn.addEventListener('click', closeLogin);
  }

  overlay.addEventListener('click', function (event) {
    if (event.target === overlay) {
      closeLogin();
    }
  });

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && isOpen()) {
      var unknownModal = document.getElementById('modal-rfid-register-unknown');
      if (unknownModal && unknownModal.classList.contains('active')) {
        return;
      }
      closeLogin();
    }
  });

  if (isOpen()) {
    document.body.classList.add('modal-open');
  }
})();
