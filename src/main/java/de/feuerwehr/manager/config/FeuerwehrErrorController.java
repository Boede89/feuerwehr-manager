package de.feuerwehr.manager.config;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Eigene Fehlerseite ohne Layout/WebUiAdvice — damit /error auch bei DB- oder Template-Fehlern
 * noch ausgeliefert werden kann.
 */
@Controller
@Slf4j
public class FeuerwehrErrorController implements ErrorController {

    @RequestMapping("${server.error.path:${error.path:/error}}")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int code = status instanceof Integer i ? i : HttpStatus.INTERNAL_SERVER_ERROR.value();
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (exception instanceof Throwable throwable) {
            if (code >= 500) {
                log.error("HTTP {} {}: {}", code, uri, throwable.getMessage(), throwable);
            } else {
                log.warn("HTTP {} {}: {}", code, uri, throwable.getMessage());
            }
        } else if (code >= 500) {
            log.error("HTTP {} {} ohne Exception-Details", code, uri);
        }
        ErrorPageView view = viewForStatus(code);
        model.addAttribute("status", code);
        model.addAttribute("errorTitle", view.title());
        model.addAttribute("errorMessage", view.message());
        model.addAttribute("errorHint", view.hint());
        model.addAttribute("showStatusCode", view.showStatusCode());
        return "error";
    }

    static ErrorPageView viewForStatus(int code) {
        return switch (code) {
            case 401 -> new ErrorPageView(
                    "Anmeldung erforderlich",
                    "Bitte melden Sie sich an, um diese Seite zu öffnen.",
                    "Wenn das Problem bleibt, den Administrator informieren.",
                    false);
            case 403 -> new ErrorPageView(
                    "Kein Zugriff",
                    "Sie haben keine Berechtigung für diese Seite.",
                    "Wenn Sie Zugriff benötigen, wenden Sie sich an einen Administrator.",
                    false);
            case 404 -> new ErrorPageView(
                    "Seite nicht gefunden",
                    "Die angeforderte Seite existiert nicht oder wurde verschoben.",
                    "Bitte zur Startseite zurückkehren oder die Adresse prüfen.",
                    false);
            default -> code >= 500
                    ? new ErrorPageView(
                            "Fehler",
                            "Ein unerwarteter Fehler ist aufgetreten. Bitte erneut versuchen.",
                            "Wenn der Fehler bleibt, den Administrator informieren.",
                            true)
                    : new ErrorPageView(
                            "Fehler",
                            "Die angeforderte Seite konnte nicht geladen werden.",
                            "Bitte die Seite neu laden oder zur Startseite zurückkehren. Wenn der Fehler bleibt, den Administrator informieren.",
                            true);
        };
    }

    record ErrorPageView(String title, String message, String hint, boolean showStatusCode) {}
}
