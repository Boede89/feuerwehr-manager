(function () {
  'use strict';

  function getCsrfToken() {
    var meta = document.querySelector('meta[name="csrf-token"]');
    return meta ? meta.getAttribute('content') : '';
  }

  function getCsrfHeader() {
    var meta = document.querySelector('meta[name="csrf-header"]');
    return meta ? meta.getAttribute('content') : 'X-XSRF-TOKEN';
  }

  function esc(text) {
    var div = document.createElement('div');
    div.textContent = text == null ? '' : String(text);
    return div.innerHTML;
  }

  function fmtSize(bytes) {
    if (bytes < 1024) {
      return bytes + ' B';
    }
    if (bytes < 1024 * 1024) {
      return (bytes / 1024).toFixed(1) + ' KB';
    }
    return (bytes / 1024 / 1024).toFixed(1) + ' MB';
  }

  function fmtDate(iso) {
    if (!iso) {
      return '';
    }
    return new Date(iso).toLocaleString('de-DE', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  function fileIcon(mime) {
    if (!mime) {
      return '📎';
    }
    if (mime.indexOf('image/') === 0) {
      return '🖼';
    }
    if (mime === 'application/pdf') {
      return '📄';
    }
    if (mime.indexOf('word') >= 0 || mime.indexOf('officedocument') >= 0) {
      return '📝';
    }
    if (mime === 'text/plain') {
      return '📋';
    }
    return '📎';
  }

  function apiBase(wrap) {
    var prefix = wrap.dataset.apiBase
      || (window.BerichteApiBase ? window.BerichteApiBase.path() : '/berichte/einsatzberichte');
    return prefix + '/' + wrap.dataset.reportId + '/anhaenge?unit=' + wrap.dataset.unitId;
  }

  function isReadonly(wrap) {
    return wrap.dataset.readonly === 'true';
  }

  function render(wrap, attachments) {
    var readonly = isReadonly(wrap);
    var listHtml = attachments.length
      ? '<div class="incident-attachments-list">' + attachments.map(function (a) {
        return '<div class="incident-attachment-card">' +
          '<span class="incident-attachment-card__icon">' + fileIcon(a.mimeType) + '</span>' +
          '<div class="incident-attachment-card__meta">' +
            '<div class="incident-attachment-card__name" title="' + esc(a.filename) + '">' + esc(a.filename) + '</div>' +
            '<div class="incident-attachment-card__details">' + esc(fmtSize(a.fileSize)) + ' · ' + esc(fmtDate(a.createdAt)) + '</div>' +
          '</div>' +
          '<button type="button" class="btn btn--outline btn--sm" data-action="download" data-id="' + a.id + '" data-name="' + esc(a.filename) + '">↓ Download</button>' +
          (readonly ? '' : '<button type="button" class="btn btn--danger btn--sm" data-action="delete" data-id="' + a.id + '">✕</button>') +
        '</div>';
      }).join('') + '</div>'
      : '<p class="incident-attachments-empty">Keine Anhänge vorhanden.</p>';

    var uploadHtml = readonly ? '' :
      '<div class="incident-attachment-drop" id="attach-drop-zone">' +
        '<div class="incident-attachment-drop__icon">📎</div>' +
        '<div class="incident-attachment-drop__hint">Datei hierher ziehen oder klicken zum Auswählen</div>' +
        '<div class="incident-attachment-drop__types">Bilder (JPEG/PNG/GIF/WebP), PDF, Word (docx), ODT, Text — max. 20 MB</div>' +
        '<input type="file" id="attach-file-input" hidden accept="image/*,.pdf,.docx,.odt,.txt">' +
      '</div>' +
      '<div class="incident-attachment-progress" id="attach-progress">Wird hochgeladen …</div>';

    wrap.innerHTML = listHtml + uploadHtml;

    wrap.querySelectorAll('[data-action="download"]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        downloadAttachment(wrap, btn.dataset.id, btn.dataset.name);
      });
    });

    wrap.querySelectorAll('[data-action="delete"]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        if (!confirm('Anhang wirklich löschen?')) {
          return;
        }
        deleteAttachment(wrap, btn.dataset.id);
      });
    });

    if (!readonly) {
      bindUpload(wrap);
    }
  }

  function bindUpload(wrap) {
    var dropZone = document.getElementById('attach-drop-zone');
    var fileInput = document.getElementById('attach-file-input');
    if (!dropZone || !fileInput) {
      return;
    }

    dropZone.addEventListener('click', function () {
      fileInput.click();
    });
    dropZone.addEventListener('dragover', function (e) {
      e.preventDefault();
      dropZone.classList.add('incident-attachment-drop--active');
    });
    dropZone.addEventListener('dragleave', function () {
      dropZone.classList.remove('incident-attachment-drop--active');
    });
    dropZone.addEventListener('drop', function (e) {
      e.preventDefault();
      dropZone.classList.remove('incident-attachment-drop--active');
      var file = e.dataTransfer.files[0];
      if (file) {
        uploadFile(wrap, file);
      }
    });
    fileInput.addEventListener('change', function () {
      var file = fileInput.files[0];
      if (file) {
        uploadFile(wrap, file);
      }
      fileInput.value = '';
    });
  }

  function downloadAttachment(wrap, attachmentId, filename) {
    var url = apiBase(wrap).replace('/anhaenge', '/anhaenge/' + attachmentId + '/download');
    fetch(url, { credentials: 'same-origin' })
      .then(function (res) {
        if (!res.ok) {
          throw new Error('HTTP ' + res.status);
        }
        return res.blob();
      })
      .then(function (blob) {
        var objectUrl = URL.createObjectURL(blob);
        var link = document.createElement('a');
        link.href = objectUrl;
        link.download = filename || 'anhang';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(objectUrl);
      })
      .catch(function () {
        alert('Download fehlgeschlagen.');
      });
  }

  function uploadFile(wrap, file) {
    var progress = document.getElementById('attach-progress');
    var dropZone = document.getElementById('attach-drop-zone');
    if (progress) {
      progress.style.display = 'block';
    }
    if (dropZone) {
      dropZone.style.opacity = '0.5';
    }

    var formData = new FormData();
    formData.append('file', file);
    fetch(apiBase(wrap), {
      method: 'POST',
      credentials: 'same-origin',
      headers: (function () {
        var headers = {};
        headers[getCsrfHeader()] = getCsrfToken();
        return headers;
      })(),
      body: formData
    })
      .then(function (res) {
        if (!res.ok) {
          return res.text().then(function (text) {
            var message = 'Upload fehlgeschlagen';
            try {
              var body = JSON.parse(text);
              message = body.message || body.error || message;
            } catch (e) {
              if (text) {
                message = text;
              }
            }
            throw new Error(message);
          });
        }
        return load(wrap);
      })
      .catch(function (err) {
        alert(err.message || 'Upload fehlgeschlagen');
        if (progress) {
          progress.style.display = 'none';
        }
        if (dropZone) {
          dropZone.style.opacity = '1';
        }
      });
  }

  function deleteAttachment(wrap, attachmentId) {
    fetch(apiBase(wrap) + '/' + attachmentId, {
      method: 'DELETE',
      credentials: 'same-origin',
      headers: (function () {
        var headers = {};
        headers[getCsrfHeader()] = getCsrfToken();
        return headers;
      })()
    })
      .then(function (res) {
        if (!res.ok) {
          throw new Error('Löschen fehlgeschlagen');
        }
        return load(wrap);
      })
      .catch(function (err) {
        alert(err.message || 'Löschen fehlgeschlagen');
      });
  }

  function load(wrap) {
    if (!wrap) {
      wrap = document.getElementById('incident-attachments-wrap');
    }
    if (!wrap || !wrap.dataset.reportId) {
      return Promise.resolve();
    }
    wrap.innerHTML = '<p class="incident-attachments-empty">Anhänge werden geladen …</p>';
    return fetch(apiBase(wrap), { credentials: 'same-origin' })
      .then(function (res) {
        if (!res.ok) {
          throw new Error('Anhänge konnten nicht geladen werden');
        }
        return res.json();
      })
      .then(function (attachments) {
        render(wrap, attachments);
      })
      .catch(function (err) {
        wrap.innerHTML = '<p class="incident-attachments-empty">' + esc(err.message) + '</p>';
      });
  }

  window.BerichteAnhaenge = {
    load: function () {
      return load();
    }
  };
})();
