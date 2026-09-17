(function () {
  function onReady(fn) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', fn);
    } else {
      fn();
    }
  }

  function slideUrl(root, page) {
    var prefix = root.getAttribute('data-slide-url-prefix') || '';
    var query = root.getAttribute('data-slide-url-query') || '';
    var url = prefix.replace(/\/?$/, '/') + String(page);
    if (query) {
      url += (url.indexOf('?') >= 0 ? '&' : '?') + query;
    }
    return url;
  }

  function initViewer(root) {
    var pageCount = parseInt(root.getAttribute('data-page-count') || '0', 10);
    if (!pageCount || pageCount < 1) {
      return;
    }
    var requireComplete = root.getAttribute('data-require-complete') === 'true';
    var img = root.querySelector('.uvv-slide-image');
    var status = root.querySelector('.uvv-slide-status');
    var prevBtn = root.querySelector('.uvv-slide-prev');
    var nextBtn = root.querySelector('.uvv-slide-next');
    var doneHint = root.querySelector('.uvv-slide-done');
    var form = document.getElementById('uvv-complete-form');
    var after = document.getElementById('uvv-after-presentation');
    var completedInput = document.getElementById('uvv-presentation-completed');
    var page = 1;
    var maxReached = 1;

    function unlockAfterPresentation() {
      if (completedInput) {
        completedInput.value = 'true';
      }
      if (after) {
        after.hidden = false;
      }
      if (form) {
        form.classList.remove('uvv-complete-locked');
      }
      if (doneHint) {
        doneHint.hidden = false;
      }
      if (nextBtn) {
        nextBtn.textContent = 'Fertig';
        nextBtn.disabled = true;
      }
    }

    function render() {
      if (img) {
        img.src = slideUrl(root, page);
      }
      if (status) {
        status.textContent = 'Folie ' + page + ' / ' + pageCount;
      }
      if (prevBtn) {
        prevBtn.disabled = page <= 1;
      }
      if (nextBtn && !(requireComplete && maxReached >= pageCount && page >= pageCount)) {
        nextBtn.disabled = page >= pageCount && !requireComplete;
        if (!requireComplete) {
          nextBtn.textContent = page >= pageCount ? 'Ende' : 'Weiter';
        } else {
          nextBtn.textContent = page >= pageCount ? 'Zu den Fragen' : 'Weiter';
          nextBtn.disabled = false;
        }
      }
      if (requireComplete && maxReached >= pageCount && page >= pageCount) {
        unlockAfterPresentation();
      }
    }

    if (prevBtn) {
      prevBtn.addEventListener('click', function () {
        if (page > 1) {
          page -= 1;
          render();
        }
      });
    }
    if (nextBtn) {
      nextBtn.addEventListener('click', function () {
        if (page < pageCount) {
          page += 1;
          if (page > maxReached) {
            maxReached = page;
          }
          render();
          return;
        }
        if (requireComplete && maxReached >= pageCount) {
          unlockAfterPresentation();
          if (after) {
            after.scrollIntoView({ behavior: 'smooth', block: 'start' });
          }
        }
      });
    }

    render();
  }

  onReady(function () {
    document.querySelectorAll('.uvv-presentation, .uvv-admin-preview').forEach(initViewer);
  });
})();
