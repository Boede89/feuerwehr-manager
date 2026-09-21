(function () {
  'use strict';

  var overlay = document.getElementById('atemschutz-entry-overlay');
  var openBtns = document.querySelectorAll('.atemschutz-entry-open');
  if (!overlay || !openBtns.length) {
    initReviewCarrierToggle();
    return;
  }

  var form = document.getElementById('atemschutz-entry-form');
  var cancelBtn = document.getElementById('atemschutz-entry-cancel');
  var cancelFooterBtn = document.getElementById('atemschutz-entry-cancel-footer');
  var errorEl = document.getElementById('atemschutz-entry-error');
  var carrierList = document.getElementById('atemschutz-entry-carriers');
  var typeSelect = document.getElementById('atemschutz-entry-type');
  var dateInput = document.getElementById('atemschutz-entry-date');
  var submitBtn = document.getElementById('atemschutz-entry-submit');
  var meta = document.getElementById('dashboard-meta');
  var unitId = meta ? meta.getAttribute('data-unit-id') : '';
  var lastOpenBtn = null;
  var carriers = [];

  function csrfHeaders() {
    var headers = { Accept: 'application/json', 'Content-Type': 'application/json' };
    var tokenMeta = document.querySelector('meta[name="csrf-token"]');
    var headerMeta = document.querySelector('meta[name="csrf-header"]');
    if (tokenMeta && headerMeta) {
      headers[headerMeta.getAttribute('content')] = tokenMeta.getAttribute('content');
    }
    return headers;
  }

  function setError(message) {
    if (!errorEl) {
      return;
    }
    if (message) {
      errorEl.textContent = message;
      errorEl.hidden = false;
    } else {
      errorEl.textContent = '';
      errorEl.hidden = true;
    }
  }

  function openModal(event) {
    if (event && event.currentTarget) {
      lastOpenBtn = event.currentTarget;
    }
    setError('');
    overlay.classList.add('active');
    overlay.setAttribute('aria-hidden', 'false');
    document.body.classList.add('modal-open');
    loadCarriers();
    if (typeSelect) {
      window.setTimeout(function () {
        typeSelect.focus();
      }, 30);
    }
  }

  function closeModal() {
    overlay.classList.remove('active');
    overlay.setAttribute('aria-hidden', 'true');
    if (!document.querySelector('.modal-overlay.active')) {
      document.body.classList.remove('modal-open');
    }
    if (lastOpenBtn) {
      lastOpenBtn.focus();
    }
  }

  function renderCarriers() {
    if (!carrierList) {
      return;
    }
    var type = typeSelect ? typeSelect.value : '';
    var csaOnly = type === 'CSA';
    carrierList.innerHTML = '';
    if (!carriers.length) {
      carrierList.innerHTML = '<p class="text-muted text-sm">Keine aktiven Geräteträger gefunden.</p>';
      return;
    }
    carriers.forEach(function (c) {
      if (csaOnly && !c.csaEligible) {
        return;
      }
      var label = document.createElement('label');
      label.className = 'entry-request-carrier';
      label.setAttribute('data-csa', c.csaEligible ? 'true' : 'false');
      var input = document.createElement('input');
      input.type = 'checkbox';
      input.name = 'carrierIds';
      input.value = String(c.id);
      var name = document.createElement('span');
      name.textContent = c.name;
      label.appendChild(input);
      label.appendChild(name);
      if (c.csaEligible) {
        var tag = document.createElement('span');
        tag.className = 'entry-request-carrier__tag';
        tag.textContent = 'CSA';
        label.appendChild(tag);
      }
      input.addEventListener('change', function () {
        label.classList.toggle('is-selected', input.checked);
      });
      carrierList.appendChild(label);
    });
    if (!carrierList.children.length) {
      carrierList.innerHTML =
        '<p class="text-muted text-sm">Für CSA sind derzeit keine berechtigten Geräteträger vorhanden.</p>';
    }
  }

  function loadCarriers() {
    if (!unitId || !carrierList) {
      return;
    }
    carrierList.innerHTML = '<p class="text-muted text-sm">Lade Geräteträger…</p>';
    fetch('/atemschutz/antraege/api/carriers?unit=' + encodeURIComponent(unitId), {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' }
    })
      .then(function (res) {
        return res.json().then(function (data) {
          if (!res.ok) {
            throw new Error((data && data.error) || 'Laden fehlgeschlagen.');
          }
          return data;
        });
      })
      .then(function (data) {
        carriers = Array.isArray(data.carriers) ? data.carriers : [];
        if (dateInput && data.today) {
          dateInput.value = data.today;
        }
        renderCarriers();
      })
      .catch(function (err) {
        carrierList.innerHTML = '';
        setError(err.message || 'Geräteträger konnten nicht geladen werden.');
      });
  }

  openBtns.forEach(function (btn) {
    btn.addEventListener('click', function (event) {
      if (document.body.classList.contains('dashboard-editing')) {
        return;
      }
      openModal(event);
    });
  });
  if (cancelBtn) {
    cancelBtn.addEventListener('click', closeModal);
  }
  if (cancelFooterBtn) {
    cancelFooterBtn.addEventListener('click', closeModal);
  }
  overlay.addEventListener('click', function (event) {
    if (event.target === overlay) {
      closeModal();
    }
  });
  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && overlay.classList.contains('active')) {
      closeModal();
    }
  });
  if (typeSelect) {
    typeSelect.addEventListener('change', renderCarriers);
  }

  if (form) {
    form.addEventListener('submit', function (event) {
      event.preventDefault();
      setError('');
      var selected = Array.prototype.slice
        .call(form.querySelectorAll('input[name="carrierIds"]:checked'))
        .map(function (el) {
          return Number(el.value);
        })
        .filter(function (id) {
          return id > 0;
        });
      if (!typeSelect || !typeSelect.value) {
        setError('Bitte einen Eintragstyp wählen.');
        return;
      }
      if (!dateInput || !dateInput.value) {
        setError('Bitte ein Datum angeben.');
        return;
      }
      if (!selected.length) {
        setError('Bitte mindestens einen Geräteträger auswählen.');
        return;
      }
      if (submitBtn) {
        submitBtn.disabled = true;
      }
      fetch('/atemschutz/antraege/api/submit', {
        method: 'POST',
        credentials: 'same-origin',
        headers: csrfHeaders(),
        body: JSON.stringify({
          unitId: Number(unitId),
          entryType: typeSelect.value,
          eventDate: dateInput.value,
          carrierIds: selected
        })
      })
        .then(function (res) {
          return res.json().then(function (data) {
            if (!res.ok) {
              throw new Error((data && data.error) || 'Senden fehlgeschlagen.');
            }
            return data;
          });
        })
        .then(function () {
          closeModal();
          window.location.reload();
        })
        .catch(function (err) {
          setError(err.message || 'Antrag konnte nicht gesendet werden.');
        })
        .finally(function () {
          if (submitBtn) {
            submitBtn.disabled = false;
          }
        });
    });
  }

  initReviewCarrierToggle();

  function initReviewCarrierToggle() {
    var list = document.getElementById('review-carrier-list');
    if (!list) {
      return;
    }
    var typeSelectReview = document.getElementById('entryType');

    function syncCsaVisibility() {
      var csaOnly = typeSelectReview && typeSelectReview.value === 'CSA';
      list.querySelectorAll('.entry-request-carrier').forEach(function (label) {
        var eligible = label.getAttribute('data-csa') === 'true';
        var hide = csaOnly && !eligible;
        label.hidden = hide;
        if (hide) {
          var input = label.querySelector('input[type="checkbox"]');
          if (input) {
            input.checked = false;
            label.classList.remove('is-selected');
          }
        }
      });
    }

    list.querySelectorAll('.entry-request-carrier').forEach(function (label) {
      var input = label.querySelector('input[type="checkbox"]');
      if (!input) {
        return;
      }
      label.classList.toggle('is-selected', input.checked);
      input.addEventListener('change', function () {
        label.classList.toggle('is-selected', input.checked);
      });
    });
    if (typeSelectReview) {
      typeSelectReview.addEventListener('change', syncCsaVisibility);
      syncCsaVisibility();
    }
  }
})();
