(function () {
  function onReady(fn) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', fn);
    } else {
      fn();
    }
  }

  function csrfToken() {
    var meta = document.querySelector('meta[name="csrf-token"]');
    return meta ? meta.getAttribute('content') : '';
  }

  function csrfParam() {
    var meta = document.querySelector('meta[name="csrf-param"]');
    return meta ? meta.getAttribute('content') : '_csrf';
  }

  function esc(text) {
    return String(text == null ? '' : text)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function submitReorder(table) {
    var url = table.getAttribute('data-reorder-url');
    if (!url) return;
    var form = document.createElement('form');
    form.method = 'post';
    form.action = url;
    function hidden(name, value) {
      var input = document.createElement('input');
      input.type = 'hidden';
      input.name = name;
      input.value = value;
      form.appendChild(input);
    }
    hidden(csrfParam(), csrfToken());
    hidden('unit', table.getAttribute('data-unit') || '');
    hidden('jahr', table.getAttribute('data-jahr') || '');
    table.querySelectorAll('tbody tr[data-entry-id]').forEach(function (row) {
      hidden('entryIds', row.getAttribute('data-entry-id'));
    });
    document.body.appendChild(form);
    form.submit();
  }

  function bindDrag(table) {
    if (table.getAttribute('data-can-write') !== 'true') return;
    var tbody = table.querySelector('tbody');
    if (!tbody) return;
    var dragged = null;

    tbody.querySelectorAll('tr[data-entry-id]').forEach(function (row) {
      row.addEventListener('dragstart', function (e) {
        if (e.target.closest('a, button, input, label, form')) {
          e.preventDefault();
          return;
        }
        dragged = row;
        row.classList.add('course-detail-row--dragging');
        if (e.dataTransfer) {
          e.dataTransfer.effectAllowed = 'move';
          e.dataTransfer.setData('text/plain', row.getAttribute('data-entry-id') || '');
        }
      });
      row.addEventListener('dragend', function () {
        row.classList.remove('course-detail-row--dragging');
        dragged = null;
      });
      row.addEventListener('dragover', function (e) {
        if (!dragged || dragged === row) return;
        e.preventDefault();
        var rect = row.getBoundingClientRect();
        var before = e.clientY < rect.top + rect.height / 2;
        tbody.insertBefore(dragged, before ? row : row.nextSibling);
      });
      row.addEventListener('drop', function (e) {
        e.preventDefault();
        if (dragged) {
          submitReorder(table);
        }
      });
    });
  }

  function itemIdFromHash() {
    var hash = (window.location.hash || '').replace(/^#/, '');
    if (hash.indexOf('item-') === 0) {
      return hash.slice(5);
    }
    return '';
  }

  function applyFilters() {
    var q = (document.getElementById('course-detail-search') || {}).value || '';
    q = q.trim().toLowerCase();
    var seatsOnly = !!(document.getElementById('course-detail-seats-only') || {}).checked;
    document.querySelectorAll('.course-detail-panel.is-active tbody tr[data-entry-id]').forEach(function (row) {
      var hay = (row.getAttribute('data-search') || '').toLowerCase();
      var matchSearch = !q || hay.indexOf(q) !== -1;
      var matchSeats = !seatsOnly || row.classList.contains('course-detail-row--seat');
      row.hidden = !(matchSearch && matchSeats);
    });
  }

  function activateItem(itemId) {
    var buttons = document.querySelectorAll('.course-detail-nav-btn');
    var panels = document.querySelectorAll('.course-detail-panel');
    if (!buttons.length || !panels.length) return;
    var found = false;
    buttons.forEach(function (btn) {
      var active = String(btn.getAttribute('data-item-id')) === String(itemId);
      btn.classList.toggle('is-active', active);
      if (active) found = true;
    });
    if (!found) {
      itemId = buttons[0].getAttribute('data-item-id');
      buttons[0].classList.add('is-active');
    }
    panels.forEach(function (panel) {
      var id = panel.getAttribute('data-item-id') || (panel.id || '').replace(/^item-/, '');
      var active = String(id) === String(itemId);
      panel.classList.toggle('is-active', active);
    });
    var activeBtn = document.querySelector('.course-detail-nav-btn.is-active');
    var nav = document.querySelector('.course-detail-nav');
    if (activeBtn && nav) {
      var btnRect = activeBtn.getBoundingClientRect();
      var navRect = nav.getBoundingClientRect();
      if (btnRect.top < navRect.top) {
        nav.scrollTop += btnRect.top - navRect.top - 8;
      } else if (btnRect.bottom > navRect.bottom) {
        nav.scrollTop += btnRect.bottom - navRect.bottom + 8;
      }
    }
    if (itemId && history.replaceState) {
      var url = window.location.pathname + window.location.search + '#item-' + itemId;
      history.replaceState(null, '', url);
    }
    applyFilters();
  }

  function bindAssignModal() {
    var modal = document.getElementById('course-assign-modal');
    var form = document.getElementById('course-assign-form');
    var tbody = document.getElementById('course-assign-tbody');
    var meta = document.getElementById('course-assign-meta');
    var search = document.getElementById('course-assign-search');
    var showAll = document.getElementById('course-assign-show-all');
    var selectAll = document.getElementById('course-assign-select-all');
    var submitBtn = document.getElementById('course-assign-submit');
    var selectionHint = document.getElementById('course-assign-selection');
    var participationCol = document.getElementById('course-assign-participation-col');
    var labelOn = document.getElementById('course-assign-prereq-label-on');
    var labelOff = document.getElementById('course-assign-prereq-label-off');
    if (!modal || !form || !tbody) {
      return;
    }

    var state = {
      candidatesUrl: '',
      assignUrl: '',
      courseName: '',
      seats: 0,
      assigned: 0,
      useParticipation: false,
      candidates: []
    };

    function selectedCount() {
      return tbody.querySelectorAll('input[name="personIds"]:checked').length;
    }

    function updateSelection() {
      var count = selectedCount();
      var openSeats = Math.max(0, state.seats - state.assigned);
      if (selectionHint) {
        var text = count + ' ausgewählt';
        if (count > 0 && openSeats <= 0) {
          text += ' · werden als Nachrücker zugewiesen';
        } else if (count > openSeats && openSeats > 0) {
          text += ' · ' + openSeats + ' Plätze, Rest als Nachrücker';
        } else if (count > 0 && openSeats > 0) {
          text += ' · noch ' + openSeats + ' Plätze frei';
        }
        selectionHint.textContent = text;
      }
      if (submitBtn) {
        submitBtn.disabled = count === 0;
      }
      if (selectAll) {
        var boxes = tbody.querySelectorAll('input[name="personIds"]');
        var visible = Array.prototype.filter.call(boxes, function (box) {
          return !box.closest('tr') || !box.closest('tr').hidden;
        });
        var checkedVisible = visible.filter(function (box) { return box.checked; });
        selectAll.checked = visible.length > 0 && checkedVisible.length === visible.length;
        selectAll.indeterminate = checkedVisible.length > 0 && checkedVisible.length < visible.length;
      }
    }

    function syncPrereqLabels() {
      var all = !!(showAll && showAll.checked);
      if (labelOn) {
        labelOn.classList.toggle('toolbar-toggle__label--active', !all);
      }
      if (labelOff) {
        labelOff.classList.toggle('toolbar-toggle__label--active', all);
      }
    }

    function applyCandidateSearch() {
      var q = ((search && search.value) || '').trim().toLowerCase();
      tbody.querySelectorAll('tr[data-search]').forEach(function (row) {
        var hay = (row.getAttribute('data-search') || '').toLowerCase();
        row.hidden = !!(q && hay.indexOf(q) === -1);
      });
      updateSelection();
    }

    function renderCandidates(payload) {
      state.candidates = payload && payload.candidates ? payload.candidates : [];
      state.useParticipation = !!(payload && payload.useParticipation);
      state.seats = payload && payload.seats != null ? Number(payload.seats) : state.seats;
      state.assigned = payload && payload.assignedCount != null ? Number(payload.assignedCount) : state.assigned;
      if (participationCol) {
        participationCol.hidden = !state.useParticipation;
      }
      if (meta) {
        var openSeats = Math.max(0, state.seats - state.assigned);
        var parts = [
          state.courseName || (payload && payload.courseName) || 'Lehrgang',
          state.assigned + '/' + state.seats + ' Plätze belegt'
        ];
        if (openSeats <= 0) {
          parts.push('weitere Zuweisungen werden Nachrücker');
        } else {
          parts.push(openSeats + ' frei');
        }
        meta.textContent = parts.join(' · ');
      }
      if (!state.candidates.length) {
        tbody.innerHTML = '<tr><td colspan="4" class="hint">Keine passenden Personen gefunden.</td></tr>';
        updateSelection();
        return;
      }
      tbody.innerHTML = state.candidates.map(function (row) {
        var prereq = row.prerequisitesMet
          ? '<span class="course-plan-status course-plan-status--ok" title="Voraussetzungen erfüllt">✓</span>'
          : esc(row.missingPrerequisitesLabel || '—');
        var participation = state.useParticipation
          ? '<td>' + esc(row.dienstbeteiligung || '—') + '</td>'
          : '';
        var search = [
          row.name || '',
          row.missingPrerequisitesLabel || ''
        ].join(' ');
        return (
          '<tr data-search="' + esc(search) + '">' +
            '<td class="course-assign-check-col">' +
              '<input type="checkbox" name="personIds" value="' + esc(row.personId) + '"' +
              ' aria-label="' + esc(row.name || 'Person') + ' auswählen"/>' +
            '</td>' +
            '<td>' + esc(row.name || '—') + '</td>' +
            participation +
            '<td class="text-muted text-sm">' + prereq + '</td>' +
          '</tr>'
        );
      }).join('');
      if (selectAll) {
        selectAll.checked = false;
        selectAll.indeterminate = false;
      }
      applyCandidateSearch();
    }

    function loadCandidates() {
      if (!state.candidatesUrl) {
        return;
      }
      tbody.innerHTML = '<tr><td colspan="4" class="hint">Liste wird geladen…</td></tr>';
      if (submitBtn) {
        submitBtn.disabled = true;
      }
      var only = showAll && showAll.checked ? 'false' : 'true';
      var url = state.candidatesUrl
        + (state.candidatesUrl.indexOf('?') >= 0 ? '&' : '?')
        + 'onlyWithPrerequisites=' + encodeURIComponent(only);
      fetch(url, {
        headers: { 'Accept': 'application/json' },
        credentials: 'same-origin'
      }).then(function (res) {
        if (!res.ok) {
          throw new Error('Laden fehlgeschlagen');
        }
        return res.json();
      }).then(function (payload) {
        if (payload && payload.error) {
          throw new Error(payload.error);
        }
        renderCandidates(payload || {});
      }).catch(function (err) {
        tbody.innerHTML = '<tr><td colspan="4" class="hint">' +
          esc(err && err.message ? err.message : 'Liste konnte nicht geladen werden.') +
          '</td></tr>';
        updateSelection();
      });
    }

    function openModal(trigger) {
      state.candidatesUrl = trigger.getAttribute('data-candidates-url') || '';
      state.assignUrl = trigger.getAttribute('data-assign-url') || '';
      state.courseName = trigger.getAttribute('data-course-name') || '';
      state.seats = Number(trigger.getAttribute('data-seats') || 0);
      state.assigned = Number(trigger.getAttribute('data-assigned') || 0);
      form.action = state.assignUrl;
      var title = document.getElementById('course-assign-modal-title');
      if (title) {
        title.textContent = 'Teilnehmer: ' + (state.courseName || 'Lehrgang');
      }
      if (search) {
        search.value = '';
      }
      if (showAll) {
        showAll.checked = false;
      }
      syncPrereqLabels();
      modal.hidden = false;
      modal.setAttribute('aria-hidden', 'false');
      modal.style.display = '';
      loadCandidates();
      if (search) {
        setTimeout(function () { search.focus(); }, 0);
      }
    }

    function closeModal() {
      modal.hidden = true;
      modal.setAttribute('aria-hidden', 'true');
      modal.style.display = 'none';
      state.candidates = [];
      tbody.innerHTML = '';
      if (selectAll) {
        selectAll.checked = false;
        selectAll.indeterminate = false;
      }
      updateSelection();
    }

    document.querySelectorAll('[data-course-assign-open]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        openModal(btn);
      });
    });
    modal.querySelectorAll('[data-course-assign-close]').forEach(function (el) {
      el.addEventListener('click', closeModal);
    });
    document.addEventListener('keydown', function (ev) {
      if (ev.key === 'Escape' && !modal.hidden) {
        closeModal();
      }
    });
    if (showAll) {
      showAll.addEventListener('change', function () {
        syncPrereqLabels();
        loadCandidates();
      });
    }
    if (search) {
      search.addEventListener('input', applyCandidateSearch);
    }
    if (selectAll) {
      selectAll.addEventListener('change', function () {
        var checked = selectAll.checked;
        tbody.querySelectorAll('tr[data-search]').forEach(function (row) {
          if (row.hidden) return;
          var box = row.querySelector('input[name="personIds"]');
          if (box) box.checked = checked;
        });
        updateSelection();
      });
    }
    tbody.addEventListener('change', function (ev) {
      if (ev.target && ev.target.name === 'personIds') {
        updateSelection();
      }
    });
    form.addEventListener('submit', function (ev) {
      if (selectedCount() === 0) {
        ev.preventDefault();
      }
    });
  }

  onReady(function () {
    document.querySelectorAll('form[data-confirm]').forEach(function (form) {
      form.addEventListener('submit', function (e) {
        var msg = form.getAttribute('data-confirm');
        if (msg && !window.confirm(msg)) e.preventDefault();
      });
    });

    document.querySelectorAll('.course-detail-entry-table').forEach(bindDrag);

    var nav = document.querySelector('.course-detail-nav');
    if (nav) {
      if ('scrollRestoration' in history) {
        history.scrollRestoration = 'manual';
      }
      nav.addEventListener('click', function (e) {
        var btn = e.target.closest('.course-detail-nav-btn');
        if (!btn || !nav.contains(btn)) return;
        activateItem(btn.getAttribute('data-item-id'));
      });
      activateItem(itemIdFromHash());
      window.scrollTo(0, 0);
    }

    var search = document.getElementById('course-detail-search');
    if (search) search.addEventListener('input', applyFilters);
    var seatsOnly = document.getElementById('course-detail-seats-only');
    if (seatsOnly) seatsOnly.addEventListener('change', applyFilters);

    document.querySelectorAll('.course-seat-tile').forEach(function (tile) {
      var box = tile.querySelector('input[type="checkbox"]');
      var seats = tile.querySelector('input[type="number"]');
      function sync() {
        tile.classList.toggle('is-selected', !!(box && box.checked));
      }
      if (box) box.addEventListener('change', sync);
      if (seats && box) {
        seats.addEventListener('input', function () {
          if (parseInt(seats.value, 10) > 0) box.checked = true;
          sync();
        });
      }
    });

    bindAssignModal();
  });
})();
