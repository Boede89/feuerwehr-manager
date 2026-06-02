package de.feuerwehr.manager.web;

import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/unit")
@RequiredArgsConstructor
public class UnitSwitchController {

    private final UnitService unitService;

    @PostMapping("/select")
    public String selectUnit(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(required = false) String redirect) {
        if (actor == null || !actor.getRole().isSuperAdmin()) {
            return "redirect:/";
        }
        Optional<Unit> resolved = unitService.resolveActiveUnit(unit, actor);
        if (resolved.isEmpty()) {
            return "redirect:/";
        }
        long unitId = resolved.get().getId();
        if (redirect != null && redirect.startsWith("/") && !redirect.startsWith("//")) {
            String target = UriComponentsBuilder.fromUriString(redirect)
                    .replaceQueryParam("unit", unitId)
                    .build()
                    .toUriString();
            return "redirect:" + target;
        }
        return "redirect:" + ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/")
                .queryParam("unit", unitId)
                .build()
                .toUriString();
    }
}
