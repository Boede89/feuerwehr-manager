package de.feuerwehr.manager.berichte;

import java.util.List;

public record EinsatzberichtListResponse(List<EinsatzberichtListItemView> items, List<String> stichworte) {}
