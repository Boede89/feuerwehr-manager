/**
 * Einheitswahl (Superadmin): setzt ?unit=… und lädt die Seite neu.
 * Kein Inline-JS — kompatibel mit Content-Security-Policy.
 */
(function () {
  function onReady(fn) {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", fn);
    } else {
      fn();
    }
  }

  onReady(function () {
    document.querySelectorAll("select[data-unit-switch]").forEach(function (select) {
      select.addEventListener("change", function () {
        var value = select.value;
        if (!value) {
          return;
        }
        var url = new URL(window.location.href);
        url.searchParams.set("unit", value);
        window.location.assign(url.toString());
      });
    });
  });
})();
