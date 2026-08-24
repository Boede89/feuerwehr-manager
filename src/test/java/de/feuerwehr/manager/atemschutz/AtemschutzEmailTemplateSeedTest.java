package de.feuerwehr.manager.atemschutz;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AtemschutzEmailTemplateSeedTest {

    @Test
    void defaultSeedsCoverEveryNotificationCategory() {
        var keys = AtemschutzSettingsService.defaultEmailTemplateKeys();
        for (AtemschutzNotificationCategory category : AtemschutzNotificationCategory.values()) {
            assertThat(keys)
                    .as("fehlende Standard-Vorlage für %s", category)
                    .contains(category.getWarnungTemplateKey(), category.getAbgelaufenTemplateKey());
        }
    }
}
