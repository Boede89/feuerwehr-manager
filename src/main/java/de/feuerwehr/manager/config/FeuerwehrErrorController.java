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
            log.error("HTTP {} {}: {}", code, uri, throwable.getMessage(), throwable);
        } else if (code >= 500) {
            log.error("HTTP {} {} ohne Exception-Details", code, uri);
        }
        model.addAttribute("status", code);
        model.addAttribute(
                "errorMessage",
                code >= 500
                        ? "Ein unerwarteter Fehler ist aufgetreten. Bitte erneut versuchen."
                        : "Die angeforderte Seite konnte nicht geladen werden.");
        return "error";
    }
}
