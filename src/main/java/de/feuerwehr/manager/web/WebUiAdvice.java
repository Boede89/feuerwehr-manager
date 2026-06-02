package de.feuerwehr.manager.web;

import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.user.UserRole;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class WebUiAdvice {

    @ModelAttribute("isAdmin")
    public boolean isAdmin(@AuthenticationPrincipal AppUserDetails user) {
        return user != null && user.getRole() == UserRole.ADMIN;
    }
}
