(function (global) {
  function toast(msg, type) {
    type = type || 'success';
    var container = document.getElementById('toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'toast-container';
      document.body.appendChild(container);
    }
    var el = document.createElement('div');
    el.className = 'toast' + (type !== 'success' ? ' toast--' + type : '');
    el.textContent = msg;
    container.appendChild(el);
    setTimeout(function () {
      el.remove();
    }, 3500);
  }

  global.toast = toast;
})(typeof window !== 'undefined' ? window : globalThis);
