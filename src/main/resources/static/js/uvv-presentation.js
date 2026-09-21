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

  function initViewer(root, onFinish) {
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
    var page = 1;
    var maxReached = 1;

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
      if (!nextBtn) {
        return;
      }
      nextBtn.disabled = false;
      if (!requireComplete) {
        nextBtn.textContent = page >= pageCount ? 'Ende' : 'Weiter';
        nextBtn.disabled = page >= pageCount;
        return;
      }
      nextBtn.textContent = page >= pageCount ? 'Fertig' : 'Weiter';
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
        if (requireComplete && maxReached >= pageCount && typeof onFinish === 'function') {
          onFinish();
        }
      });
    }

    root._uvvReset = function () {
      page = 1;
      maxReached = 1;
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

  function initFinishFlow() {
    var form = document.getElementById('uvv-complete-form');
    var modal = document.getElementById('uvv-finish-modal');
    var completedInput = document.getElementById('uvv-presentation-completed');
    if (!form || !modal) {
      return null;
    }

    var hasQuestions = form.getAttribute('data-has-questions') === 'true';
    var steps = {
      start: modal.querySelector('[data-uvv-step="start"]'),
      question: modal.querySelector('[data-uvv-step="question"]'),
      failed: modal.querySelector('[data-uvv-step="failed"]'),
      confirm: modal.querySelector('[data-uvv-step="confirm"]')
    };
    var questions = Array.prototype.slice.call(modal.querySelectorAll('.uvv-quiz-question'));
    var quizIndex = 0;
    var progress = document.getElementById('uvv-quiz-progress');
    var quizError = document.getElementById('uvv-quiz-error');
    var confirmError = document.getElementById('uvv-confirm-error');
    var quizSuccess = document.getElementById('uvv-quiz-success');
    var confirmed = document.getElementById('uvv-confirmed');
    var quizPrev = document.getElementById('uvv-quiz-prev');
    var quizNext = document.getElementById('uvv-quiz-next');

    function showStep(name) {
      Object.keys(steps).forEach(function (key) {
        if (steps[key]) {
          steps[key].hidden = key !== name;
        }
      });
    }

    function setQuizError(visible) {
      if (quizError) {
        quizError.hidden = !visible;
      }
    }

    function setConfirmError(visible) {
      if (confirmError) {
        confirmError.hidden = !visible;
      }
    }

    function selectedAnswer(questionEl) {
      var checked = questionEl.querySelector('input[type="radio"]:checked');
      return checked ? checked.value : '';
    }

    function renderQuestion() {
      questions.forEach(function (el, index) {
        el.hidden = index !== quizIndex;
      });
      if (progress) {
        progress.textContent = 'Frage ' + (quizIndex + 1) + ' / ' + questions.length;
      }
      if (quizPrev) {
        quizPrev.hidden = quizIndex <= 0;
      }
      if (quizNext) {
        quizNext.textContent = quizIndex >= questions.length - 1 ? 'Auswerten' : 'Weiter';
      }
      setQuizError(false);
    }

    function clearQuizAnswers() {
      questions.forEach(function (el) {
        el.querySelectorAll('input[type="radio"]').forEach(function (input) {
          input.checked = false;
        });
      });
    }

    function evaluateQuiz() {
      for (var i = 0; i < questions.length; i++) {
        var correct = (questions[i].getAttribute('data-correct') || '').trim().toUpperCase();
        var answer = selectedAnswer(questions[i]).trim().toUpperCase();
        if (!answer || answer !== correct) {
          return false;
        }
      }
      return true;
    }

    function openModal() {
      if (completedInput) {
        completedInput.value = 'true';
      }
      if (confirmed) {
        confirmed.checked = false;
      }
      setConfirmError(false);
      if (quizSuccess) {
        quizSuccess.hidden = true;
      }
      clearQuizAnswers();
      quizIndex = 0;
      if (hasQuestions && questions.length > 0) {
        showStep('start');
      } else {
        showConfirm(false);
      }
      modal.hidden = false;
      var focusEl = hasQuestions
        ? (document.getElementById('uvv-quiz-start') || modal.querySelector('button, input'))
        : (confirmed || modal.querySelector('button, input'));
      if (focusEl) {
        focusEl.focus();
      }
    }

    function closeModal() {
      modal.hidden = true;
      showStep('start');
    }

    function startQuiz() {
      if (!hasQuestions || questions.length === 0) {
        showConfirm(false);
        return;
      }
      quizIndex = 0;
      renderQuestion();
      showStep('question');
    }

    function showConfirm(fromQuizPass) {
      if (quizSuccess) {
        quizSuccess.hidden = !fromQuizPass;
      }
      setConfirmError(false);
      showStep('confirm');
      if (confirmed) {
        confirmed.focus();
      }
    }

    modal.querySelectorAll('[data-uvv-modal-close]').forEach(function (el) {
      el.addEventListener('click', closeModal);
    });

    var quizStart = document.getElementById('uvv-quiz-start');
    if (quizStart) {
      quizStart.addEventListener('click', startQuiz);
    }
    var retry = document.getElementById('uvv-quiz-retry');
    if (retry) {
      retry.addEventListener('click', function () {
        clearQuizAnswers();
        startQuiz();
      });
    }
    if (quizPrev) {
      quizPrev.addEventListener('click', function () {
        if (quizIndex > 0) {
          quizIndex -= 1;
          renderQuestion();
        }
      });
    }
    if (quizNext) {
      quizNext.addEventListener('click', function () {
        if (!questions[quizIndex] || !selectedAnswer(questions[quizIndex])) {
          setQuizError(true);
          return;
        }
        setQuizError(false);
        if (quizIndex < questions.length - 1) {
          quizIndex += 1;
          renderQuestion();
          return;
        }
        if (evaluateQuiz()) {
          showConfirm(true);
        } else {
          showStep('failed');
        }
      });
    }

    form.addEventListener('submit', function (event) {
      if (!confirmed || !confirmed.checked) {
        event.preventDefault();
        showStep('confirm');
        setConfirmError(true);
        return;
      }
      if (hasQuestions && !evaluateQuiz()) {
        event.preventDefault();
        showStep('failed');
      }
    });

    return {
      open: openModal,
      close: closeModal,
      reset: function () {
        closeModal();
        clearQuizAnswers();
        if (confirmed) {
          confirmed.checked = false;
        }
        if (completedInput && form.closest('#uvv-runner')
            && form.closest('#uvv-runner').getAttribute('data-has-presentation') === 'true') {
          completedInput.value = 'false';
        }
      }
    };
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
    var finishFlow = initFinishFlow();
    var noPresentationFinish = document.getElementById('uvv-no-presentation-finish');

    function openFinish() {
      if (finishFlow) {
        finishFlow.open();
      }
    }

    function startRunner() {
      if (landing) {
        landing.hidden = true;
      }
      runner.hidden = false;
      document.body.classList.add('uvv-runner-open');
      if (presentation) {
        initViewer(presentation, openFinish);
        if (typeof presentation._uvvReset === 'function') {
          presentation._uvvReset();
        }
      }
      if (finishFlow) {
        finishFlow.reset();
      }
      enterBrowserFullscreen(runner);
      window.setTimeout(function () {
        var focusEl = runner.querySelector('.uvv-slide-next')
          || noPresentationFinish
          || runner.querySelector('button, input, textarea, select');
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
      if (finishFlow) {
        finishFlow.reset();
      }
      var form = document.getElementById('uvv-complete-form');
      if (form) {
        form.reset();
        var completedInput = document.getElementById('uvv-presentation-completed');
        if (completedInput) {
          completedInput.value = runner.getAttribute('data-has-presentation') === 'true' ? 'false' : 'true';
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
    if (noPresentationFinish) {
      noPresentationFinish.addEventListener('click', openFinish);
    }

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
    document.querySelectorAll('.uvv-admin-preview').forEach(function (root) {
      initViewer(root);
    });
    initRunner();
  });
})();
