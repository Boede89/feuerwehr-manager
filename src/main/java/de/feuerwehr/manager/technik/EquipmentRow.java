package de.feuerwehr.manager.technik;

/** Anzeigezeile Beladung (open-in-view: false — keine Lazy-Assoziationen im Template). */
public record EquipmentRow(long id, String name, Long categoryId, String categoryName) {}
