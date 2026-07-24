(function () {
  'use strict';

  function readData() {
    var el = document.getElementById('auswertung-chart-data');
    if (!el) {
      return {};
    }
    try {
      return JSON.parse(el.textContent || '{}');
    } catch (e) {
      return {};
    }
  }

  function cssVar(name, fallback) {
    var value = getComputedStyle(document.documentElement).getPropertyValue(name);
    return (value && value.trim()) || fallback;
  }

  function palette(count) {
    var base = [
      cssVar('--rot', '#c1121f'),
      cssVar('--gruen', '#2a9d8f'),
      '#e9c46a',
      '#457b9d',
      '#f4a261',
      '#6d6875',
      '#264653',
      '#b56576'
    ];
    var colors = [];
    for (var i = 0; i < count; i++) {
      colors.push(base[i % base.length]);
    }
    return colors;
  }

  function slices(raw) {
    if (!Array.isArray(raw)) {
      return [];
    }
    return raw.map(function (item) {
      return {
        label: item.label || item.name || '—',
        value: Number(item.value != null ? item.value : item.count) || 0
      };
    }).filter(function (item) {
      return item.value > 0;
    });
  }

  function renderBar(canvasId, data, horizontal) {
    var canvas = document.getElementById(canvasId);
    if (!canvas || typeof Chart === 'undefined') {
      return;
    }
    var items = slices(data);
    if (!items.length) {
      canvas.parentNode.innerHTML = '<p class="hint">Keine Diagrammdaten vorhanden.</p>';
      return;
    }
    new Chart(canvas, {
      type: 'bar',
      data: {
        labels: items.map(function (i) { return i.label; }),
        datasets: [{
          data: items.map(function (i) { return i.value; }),
          backgroundColor: palette(items.length),
          borderWidth: 0,
          borderRadius: 6
        }]
      },
      options: {
        indexAxis: horizontal ? 'y' : 'x',
        responsive: true,
        plugins: {
          legend: { display: false }
        },
        scales: {
          x: {
            beginAtZero: true,
            ticks: { color: cssVar('--text-muted', '#666') },
            grid: { color: cssVar('--border', '#ddd') }
          },
          y: {
            ticks: { color: cssVar('--text-muted', '#666') },
            grid: { color: cssVar('--border', '#ddd') }
          }
        }
      }
    });
  }

  function renderDoughnut(canvasId, data) {
    var canvas = document.getElementById(canvasId);
    if (!canvas || typeof Chart === 'undefined') {
      return;
    }
    var items = slices(data);
    if (!items.length) {
      canvas.parentNode.innerHTML = '<p class="hint">Keine Diagrammdaten vorhanden.</p>';
      return;
    }
    new Chart(canvas, {
      type: 'doughnut',
      data: {
        labels: items.map(function (i) { return i.label; }),
        datasets: [{
          data: items.map(function (i) { return i.value; }),
          backgroundColor: palette(items.length),
          borderWidth: 0
        }]
      },
      options: {
        responsive: true,
        plugins: {
          legend: {
            position: 'bottom',
            labels: { color: cssVar('--text', '#222') }
          }
        }
      }
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    var data = readData();
    renderBar('chart-top-personen', data.topPersonen, true);
    renderDoughnut('chart-typ', data.typ);
    renderBar('chart-stichworte', data.stichworte || data.topStichworte, true);
    renderBar('chart-themen', data.themen, true);
    renderBar('chart-fahrzeuge', data.fahrzeuge, true);
    renderBar('chart-geraete', data.geraete, true);
  });
})();
