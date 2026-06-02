package de.feuerwehr.manager.web;

import de.feuerwehr.manager.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/my-area")
@RequiredArgsConstructor
public class MyAreaController {

    @GetMapping
    public String index(@AuthenticationPrincipal AppUserDetails actor, Model model) {
        model.addAttribute("displayName", actor.getDisplayName());
        model.addAttribute("username", actor.getUsername());
        return "my-area";
    }
}
