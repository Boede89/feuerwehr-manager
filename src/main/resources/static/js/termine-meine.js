(function () {
  'use strict';

  var panel = document.getElementById('termine-meine-panel');
  if (!panel) {
    return;
  }

  var zeitFilter = 'bevorstehend';
  var activeView = 'list';
  var calendarMonth = null;
  var calendarYear = null;
  var selectedDay = null;
  var termine = loadTermine();

  function loadTermine() {
    var el = document.getElementById('termine-meine-data');
    if (!el) {
      return [];
    }
    try {
      var parsed = JSON.parse(el.textContent || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch (e) {
      return [];
    }
  }

  function parseTerminEnd(row) {
    var datum = row.getAttribute('data-datum');
    var ende = row.getAttribute('data-ende') || '23:59';
    if (!datum) {
      return null;
    }
    var dateParts = datum.split('-');
    var timeParts = ende.split(':');
    if (dateParts.length !== 3) {
      return null;
    }
    return new Date(
      Number(dateParts[0]),
      Number(dateParts[1]) - 1,
      Number(dateParts[2]),
      Number(timeParts[0] || 0),
      Number(timeParts[1] || 0)
    );
  }

  function parseIsoDate(value) {
    if (!value) {
      return null;
    }
    var date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  function isTerminVergangen(row) {
    var endAt = parseTerminEnd(row);
    if (!endAt) {
      return false;
    }
    return endAt.getTime() < Date.now();
  }

  function rowMatchesZeitFilter(row) {
    if (zeitFilter === 'alle') {
      return true;
    }
    var vergangen = isTerminVergangen(row);
    if (zeitFilter === 'vergangen') {
      return vergangen;
    }
    return !vergangen;
  }

  function applyZeitFilter() {
    var rows = document.querySelectorAll('.meine-termin-row');
    var visibleCount = 0;
    rows.forEach(function (row) {
      var show = rowMatchesZeitFilter(row);
      row.hidden = !show;
      if (show) {
        visibleCount++;
      }
    });
    var emptyHint = document.getElementById('meine-filter-empty');
    var tableWrap = document.getElementById('meine-table-wrap');
    if (emptyHint) {
      emptyHint.hidden = visibleCount > 0;
    }
    if (tableWrap) {
      tableWrap.hidden = visibleCount === 0;
    }
  }

  function bindZeitFilter() {
    var select = document.getElementById('meine-filter-zeit');
    if (!select) {
      return;
    }
    zeitFilter = select.value || 'bevorstehend';
    select.addEventListener('change', function () {
      zeitFilter = select.value || 'alle';
      applyZeitFilter();
    });
    applyZeitFilter();
  }

  function setActiveView(view) {
    activeView = view === 'calendar' ? 'calendar' : 'list';
    var listWrap = document.getElementById('meine-list-wrap');
    var calendarWrap = document.getElementById('meine-calendar-wrap');
    if (listWrap) {
      listWrap.hidden = activeView !== 'list';
    }
    if (calendarWrap) {
      calendarWrap.hidden = activeView !== 'calendar';
    }
    document.querySelectorAll('[data-meine-view]').forEach(function (btn) {
      var isActive = btn.getAttribute('data-meine-view') === activeView;
      btn.classList.toggle('tab-btn--active', isActive);
    });
    if (activeView === 'calendar') {
      initCalendarMonth();
      renderCalendar();
    }
  }

  function bindViewToggle() {
    document.querySelectorAll('[data-meine-view]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        setActiveView(btn.getAttribute('data-meine-view'));
      });
    });
  }

  function initCalendarMonth() {
    if (calendarMonth !== null && calendarYear !== null) {
      return;
    }
    var now = new Date();
    calendarMonth = now.getMonth();
    calendarYear = now.getFullYear();
  }

  function pad2(value) {
    return String(value).padStart(2, '0');
  }

  function dateKey(year, month, day) {
    return year + '-' + pad2(month + 1) + '-' + pad2(day);
  }

  function formatDisplayDate(year, month, day) {
    return pad2(day) + '.' + pad2(month + 1) + '.' + year;
  }

  function formatTime(value) {
    if (!value) {
      return '—';
    }
    var date = parseIsoDate(value);
    if (!date) {
      return '—';
    }
    return pad2(date.getHours()) + ':' + pad2(date.getMinutes());
  }

  function eventsForDay(year, month, day) {
    var key = dateKey(year, month, day);
    return termine.filter(function (event) {
      var start = parseIsoDate(event.startAt);
      if (!start) {
        return false;
      }
      var eventKey = start.getFullYear() + '-' + pad2(start.getMonth() + 1) + '-' + pad2(start.getDate());
      return eventKey === key;
    });
  }

  function renderDayEvents(year, month, day) {
    var wrap = document.getElementById('meine-cal-day-events');
    var title = document.getElementById('meine-cal-day-title');
    var list = document.getElementById('meine-cal-day-list');
    if (!wrap || !title || !list) {
      return;
    }
    var dayEvents = eventsForDay(year, month, day);
    if (dayEvents.length === 0) {
      wrap.hidden = true;
      list.innerHTML = '';
      return;
    }
    title.textContent = formatDisplayDate(year, month, day);
    list.innerHTML = '';
    dayEvents.forEach(function (event) {
      var item = document.createElement('li');
      item.className = 'termine-calendar__event';
      var beginn = formatTime(event.startAt);
      var ende = formatTime(event.endAt);
      var ausbilder = event.ausbilderName ? ' · Ausbilder: ' + event.ausbilderName : '';
      item.textContent = (event.categoryLabel || 'Termin') + ': ' + (event.thema || '—')
        + ' (' + beginn + '–' + ende + ')' + ausbilder;
      list.appendChild(item);
    });
    wrap.hidden = false;
  }

  function renderCalendar() {
    var grid = document.getElementById('meine-cal-grid');
    var monthLabel = document.getElementById('meine-cal-month-label');
    if (!grid || !monthLabel) {
      return;
    }
    initCalendarMonth();
    var monthNames = [
      'Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
      'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'
    ];
    monthLabel.textContent = monthNames[calendarMonth] + ' ' + calendarYear;
    grid.innerHTML = '';

    var firstDay = new Date(calendarYear, calendarMonth, 1);
    var startOffset = (firstDay.getDay() + 6) % 7;
    var daysInMonth = new Date(calendarYear, calendarMonth + 1, 0).getDate();
    var totalCells = Math.ceil((startOffset + daysInMonth) / 7) * 7;

    for (var i = 0; i < totalCells; i++) {
      var cell = document.createElement('button');
      cell.type = 'button';
      cell.className = 'termine-calendar__day';
      var dayNum = i - startOffset + 1;
      if (dayNum < 1 || dayNum > daysInMonth) {
        cell.classList.add('termine-calendar__day--outside');
        cell.disabled = true;
        cell.textContent = '';
      } else {
        var dayEvents = eventsForDay(calendarYear, calendarMonth, dayNum);
        cell.textContent = String(dayNum);
        if (dayEvents.length > 0) {
          cell.classList.add('termine-calendar__day--has-event');
          cell.setAttribute('aria-label', dayNum + '. ' + monthNames[calendarMonth] + ', ' + dayEvents.length + ' Termin(e)');
        }
        var selectedKey = selectedDay;
        var cellKey = dateKey(calendarYear, calendarMonth, dayNum);
        if (selectedKey === cellKey) {
          cell.classList.add('termine-calendar__day--selected');
        }
        (function (year, month, day, key) {
          cell.addEventListener('click', function () {
            selectedDay = key;
            renderCalendar();
            renderDayEvents(year, month, day);
          });
        })(calendarYear, calendarMonth, dayNum, cellKey);
      }
      grid.appendChild(cell);
    }

    if (selectedDay) {
      var parts = selectedDay.split('-');
      if (parts.length === 3 && Number(parts[0]) === calendarYear && Number(parts[1]) === calendarMonth + 1) {
        renderDayEvents(calendarYear, calendarMonth, Number(parts[2]));
        return;
      }
    }
    var dayEventsWrap = document.getElementById('meine-cal-day-events');
    if (dayEventsWrap) {
      dayEventsWrap.hidden = true;
    }
  }

  function bindCalendarNav() {
    var prev = document.getElementById('meine-cal-prev');
    var next = document.getElementById('meine-cal-next');
    if (prev) {
      prev.addEventListener('click', function () {
        initCalendarMonth();
        calendarMonth -= 1;
        if (calendarMonth < 0) {
          calendarMonth = 11;
          calendarYear -= 1;
        }
        selectedDay = null;
        renderCalendar();
      });
    }
    if (next) {
      next.addEventListener('click', function () {
        initCalendarMonth();
        calendarMonth += 1;
        if (calendarMonth > 11) {
          calendarMonth = 0;
          calendarYear += 1;
        }
        selectedDay = null;
        renderCalendar();
      });
    }
  }

  bindZeitFilter();
  bindViewToggle();
  bindCalendarNav();
  setActiveView('list');
})();
