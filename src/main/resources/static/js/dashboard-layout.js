(function () {
  var meta = document.getElementById('dashboard-meta');
  var board = document.getElementById('dashboard-widgets');
  if (!meta || !board) return;

  var SIZE_ORDER = ['NARROW', 'HALF', 'WIDE', 'FULL'];
  var SIZE_LABEL = {
    NARROW: 'Schmal',
    HALF: 'Halb',
    WIDE: 'Breit',
    FULL: 'Ganz',
  };

  var editBtn = document.getElementById('dashboard-edit-btn');
  var doneBtn = document.getElementById('dashboard-done-btn');
  var addBtn = document.getElementById('dashboard-add-btn');
  var editBar = document.getElementById('dashboard-edit-bar');
  var addModal = document.getElementById('modal-dashboard-add');
  var emptyHint = document.getElementById('dashboard-widgets-empty');
  var editing = false;
  var dragEl = null;

  function csrfToken() {
    var fromMeta = meta.getAttribute('data-csrf-token');
    if (fromMeta) return fromMeta;
    var match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : '';
  }

  function unitId() {
    return meta.getAttribute('data-unit-id') || board.getAttribute('data-unit-id') || '';
  }

  function catalog() {
    var el = document.getElementById('dashboard-catalog-json');
    if (!el) return [];
    try {
      return JSON.parse(el.textContent || '[]');
    } catch (e) {
      return [];
    }
  }

  function widgetNodes() {
    return Array.prototype.slice.call(board.querySelectorAll('.dashboard-widget'));
  }

  function defaultSizeFor(type) {
    if (type === 'TERMINE') return 'NARROW';
    if (type === 'MY_STATS') return 'HALF';
    if (type === 'UNIT_OVERVIEW') return 'FULL';
    return 'WIDE';
  }

  function currentLayout() {
    return widgetNodes()
      .filter(function (n) {
        return !n.hasAttribute('data-removed');
      })
      .map(function (n) {
        return {
          type: n.getAttribute('data-widget-type'),
          size: n.getAttribute('data-widget-size') || defaultSizeFor(n.getAttribute('data-widget-type')),
        };
      })
      .filter(function (item) {
        return !!item.type;
      });
  }

  function updateEmptyHint() {
    if (!emptyHint) return;
    emptyHint.hidden = currentLayout().length > 0;
  }

  function applySizeClass(node, size) {
    SIZE_ORDER.forEach(function (s) {
      node.classList.remove('dashboard-widget--size-' + s.toLowerCase());
    });
    node.classList.add('dashboard-widget--size-' + String(size).toLowerCase());
    node.setAttribute('data-widget-size', size);
    var btn = node.querySelector('.dashboard-widget__resize');
    if (btn) {
      btn.textContent = 'Größe: ' + (SIZE_LABEL[size] || size);
    }
  }

  function cycleSize(node) {
    var current = node.getAttribute('data-widget-size') || 'FULL';
    var idx = SIZE_ORDER.indexOf(current);
    var next = SIZE_ORDER[(idx + 1) % SIZE_ORDER.length];
    applySizeClass(node, next);
  }

  function setEditing(on) {
    editing = !!on;
    document.body.classList.toggle('dashboard-editing', editing);
    if (editBtn) editBtn.hidden = editing;
    if (doneBtn) doneBtn.hidden = !editing;
    if (editBar) editBar.hidden = !editing;
    widgetNodes().forEach(function (node) {
      var chrome = node.querySelector('.dashboard-widget__chrome');
      if (chrome) chrome.hidden = !editing;
      node.draggable = editing;
      node.classList.toggle('dashboard-widget--editing', editing);
    });
  }

  function renderCatalog() {
    var list = document.getElementById('dashboard-catalog-list');
    if (!list) return;
    var active = {};
    currentLayout().forEach(function (item) {
      active[item.type] = true;
    });
    var items = catalog();
    list.innerHTML = items
      .map(function (item) {
        var already = !!active[item.id];
        return (
          '<button type="button" class="dashboard-catalog-item' +
          (already ? ' dashboard-catalog-item--disabled' : '') +
          '" data-widget-id="' +
          item.id +
          '" ' +
          (already ? 'disabled' : '') +
          '>' +
          '<strong>' +
          escapeHtml(item.label) +
          '</strong>' +
          '<span>' +
          escapeHtml(item.description || '') +
          '</span>' +
          (already ? '<em>bereits aktiv</em>' : '') +
          '</button>'
        );
      })
      .join('');
  }

  function escapeHtml(text) {
    var d = document.createElement('div');
    d.textContent = text == null ? '' : String(text);
    return d.innerHTML;
  }

  function openAddModal() {
    renderCatalog();
    if (addModal) {
      addModal.classList.add('active');
      addModal.setAttribute('aria-hidden', 'false');
      document.body.classList.add('modal-open');
    }
  }

  function closeAddModal() {
    if (addModal) {
      addModal.classList.remove('active');
      addModal.setAttribute('aria-hidden', 'true');
      document.body.classList.remove('modal-open');
    }
  }

  function addWidget(type) {
    if (!type) return;
    var exists = currentLayout().some(function (item) {
      return item.type === type;
    });
    if (exists) return;
    var existing = board.querySelector('.dashboard-widget[data-widget-type="' + type + '"]');
    var size = defaultSizeFor(type);
    if (existing) {
      existing.removeAttribute('data-removed');
      existing.hidden = false;
      existing.classList.remove('dashboard-widget--removed');
      applySizeClass(existing, existing.getAttribute('data-widget-size') || size);
      board.appendChild(existing);
    } else {
      var item = catalog().find(function (c) {
        return c.id === type;
      });
      var article = document.createElement('article');
      article.className =
        'dashboard-widget widget-card dashboard-widget--placeholder dashboard-widget--editing dashboard-widget--size-' +
        size.toLowerCase();
      article.setAttribute('data-widget-type', type);
      article.setAttribute('data-widget-size', size);
      article.draggable = true;
      article.innerHTML =
        '<div class="dashboard-widget__chrome">' +
        '<span class="dashboard-widget__drag" aria-hidden="true">⋮⋮</span>' +
        '<div class="dashboard-widget__chrome-actions">' +
        '<button type="button" class="btn btn--outline btn--sm dashboard-widget__resize">Größe: ' +
        escapeHtml(SIZE_LABEL[size]) +
        '</button>' +
        '<button type="button" class="btn btn--outline btn--sm dashboard-widget__remove">Entfernen</button>' +
        '</div></div>' +
        '<div class="widget-card__header"><h3>' +
        escapeHtml(item ? item.label : type) +
        '</h3></div>' +
        '<div class="widget-card__body"><p class="hint">Wird nach „Fertig“ geladen.</p></div>';
      board.appendChild(article);
    }
    updateEmptyHint();
    renderCatalog();
  }

  function removeWidget(node) {
    if (!node) return;
    if (node.classList.contains('dashboard-widget--placeholder')) {
      node.remove();
    } else {
      node.setAttribute('data-removed', '1');
      node.hidden = true;
      node.classList.add('dashboard-widget--removed');
    }
    updateEmptyHint();
    renderCatalog();
  }

  function saveAndReload() {
    var headers = {
      'Content-Type': 'application/json',
      'X-Requested-With': 'XMLHttpRequest',
    };
    var csrf = csrfToken();
    if (csrf) headers['X-XSRF-TOKEN'] = csrf;
    var url = '/dashboard/layout?unit=' + encodeURIComponent(unitId());
    if (doneBtn) doneBtn.disabled = true;
    fetch(url, {
      method: 'POST',
      headers: headers,
      credentials: 'same-origin',
      body: JSON.stringify({ widgets: currentLayout() }),
    })
      .then(function (res) {
        if (!res.ok) {
          return res.json().then(function (data) {
            throw new Error(data.message || 'Speichern fehlgeschlagen');
          });
        }
        return res.json();
      })
      .then(function () {
        if (typeof window.toast === 'function') {
          window.toast('Startseite gespeichert');
        }
        window.location.reload();
      })
      .catch(function (err) {
        if (typeof window.toast === 'function') {
          window.toast(err.message || 'Fehler', 'error');
        }
        if (doneBtn) doneBtn.disabled = false;
      });
  }

  if (editBtn) {
    editBtn.addEventListener('click', function () {
      setEditing(true);
    });
  }
  if (doneBtn) {
    doneBtn.addEventListener('click', function () {
      saveAndReload();
    });
  }
  if (addBtn) {
    addBtn.addEventListener('click', openAddModal);
  }

  board.addEventListener('click', function (e) {
    var removeBtn = e.target.closest('.dashboard-widget__remove');
    if (removeBtn && editing) {
      e.preventDefault();
      removeWidget(removeBtn.closest('.dashboard-widget'));
      return;
    }
    var resizeBtn = e.target.closest('.dashboard-widget__resize');
    if (resizeBtn && editing) {
      e.preventDefault();
      cycleSize(resizeBtn.closest('.dashboard-widget'));
    }
  });

  var catalogList = document.getElementById('dashboard-catalog-list');
  if (catalogList) {
    catalogList.addEventListener('click', function (e) {
      var btn = e.target.closest('.dashboard-catalog-item');
      if (!btn || btn.disabled) return;
      addWidget(btn.getAttribute('data-widget-id'));
      closeAddModal();
    });
  }

  board.addEventListener('dragstart', function (e) {
    if (!editing) return;
    var widget = e.target.closest('.dashboard-widget');
    if (!widget || widget.hasAttribute('data-removed')) return;
    dragEl = widget;
    widget.classList.add('dashboard-widget--dragging');
    e.dataTransfer.effectAllowed = 'move';
    try {
      e.dataTransfer.setData('text/plain', widget.getAttribute('data-widget-type') || '');
    } catch (err) {
      /* ignore */
    }
  });

  board.addEventListener('dragend', function () {
    if (dragEl) dragEl.classList.remove('dashboard-widget--dragging');
    dragEl = null;
  });

  board.addEventListener('dragover', function (e) {
    if (!editing || !dragEl) return;
    e.preventDefault();
    var target = e.target.closest('.dashboard-widget');
    if (!target || target === dragEl || target.hasAttribute('data-removed')) return;
    var rect = target.getBoundingClientRect();
    var before = e.clientY < rect.top + rect.height / 2;
    if (before) {
      board.insertBefore(dragEl, target);
    } else {
      board.insertBefore(dragEl, target.nextSibling);
    }
  });

  if (addModal) {
    addModal.querySelectorAll('[data-close-modal]').forEach(function (btn) {
      btn.addEventListener('click', closeAddModal);
    });
    addModal.addEventListener('click', function (e) {
      if (e.target === addModal) closeAddModal();
    });
  }

  updateEmptyHint();
})();
