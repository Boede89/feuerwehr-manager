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
    if (root.dataset.uvvViewerReady === 'true') {
      return;
    }
    root.dataset.uvvViewerReady = 'true';

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

    root._uvvReset = function () {
      page = 1;
      maxReached = 1;
      if (completedInput && root.getAttribute('data-require-complete') === 'true') {
        completedInput.value = 'false';
      }
      if (after && root.getAttribute('data-require-complete') === 'true') {
        after.hidden = true;
      }
      if (form && root.getAttribute('data-require-complete') === 'true') {
        form.classList.add('uvv-complete-locked');
      }
      if (doneHint) {
        doneHint.hidden = true;
      }
      if (nextBtn) {
        nextBtn.disabled = false;
      }
      render();
    };

    render();
  }

  function exitBrowserFullscreen() {
    var exitFs = document.exitFullscreen
      || document.webkitExitFullscreen
      || document.msExitFullscreen;
    if (exitFs && (document.fullscreenElement || document.webkitFullscreenElement)) {
      try {
        exitFs.call(document);
      } catch (e) {
        // Browser blockiert ggf. den Exit
      }
    }
  }

  function enterBrowserFullscreen(el) {
    var req = el.requestFullscreen
      || el.webkitRequestFullscreen
      || el.msRequestFullscreen;
    if (!req) {
      return;
    }
    try {
      var result = req.call(el);
      if (result && typeof result.catch === 'function') {
        result.catch(function () {
          // Fallback: CSS-Vollbild reicht
        });
      }
    } catch (e) {
      // Fallback: CSS-Vollbild reicht
    }
  }

  function initRunner() {
    var runner = document.getElementById('uvv-runner');
    var landing = document.getElementById('uvv-landing');
    var startBtn = document.getElementById('uvv-start-btn');
    var exitBtn = document.getElementById('uvv-runner-exit');
    if (!runner || !startBtn) {
      return;
    }

    var presentation = runner.querySelector('.uvv-presentation');
    var form = document.getElementById('uvv-complete-form');

    function startRunner() {
      if (landing) {
        landing.hidden = true;
      }
      runner.hidden = false;
      document.body.classList.add('uvv-runner-open');
      if (presentation) {
        initViewer(presentation);
        if (typeof presentation._uvvReset === 'function') {
          presentation._uvvReset();
        }
      } else if (form) {
        form.classList.remove('uvv-complete-locked');
        var after = document.getElementById('uvv-after-presentation');
        if (after) {
          after.hidden = false;
        }
        var completedInput = document.getElementById('uvv-presentation-completed');
        if (completedInput) {
          completedInput.value = 'true';
        }
      }
      enterBrowserFullscreen(runner);
      window.setTimeout(function () {
        var focusEl = runner.querySelector('.uvv-slide-next')
          || runner.querySelector('input, textarea, select, button[type="submit"]');
        if (focusEl) {
          focusEl.focus();
        }
      }, 50);
    }

    function stopRunner() {
      exitBrowserFullscreen();
      runner.hidden = true;
      document.body.classList.remove('uvv-runner-open');
      if (landing) {
        landing.hidden = false;
      }
      if (form) {
        form.reset();
        if (runner.getAttribute('data-has-presentation') === 'true') {
          form.classList.add('uvv-complete-locked');
          var after = document.getElementById('uvv-after-presentation');
          if (after) {
            after.hidden = true;
          }
          var completedInput = document.getElementById('uvv-presentation-completed');
          if (completedInput) {
            completedInput.value = 'false';
          }
        }
      }
      if (presentation && typeof presentation._uvvReset === 'function') {
        presentation._uvvReset();
      }
      if (startBtn) {
        startBtn.focus();
      }
    }

    startBtn.addEventListener('click', startRunner);
    if (exitBtn) {
      exitBtn.addEventListener('click', function () {
        if (window.confirm('UVV abbrechen und Vollbild schließen?')) {
          stopRunner();
        }
      });
    }

    document.addEventListener('keydown', function (event) {
      if (event.key !== 'Escape' || runner.hidden) {
        return;
      }
      // Escape beendet ggf. Browser-Fullscreen; Runner bleibt bis Abbrechen offen
    });

    document.addEventListener('fullscreenchange', function () {
      if (!document.fullscreenElement && !runner.hidden) {
        // CSS-Vollbild bleibt aktiv, auch wenn Browser-Fullscreen beendet wurde
      }
    });

    try {
      var params = new URLSearchParams(window.location.search);
      if (params.get('start') === '1') {
        startRunner();
        params.delete('start');
        var next = window.location.pathname + (params.toString() ? '?' + params.toString() : '') + window.location.hash;
        window.history.replaceState({}, '', next);
      }
    } catch (e) {
      // URL-Parsing fehlgeschlagen: manuell starten
    }
  }

  onReady(function () {
    document.querySelectorAll('.uvv-admin-preview').forEach(initViewer);
    initRunner();
  });
})();
