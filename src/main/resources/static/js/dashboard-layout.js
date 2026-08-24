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
  var openReportsModal = document.getElementById('modal-dashboard-open-reports-config');
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
    var items = [];
    if (el) {
      try {
        var raw = (el.textContent || '').trim();
        // Falls Thymeleaf Quotes als &quot; escaped hat
        if (raw.indexOf('&quot;') >= 0) {
          raw = raw.replace(/&quot;/g, '"').replace(/&amp;/g, '&').replace(/&#39;/g, "'");
        }
        items = JSON.parse(raw || '[]');
      } catch (e) {
        items = [];
      }
    }
    if (!Array.isArray(items)) items = [];
    var builtins = builtinCatalog();
    builtins.forEach(function (fallback) {
      ensureCatalogFallback(items, fallback.id, fallback.label, fallback.description);
    });
    // alreadyActive vom Server ist nur der Seitenstand beim Laden —
    // maßgeblich ist der aktuelle DOM (activeWidgetTypes).
    return items.map(function (item) {
      if (!item || typeof item !== 'object') return item;
      return {
        id: item.id,
        label: item.label,
        description: item.description,
        alreadyActive: false,
      };
    });
  }

  function builtinCatalog() {
    return [
      {
        id: 'MY_STATS',
        label: 'Meine Beteiligung',
        description: 'Persönliche Übungsdienst- und Einsatzquote im laufenden Jahr',
      },
      {
        id: 'DIVERA',
        label: 'Aktuelle Einsätze',
        description: 'Laufende Einsätze aus Divera (und manuelle Alarme)',
      },
      {
        id: 'TERMINE',
        label: 'Meine Termine',
        description: 'Kommende Termine Ihrer Person',
      },
      {
        id: 'PLANNED_ALARMS',
        label: 'Geplante Einsätze',
        description: 'Noch nicht gestartete manuelle Einsätze',
      },
      {
        id: 'UNIT_OVERVIEW',
        label: 'Einheiten-Kennzahlen',
        description: 'Einsätze, Übungsdienste und Mitglieder der Einheit',
      },
      {
        id: 'ATEMSCHUTZ',
        label: 'Atemschutz',
        description: 'Tauglichkeiten der Geräteträger - Zahlen und optional Namen',
      },
      {
        id: 'OPEN_REPORTS',
        label: 'Offene Berichte',
        description: 'Noch nicht freigegebene Einsatzberichte und Anwesenheitslisten',
      },
    ];
  }

  function ensureCatalogFallback(items, id, label, description) {
    var exists = items.some(function (item) {
      return item && item.id === id;
    });
    if (!exists) {
      items.push({
        id: id,
        label: label,
        description: description,
        alreadyActive: false,
      });
    }
  }

  function activeWidgetTypes() {
    var active = {};
    widgetNodes().forEach(function (node) {
      if (node.hasAttribute('data-removed') || node.hidden) return;
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
          { key: 'csa', show: true, showNames: true },
        ],
      };
    }
    try {
      return JSON.parse(el.textContent || '{}');
    } catch (e) {
      return { includePaused: false, metrics: [] };
    }
  }

  function openReportsDefaults() {
    var el = document.getElementById('open-reports-widget-defaults');
    if (!el) {
      return {
        showEinsatzberichte: true,
        showAnwesenheitslisten: true,
        anwesenheitOnlyUntilToday: true,
        limit: 15,
        openInEdit: true,
      };
    }
    try {
      return JSON.parse(el.textContent || '{}');
    } catch (e) {
      return {
        showEinsatzberichte: true,
        showAnwesenheitslisten: true,
        anwesenheitOnlyUntilToday: true,
        limit: 15,
        openInEdit: true,
      };
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
    var type = node.getAttribute('data-widget-type');
    if (type === 'ATEMSCHUTZ') {
      return parsed && typeof parsed === 'object' ? parsed : atemschutzDefaults();
    }
    if (type === 'OPEN_REPORTS') {
      return parsed && typeof parsed === 'object' ? parsed : openReportsDefaults();
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
    if (type === 'OPEN_REPORTS') return { x: 0, y: row, w: 6, h: 8 };
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

  function fillOpenReportsConfigForm(cfg) {
    var einsatz = document.getElementById('open-reports-cfg-einsatz');
    var anwesenheit = document.getElementById('open-reports-cfg-anwesenheit');
    var untilToday = document.getElementById('open-reports-cfg-until-today');
    var openEdit = document.getElementById('open-reports-cfg-edit');
    var limit = document.getElementById('open-reports-cfg-limit');
    if (einsatz) einsatz.checked = cfg.showEinsatzberichte !== false;
    if (anwesenheit) anwesenheit.checked = cfg.showAnwesenheitslisten !== false;
    if (untilToday) untilToday.checked = cfg.anwesenheitOnlyUntilToday !== false;
    if (openEdit) openEdit.checked = cfg.openInEdit !== false;
    if (limit) limit.value = String(cfg.limit != null ? cfg.limit : 15);
  }

  function readOpenReportsConfigForm() {
    var einsatz = document.getElementById('open-reports-cfg-einsatz');
    var anwesenheit = document.getElementById('open-reports-cfg-anwesenheit');
    var untilToday = document.getElementById('open-reports-cfg-until-today');
    var openEdit = document.getElementById('open-reports-cfg-edit');
    var limit = document.getElementById('open-reports-cfg-limit');
    var parsedLimit = limit ? parseInt(limit.value, 10) : 15;
    if (isNaN(parsedLimit)) parsedLimit = 15;
    parsedLimit = Math.max(1, Math.min(50, parsedLimit));
    return {
      showEinsatzberichte: !!(einsatz && einsatz.checked),
      showAnwesenheitslisten: !!(anwesenheit && anwesenheit.checked),
      anwesenheitOnlyUntilToday: !!(untilToday && untilToday.checked),
      openInEdit: !!(openEdit && openEdit.checked),
      limit: parsedLimit,
    };
  }

  function openOpenReportsConfig(node) {
    configTarget = node;
    fillOpenReportsConfigForm(readConfig(node) || openReportsDefaults());
    if (openReportsModal) {
      openReportsModal.classList.add('active');
      openReportsModal.setAttribute('aria-hidden', 'false');
      document.body.classList.add('modal-open');
    }
  }

  function closeOpenReportsConfig() {
    configTarget = null;
    if (openReportsModal) {
      openReportsModal.classList.remove('active');
      openReportsModal.setAttribute('aria-hidden', 'true');
      document.body.classList.remove('modal-open');
    }
  }

  function saveOpenReportsConfig() {
    if (!configTarget) return;
    configTarget.setAttribute('data-config', JSON.stringify(readOpenReportsConfigForm()));
    closeOpenReportsConfig();
    if (typeof window.toast === 'function') {
      window.toast('Einstellungen übernommen – mit „Fertig“ speichern');
    }
  }

  function openWidgetConfig(node) {
    if (!node) return;
    var type = node.getAttribute('data-widget-type');
    if (type === 'ATEMSCHUTZ') {
      openAtemschutzConfig(node);
      return;
    }
    if (type === 'OPEN_REPORTS') {
      openOpenReportsConfig(node);
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
    if (activeWidgetTypes()[type]) return;
    var existing = board.querySelector(
      '.dashboard-widget[data-widget-type="' + type + '"]:not([data-removed])'
    );
    var geom = defaultsFor(type, nextFreeRow());
    if (existing && !existing.hidden) {
      applyGeom(existing, geom);
      existing.classList.add('dashboard-widget--editing');
      if ((type === 'ATEMSCHUTZ' || type === 'OPEN_REPORTS') && !existing.getAttribute('data-config')) {
        existing.setAttribute(
          'data-config',
          JSON.stringify(type === 'ATEMSCHUTZ' ? atemschutzDefaults() : openReportsDefaults())
        );
      }
    } else {
      if (existing) existing.remove();
      var item = catalog().find(function (c) { return c.id === type; });
      var labels = {
        MY_STATS: 'Meine Beteiligung',
        DIVERA: 'Aktuelle Einsätze',
        TERMINE: 'Meine Termine',
        PLANNED_ALARMS: 'Geplante Einsätze',
        UNIT_OVERVIEW: 'Einheiten-Kennzahlen',
        ATEMSCHUTZ: 'Atemschutz',
        OPEN_REPORTS: 'Offene Berichte',
      };
      var article = document.createElement('article');
      article.className = 'dashboard-widget widget-card dashboard-widget--placeholder dashboard-widget--editing';
      if (type === 'MY_STATS') article.classList.add('widget-card--meine-statistik');
      if (type === 'DIVERA' || type === 'PLANNED_ALARMS') article.classList.add('widget-card--einsatz');
      if (type === 'TERMINE') article.classList.add('widget-card--termine');
      if (type === 'UNIT_OVERVIEW') article.classList.add('widget-card--unit-overview');
      if (type === 'ATEMSCHUTZ') article.classList.add('widget-card--atemschutz');
      if (type === 'OPEN_REPORTS') article.classList.add('widget-card--open-reports');
      article.setAttribute('data-widget-type', type);
      if (type === 'ATEMSCHUTZ') {
        article.setAttribute('data-config', JSON.stringify(atemschutzDefaults()));
      }
      if (type === 'OPEN_REPORTS') {
        article.setAttribute('data-config', JSON.stringify(openReportsDefaults()));
      }
      var configureBtn =
        type === 'ATEMSCHUTZ' || type === 'OPEN_REPORTS'
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
        escapeHtml((item && item.label) || labels[type] || type) +
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
    // Sofort aus dem Raster entfernen (hidden reicht nicht: display:flex überschreibt [hidden]).
    node.remove();
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
      openWidgetConfig(configureBtn.closest('.dashboard-widget'));
      return;
    }
    var removeBtn = e.target.closest('.dashboard-widget__remove');
    if (removeBtn) {
      e.preventDefault();
      e.stopPropagation();
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

  // Klick zusätzlich absichern (falls Pointer-Events an Handles „kleben“)
  board.addEventListener('click', function (e) {
    if (!editing) return;
    var configureBtn = e.target.closest('.dashboard-widget__configure');
    if (configureBtn) {
      e.preventDefault();
      e.stopPropagation();
      openWidgetConfig(configureBtn.closest('.dashboard-widget'));
      return;
    }
    var removeBtn = e.target.closest('.dashboard-widget__remove');
    if (removeBtn) {
      e.preventDefault();
      e.stopPropagation();
      removeWidget(removeBtn.closest('.dashboard-widget'));
    }
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

  if (openReportsModal) {
    openReportsModal.querySelectorAll('[data-close-open-reports-config]').forEach(function (btn) {
      btn.addEventListener('click', closeOpenReportsConfig);
    });
    openReportsModal.addEventListener('click', function (e) {
      if (e.target === openReportsModal) closeOpenReportsConfig();
    });
    var saveOpenReports = document.getElementById('open-reports-config-save');
    if (saveOpenReports) saveOpenReports.addEventListener('click', saveOpenReportsConfig);
  }

  widgetNodes().forEach(ensureHandles);
  updateBoardRows();
  updateEmptyHint();
  setEditing(false);
  window.addEventListener('pageshow', function () {
    setEditing(false);
  });
})();
