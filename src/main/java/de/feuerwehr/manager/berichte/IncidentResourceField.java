package de.feuerwehr.manager.berichte;

import java.util.List;

public record IncidentResourceField(String key, String label, String unit) {

    public static final List<IncidentResourceField> ALL = List.of(
            new IncidentResourceField("kleinloescher", "Kleinlöscher", "Stk"),
            new IncidentResourceField("d_schlaeuche", "D-Schläuche", "Stk"),
            new IncidentResourceField("c_schlaeuche", "C-Schläuche", "Stk"),
            new IncidentResourceField("b_schlaeuche", "B-Schläuche", "Stk"),
            new IncidentResourceField("a_schlaeuche", "A-Schläuche", "Stk"),
            new IncidentResourceField("s_angriff", "S-Angriff", "Stk"),
            new IncidentResourceField("d_rohre", "D-Rohre", "Stk"),
            new IncidentResourceField("c_rohre", "C-Rohre", "Stk"),
            new IncidentResourceField("b_rohre", "B-Rohre", "Stk"),
            new IncidentResourceField("schaumrohre", "Schaumrohre", "Stk"),
            new IncidentResourceField("messgeraete", "Messgeräte", "Stk"),
            new IncidentResourceField("seilwinde", "Seilwinde / Greifzug", "Stk"),
            new IncidentResourceField("tueroeffnung", "Türöffnungsset", "Set"),
            new IncidentResourceField("dichtkissen", "Dichtkissen", "Stk"),
            new IncidentResourceField("beleuchtung", "Beleuchtungsgerät", "Stk"),
            new IncidentResourceField("handwerkszeug", "Handwerkszeug", "Stk"),
            new IncidentResourceField("steckleiter", "Steckleiter", "Stk"),
            new IncidentResourceField("schiebleiter", "Schiebleiter", "Stk"),
            new IncidentResourceField("feuerwehrleine", "Feuerwehrleine", "Stk"),
            new IncidentResourceField("arbeitsleine", "Arbeitsleine", "Stk"),
            new IncidentResourceField("sprungpolster", "Sprungpolster / -retter", "Stk"),
            new IncidentResourceField("hydraul_schere", "Hydraulische Schere", "Stk"),
            new IncidentResourceField("hydraul_spreizer", "Hydraulischer Spreizer", "Stk"),
            new IncidentResourceField("oelsperre", "Ölsperre", "m"),
            new IncidentResourceField("atemschutz", "Atemschutzgeräte", "Stk"),
            new IncidentResourceField("pressluftflasche", "Pressluftflasche", "Stk"),
            new IncidentResourceField("schaummittel_l", "Schaummittel", "l"),
            new IncidentResourceField("oelbinder_ohne_l", "Ölbinder ohne Entsorgung", "kg"),
            new IncidentResourceField("oelbinder_mit_l", "Ölbinder mit Entsorgung", "kg"),
            new IncidentResourceField("bioversal_ohne", "Bioversal ohne Entsorgung", "ml"),
            new IncidentResourceField("bioversal_mit", "Bioversal mit Entsorgung", "ml"),
            new IncidentResourceField("strassenreiniger", "Straßenreiniger", "kg"),
            new IncidentResourceField("vliesbahn", "Vliesbahn", "m"),
            new IncidentResourceField("vliestuch", "Vliestuch", "Stk"),
            new IncidentResourceField("wasser_gesamt_m3", "Wasser gesamt", "m³"),
            new IncidentResourceField("wasser_hydrant_m3", "davon aus Hydrant", "m³"),
            new IncidentResourceField("wasser_loeschteich_m3", "davon aus Löschteich", "m³"),
            new IncidentResourceField("pulver_kg", "Pulver", "kg"),
            new IncidentResourceField("wassersauger_hhmm", "Wassersauger", "hh:mm"));
}
