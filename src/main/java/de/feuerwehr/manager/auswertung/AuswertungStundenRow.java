package de.feuerwehr.manager.auswertung;

/** Geleistete Stunden eines Mitglieds in der Auswertung „Einsätze / Dienste“. */
public record AuswertungStundenRow(
        long personId,
        String name,
        boolean archived,
        long einsatzMinuten,
        long uebungMinuten,
        long sonderMinuten,
        long sonstigMinuten,
        long summeMinuten,
        String einsatzStunden,
        String uebungStunden,
        String sonderStunden,
        String sonstigStunden,
        String summeStunden) {}
