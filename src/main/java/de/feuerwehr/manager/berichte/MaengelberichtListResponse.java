package de.feuerwehr.manager.berichte;

import java.util.List;

public record MaengelberichtListResponse(List<MaengelberichtListItemView> items, List<Integer> years) {}
