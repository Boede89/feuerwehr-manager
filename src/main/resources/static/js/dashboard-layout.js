(function () {
  var meta = document.getElementById('dashboard-meta');
  var board = document.getElementById('dashboard-widgets');
  if (!meta || !board) return;

  var COLS = parseInt(board.getAttribute('data-cols') || '12', 10) || 12;
  var MIN_W = 2;
  var MIN_H = 3;
  var ROW_PX = 48;

  var editBtn = document.getElementById('dashboard-edit-btn');
  var doneBtn = document.getElementById('dashboard-done-btn');
  var addBtn = document.getElementById('dashboard-add-btn');
  var editHint = document.getElementById('dashboard-edit-hint');
  var addModal = document.getElementById('modal-dashboard-add');
  var atemschutzModal = document.getElementById('modal-dashboard-atemschutz-config');
  var emptyHint = document.getElementById('dashboard-widgets-empty');
  var editing = false;
  var interaction = null;
  var configTarget = null;

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
      var items = JSON.parse(el.textContent || '[]');
      return Array.isArray(items) ? items : [];
    } catch (e) {
      return [];
    }
  }

  function activeWidgetTypes() {
    var active = {};
    currentLayout().forEach(function (item) {
      if (item && item.type) active[item.type] = true;
    });
    // Auch Dom-Knoten ohne Layout-Eintrag (z. B. nur Platzhalter) berücksichtigen
    widgetNodes().forEach(function (node) {
      if (node.hasAttribute('data-removed')) return;
      var type = node.getAttribute('data-widget-type');
      if (type) active[type] = true;
    });
    return active;
  }

  function atemschutzDefaults() {
    var el = document.getElementById('atemschutz-widget-defaults');
    if (!el) {
      return {
        includePaused: false,
        metrics: [
          { key: 'total', show: true, showNames: false },
          { key: 'tauglich', show: true, showNames: false },
          { key: 'warnung', show: true, showNames: true },
          { key: 'uebungAbgelaufen', show: true, showNames: true },
          { key: 'nichtTauglich', show: true, showNames: true },
        ],
      };
    }
    try {
      return JSON.parse(el.textContent || '{}');
    } catch (e) {
      return { includePaused: false, metrics: [] };
    }
  }

  function parseConfig(raw) {
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch (e) {
      return null;
    }
  }

  function readConfig(node) {
    var parsed = parseConfig(node.getAttribute('data-config'));
    if (node.getAttribute('data-widget-type') === 'ATEMSCHUTZ') {
      return parsed && typeof parsed === 'object' ? parsed : atemschutzDefaults();
    }
    return parsed && typeof parsed === 'object' ? parsed : null;
  }

  function widgetNodes() {
    return Array.prototype.slice.call(board.querySelectorAll('.dashboard-widget'));
  }

  function readGeom(node) {
    return {
      x: parseInt(node.getAttribute('data-x') || '0', 10) || 0,
      y: parseInt(node.getAttribute('data-y') || '0', 10) || 0,
      w: parseInt(node.getAttribute('data-w') || '6', 10) || 6,
      h: parseInt(node.getAttribute('data-h') || '5', 10) || 5,
    };
  }

  function applyGeom(node, geom) {
    var x = Math.max(0, Math.min(COLS - MIN_W, geom.x));
    var w = Math.max(MIN_W, Math.min(COLS - x, geom.w));
    var y = Math.max(0, geom.y);
    var h = Math.max(MIN_H, Math.min(24, geom.h));
    if (x + w > COLS) {
      x = Math.max(0, COLS - w);
    }
    node.setAttribute('data-x', String(x));
    node.setAttribute('data-y', String(y));
    node.setAttribute('data-w', String(w));
    node.setAttribute('data-h', String(h));
    node.style.gridColumn = x + 1 + ' / span ' + w;
    node.style.gridRow = y + 1 + ' / span ' + h;
    updateBoardRows();
  }

  function currentLayout() {
    return widgetNodes()
      .filter(function (n) {
        return !n.hasAttribute('data-removed');
      })
      .map(function (n) {
        var g = readGeom(n);
        var item = {
          type: n.getAttribute('data-widget-type'),
          x: g.x,
          y: g.y,
          w: g.w,
          h: g.h,
        };
        var cfg = readConfig(n);
        if (cfg) item.config = cfg;
        return item;
      })
      .filter(function (item) {
        return !!item.type;
      });
  }

  function nextFreeRow() {
    var max = 0;
    currentLayout().forEach(function (item) {
      max = Math.max(max, item.y + item.h);
    });
    return max;
  }

  function updateBoardRows() {
    var max = 8;
    currentLayout().forEach(function (item) {
      max = Math.max(max, item.y + item.h + 2);
    });
    board.style.gridTemplateRows = 'repeat(' + max + ', ' + ROW_PX + 'px)';
    board.style.minHeight = max * ROW_PX + 'px';
  }

  function updateEmptyHint() {
    if (!emptyHint) return;
    emptyHint.hidden = currentLayout().length > 0;
  }

  function cellFromPoint(clientX, clientY) {
    var rect = board.getBoundingClientRect();
    var colW = rect.width / COLS;
    var x = Math.floor((clientX - rect.left) / colW);
    var y = Math.floor((clientY - rect.top) / ROW_PX);
    return {
      x: Math.max(0, Math.min(COLS - 1, x)),
      y: Math.max(0, y),
    };
  }

  function defaultsFor(type, row) {
    if (type === 'MY_STATS') return { x: 0, y: row, w: 6, h: 5 };
    if (type === 'TERMINE') return { x: 8, y: row, w: 4, h: 8 };
    if (type === 'UNIT_OVERVIEW') return { x: 0, y: row, w: 12, h: 5 };
    if (type === 'PLANNED_ALARMS') return { x: 0, y: row, w: 8, h: 7 };
    if (type === 'ATEMSCHUTZ') return { x: 0, y: row, w: 6, h: 10 };
    return { x: 0, y: row, w: 8, h: 8 };
  }

  function setEditing(on) {
    editing = !!on;
    document.body.classList.toggle('dashboard-editing', editing);
    if (editBtn) editBtn.hidden = editing;
    if (doneBtn) doneBtn.hidden = !editing;
    if (addBtn) addBtn.hidden = !editing;
    if (editHint) editHint.hidden = !editing;
    widgetNodes().forEach(function (node) {
      node.classList.toggle('dashboard-widget--editing', editing);
    });
  }

  function renderCatalog() {
    var list = document.getElementById('dashboard-catalog-list');
    if (!list) return;
    var active = activeWidgetTypes();
    var available = catalog().filter(function (item) {
      if (!item || !item.id) return false;
      if (active[item.id]) return false;
      if (item.alreadyActive === true) return false;
      return true;
    });
    if (available.length === 0) {
      list.innerHTML = '<p class="hint" style="margin:0;">Alle verfügbaren Widgets sind bereits auf der Startseite.</p>';
      return;
    }
    list.innerHTML = available
      .map(function (item) {
        return (
          '<button type="button" class="dashboard-catalog-item" data-widget-id="' +
          escapeHtml(item.id) +
          '">' +
          '<strong>' +
          escapeHtml(item.label) +
          '</strong>' +
          '<span>' +
          escapeHtml(item.description || '') +
          '</span>' +
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

  function fillAtemschutzConfigForm(cfg) {
    var include = document.getElementById('atemschutz-config-include-paused');
    if (include) include.checked = !!cfg.includePaused;
    var byKey = {};
    (cfg.metrics || []).forEach(function (m) {
      if (m && m.key) byKey[m.key] = m;
    });
    if (!atemschutzModal) return;
    atemschutzModal.querySelectorAll('[data-metric-key]').forEach(function (row) {
      var key = row.getAttribute('data-metric-key');
      var m = byKey[key] || {};
      var show = row.querySelector('[data-cfg="show"]');
      var names = row.querySelector('[data-cfg="showNames"]');
      if (show) show.checked = m.show !== false;
      if (names) names.checked = !!m.showNames;
    });
  }

  function readAtemschutzConfigForm() {
    var metrics = [];
    if (atemschutzModal) {
      atemschutzModal.querySelectorAll('[data-metric-key]').forEach(function (row) {
        var show = row.querySelector('[data-cfg="show"]');
        var names = row.querySelector('[data-cfg="showNames"]');
        metrics.push({
          key: row.getAttribute('data-metric-key'),
          show: !!(show && show.checked),
          showNames: !!(names && names.checked),
        });
      });
    }
    var include = document.getElementById('atemschutz-config-include-paused');
    return {
      includePaused: !!(include && include.checked),
      metrics: metrics,
    };
  }

  function openAtemschutzConfig(node) {
    configTarget = node;
    fillAtemschutzConfigForm(readConfig(node) || atemschutzDefaults());
    if (atemschutzModal) {
      atemschutzModal.classList.add('active');
      atemschutzModal.setAttribute('aria-hidden', 'false');
      document.body.classList.add('modal-open');
    }
  }

  function closeAtemschutzConfig() {
    configTarget = null;
    if (atemschutzModal) {
      atemschutzModal.classList.remove('active');
      atemschutzModal.setAttribute('aria-hidden', 'true');
      document.body.classList.remove('modal-open');
    }
  }

  function saveAtemschutzConfig() {
    if (!configTarget) return;
    configTarget.setAttribute('data-config', JSON.stringify(readAtemschutzConfigForm()));
    closeAtemschutzConfig();
    if (typeof window.toast === 'function') {
      window.toast('Einstellungen übernommen – mit „Fertig“ speichern');
    }
  }

  function ensureHandles(node) {
    if (node.querySelector('.dashboard-widget__handles')) return;
    var handles = document.createElement('div');
    handles.className = 'dashboard-widget__handles';
    handles.setAttribute('aria-hidden', 'true');
    ['e', 's', 'se', 'w', 'n', 'sw', 'ne', 'nw'].forEach(function (dir) {
      var span = document.createElement('span');
      span.className = 'dashboard-widget__handle dashboard-widget__handle--' + dir;
      span.setAttribute('data-resize', dir);
      handles.appendChild(span);
    });
    node.appendChild(handles);
  }

  function addWidget(type) {
    if (!type) return;
    if (currentLayout().some(function (item) { return item.type === type; })) return;
    var existing = board.querySelector('.dashboard-widget[data-widget-type="' + type + '"]');
    var geom = defaultsFor(type, nextFreeRow());
    if (existing) {
      existing.removeAttribute('data-removed');
      existing.hidden = false;
      existing.classList.remove('dashboard-widget--removed');
      applyGeom(existing, geom);
      existing.classList.add('dashboard-widget--editing');
      if (type === 'ATEMSCHUTZ' && !existing.getAttribute('data-config')) {
        existing.setAttribute('data-config', JSON.stringify(atemschutzDefaults()));
      }
    } else {
      var item = catalog().find(function (c) { return c.id === type; });
      var article = document.createElement('article');
      article.className = 'dashboard-widget widget-card dashboard-widget--placeholder dashboard-widget--editing';
      if (type === 'ATEMSCHUTZ') article.classList.add('widget-card--atemschutz');
      article.setAttribute('data-widget-type', type);
      if (type === 'ATEMSCHUTZ') {
        article.setAttribute('data-config', JSON.stringify(atemschutzDefaults()));
      }
      var configureBtn =
        type === 'ATEMSCHUTZ'
          ? '<button type="button" class="btn btn--outline btn--sm dashboard-widget__configure">Konfigurieren</button>'
          : '';
      article.innerHTML =
        '<div class="dashboard-widget__chrome">' +
        '<span class="dashboard-widget__drag" aria-hidden="true">⋮⋮</span>' +
        '<span class="dashboard-widget__chrome-actions">' +
        configureBtn +
        '<button type="button" class="btn btn--outline btn--sm dashboard-widget__remove">Entfernen</button>' +
        '</span></div>' +
        '<div class="widget-card__header"><h3>' +
        escapeHtml(item ? item.label : type) +
        '</h3></div>' +
        '<div class="widget-card__body"><p class="hint">Wird nach „Fertig“ geladen.</p></div>';
      ensureHandles(article);
      board.appendChild(article);
      applyGeom(article, geom);
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
    updateBoardRows();
    renderCatalog();
  }

  function saveAndReload() {
    var headers = {
      'Content-Type': 'application/json',
      'X-Requested-With': 'XMLHttpRequest',
    };
    var csrf = csrfToken();
    if (csrf) headers['X-XSRF-TOKEN'] = csrf;
    if (doneBtn) doneBtn.disabled = true;
    fetch('/dashboard/layout?unit=' + encodeURIComponent(unitId()), {
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
        if (typeof window.toast === 'function') window.toast('Startseite gespeichert');
        window.location.reload();
      })
      .catch(function (err) {
        if (typeof window.toast === 'function') window.toast(err.message || 'Fehler', 'error');
        if (doneBtn) doneBtn.disabled = false;
      });
  }

  function startMove(node, clientX, clientY) {
    var geom = readGeom(node);
    var cell = cellFromPoint(clientX, clientY);
    interaction = {
      mode: 'move',
      node: node,
      startGeom: geom,
      grabOffsetX: cell.x - geom.x,
      grabOffsetY: cell.y - geom.y,
    };
    node.classList.add('dashboard-widget--dragging');
  }

  function startResize(node, dir, clientX, clientY) {
    interaction = {
      mode: 'resize',
      node: node,
      dir: dir,
      startGeom: readGeom(node),
      startCell: cellFromPoint(clientX, clientY),
    };
    node.classList.add('dashboard-widget--resizing');
  }

  function onPointerMove(e) {
    if (!interaction) return;
    e.preventDefault();
    var cell = cellFromPoint(e.clientX, e.clientY);
    var g = interaction.startGeom;
    if (interaction.mode === 'move') {
      applyGeom(interaction.node, {
        x: cell.x - interaction.grabOffsetX,
        y: cell.y - interaction.grabOffsetY,
        w: g.w,
        h: g.h,
      });
      return;
    }
    var dir = interaction.dir;
    var x = g.x;
    var y = g.y;
    var w = g.w;
    var h = g.h;
    var right = g.x + g.w - 1;
    var bottom = g.y + g.h - 1;
    if (dir.indexOf('e') >= 0) {
      w = Math.max(MIN_W, cell.x - g.x + 1);
    }
    if (dir.indexOf('s') >= 0) {
      h = Math.max(MIN_H, cell.y - g.y + 1);
    }
    if (dir.indexOf('w') >= 0) {
      var newX = Math.min(cell.x, right - MIN_W + 1);
      newX = Math.max(0, newX);
      w = right - newX + 1;
      x = newX;
    }
    if (dir.indexOf('n') >= 0) {
      var newY = Math.min(cell.y, bottom - MIN_H + 1);
      newY = Math.max(0, newY);
      h = bottom - newY + 1;
      y = newY;
    }
    applyGeom(interaction.node, { x: x, y: y, w: w, h: h });
  }

  function onPointerUp() {
    if (!interaction) return;
    interaction.node.classList.remove('dashboard-widget--dragging', 'dashboard-widget--resizing');
    interaction = null;
  }

  board.addEventListener('pointerdown', function (e) {
    if (!editing) return;
    var configureBtn = e.target.closest('.dashboard-widget__configure');
    if (configureBtn) {
      e.preventDefault();
      e.stopPropagation();
      openAtemschutzConfig(configureBtn.closest('.dashboard-widget'));
      return;
    }
    var removeBtn = e.target.closest('.dashboard-widget__remove');
    if (removeBtn) {
      e.preventDefault();
      removeWidget(removeBtn.closest('.dashboard-widget'));
      return;
    }
    var handle = e.target.closest('[data-resize]');
    var widget = e.target.closest('.dashboard-widget');
    if (!widget || widget.hasAttribute('data-removed')) return;
    if (handle) {
      e.preventDefault();
      widget.setPointerCapture(e.pointerId);
      startResize(widget, handle.getAttribute('data-resize'), e.clientX, e.clientY);
      return;
    }
    if (e.target.closest('a, button, input, select, textarea, label')) return;
    e.preventDefault();
    widget.setPointerCapture(e.pointerId);
    startMove(widget, e.clientX, e.clientY);
  });

  board.addEventListener('pointermove', onPointerMove);
  board.addEventListener('pointerup', onPointerUp);
  board.addEventListener('pointercancel', onPointerUp);

  if (editBtn) {
    editBtn.addEventListener('click', function () {
      setEditing(true);
    });
  }
  if (doneBtn) {
    doneBtn.addEventListener('click', saveAndReload);
  }
  if (addBtn) {
    addBtn.addEventListener('click', openAddModal);
  }

  var catalogList = document.getElementById('dashboard-catalog-list');
  if (catalogList) {
    catalogList.addEventListener('click', function (e) {
      var btn = e.target.closest('.dashboard-catalog-item');
      if (!btn || btn.disabled) return;
      addWidget(btn.getAttribute('data-widget-id'));
      closeAddModal();
    });
  }

  if (addModal) {
    addModal.querySelectorAll('[data-close-modal]').forEach(function (btn) {
      btn.addEventListener('click', closeAddModal);
    });
    addModal.addEventListener('click', function (e) {
      if (e.target === addModal) closeAddModal();
    });
  }

  if (atemschutzModal) {
    atemschutzModal.querySelectorAll('[data-close-atemschutz-config]').forEach(function (btn) {
      btn.addEventListener('click', closeAtemschutzConfig);
    });
    atemschutzModal.addEventListener('click', function (e) {
      if (e.target === atemschutzModal) closeAtemschutzConfig();
    });
    var saveCfg = document.getElementById('atemschutz-config-save');
    if (saveCfg) saveCfg.addEventListener('click', saveAtemschutzConfig);
  }

  widgetNodes().forEach(ensureHandles);
  updateBoardRows();
  updateEmptyHint();
  setEditing(false);
  window.addEventListener('pageshow', function () {
    setEditing(false);
  });
})();
