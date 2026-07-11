package de.feuerwehr.manager.berichte;

import java.util.List;

public record EinsatzberichtReleaseValidationResult(boolean valid, List<EinsatzberichtReleaseFieldIssue> issues) {}
