package de.feuerwehr.manager.security;

import static org.assertj.core.api.Assertions.assertThat;

import de.feuerwehr.manager.user.PermissionOverrideEffect;
import de.feuerwehr.manager.user.UserPermissionOverride;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PermissionEffectiveSupportTest {

    @Test
    void writeImpliesRead() {
        Set<String> effective = PermissionEffectiveSupport.expandImpliedReads(Set.of("berichte.write"));
        assertThat(effective).contains("berichte.write", "berichte.read");
    }

    @Test
    void approveImpliesRead() {
        Set<String> effective = PermissionEffectiveSupport.expandImpliedReads(Set.of("berichte.approve"));
        assertThat(effective).contains("berichte.approve", "berichte.read");
    }

    @Test
    void grantAddsPermissionBeyondRole() {
        Set<String> base = PermissionEffectiveSupport.expandImpliedReads(Set.of("personal.read"));
        UserPermissionOverride grant = override("termine.read", PermissionOverrideEffect.GRANT);

        Set<String> effective = PermissionEffectiveSupport.applyOverrides(base, List.of(grant));

        assertThat(effective).contains("personal.read", "termine.read");
    }

    @Test
    void denyRemovesPermissionFromRole() {
        Set<String> base =
                PermissionEffectiveSupport.expandImpliedReads(Set.of("personal.read", "personal.write"));
        UserPermissionOverride deny = override("personal.write", PermissionOverrideEffect.DENY);

        Set<String> effective = PermissionEffectiveSupport.applyOverrides(base, List.of(deny));

        assertThat(effective).contains("personal.read");
        assertThat(effective).doesNotContain("personal.write");
    }

    @Test
    void denyWinsOverGrant() {
        Set<String> base = PermissionEffectiveSupport.expandImpliedReads(Set.of("personal.read"));
        UserPermissionOverride grant = override("berichte.read", PermissionOverrideEffect.GRANT);
        UserPermissionOverride deny = override("berichte.read", PermissionOverrideEffect.DENY);

        Set<String> effective = PermissionEffectiveSupport.applyOverrides(base, List.of(grant, deny));

        assertThat(effective).contains("personal.read");
        assertThat(effective).doesNotContain("berichte.read");
    }

    @Test
    void denyWriteKeepsExplicitRead() {
        Set<String> base =
                PermissionEffectiveSupport.expandImpliedReads(Set.of("personal.read", "personal.write"));
        UserPermissionOverride deny = override("personal.write", PermissionOverrideEffect.DENY);

        Set<String> effective = PermissionEffectiveSupport.applyOverrides(base, List.of(deny));

        assertThat(effective).containsExactly("personal.read");
    }

    private static UserPermissionOverride override(String permission, PermissionOverrideEffect effect) {
        UserPermissionOverride row = new UserPermissionOverride();
        row.setPermission(permission);
        row.setEffect(effect);
        return row;
    }
}
