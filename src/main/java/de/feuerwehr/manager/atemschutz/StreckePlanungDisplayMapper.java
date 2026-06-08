package de.feuerwehr.manager.atemschutz;

import de.feuerwehr.manager.atemschutz.StreckePlanungDisplay.PoolBadge;
import de.feuerwehr.manager.atemschutz.StreckePlanungDisplay.TeilnehmerBadge;
import de.feuerwehr.manager.atemschutz.StreckePlanungDisplay.TerminCard;
import de.feuerwehr.manager.atemschutz.StreckePlanungService.CarrierAssignmentView;
import de.feuerwehr.manager.atemschutz.StreckePlanungService.CarrierPoolView;
import de.feuerwehr.manager.atemschutz.StreckePlanungService.StreckePlanungView;
import de.feuerwehr.manager.atemschutz.StreckePlanungService.TerminView;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class StreckePlanungDisplayMapper {

    private static final DateTimeFormatter DATE_DISPLAY = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY);
    private static final DateTimeFormatter DATE_ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_DISPLAY = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY);

    private StreckePlanungDisplayMapper() {}

    public static List<TerminCard> toTerminCards(StreckePlanungView view) {
        return view.termine().stream().map(StreckePlanungDisplayMapper::toTerminCard).toList();
    }

    public static List<PoolBadge> toPoolBadges(StreckePlanungView view, int warnDays) {
        return view.unassignedCarriers().stream()
                .map(c -> toPoolBadge(c, warnDays))
                .toList();
    }

    private static TerminCard toTerminCard(TerminView termin) {
        String headerModifier = "";
        if (termin.vergangen()) {
            headerModifier = " strecke-termin-card__header--past";
        } else if (termin.voll()) {
            headerModifier = " strecke-termin-card__header--full";
        }
        return new TerminCard(
                termin.id(),
                DATE_DISPLAY.format(termin.datum()),
                DATE_ISO.format(termin.datum()),
                TIME_DISPLAY.format(termin.zeit()) + " Uhr",
                TIME_DISPLAY.format(termin.zeit()),
                blankToEmpty(termin.ort()),
                blankToEmpty(termin.bemerkung()),
                termin.maxTeilnehmer(),
                termin.aktuelleTeilnehmer(),
                termin.vergangen(),
                termin.voll(),
                headerModifier,
                termin.teilnehmer().stream().map(StreckePlanungDisplayMapper::toTeilnehmerBadge).toList());
    }

    private static TeilnehmerBadge toTeilnehmerBadge(CarrierAssignmentView teilnehmer) {
        boolean abgelaufen =
                teilnehmer.daysUntilExpiry() != null && teilnehmer.daysUntilExpiry() < 0;
        String streckeBisAnzeige = teilnehmer.streckeBis() != null
                ? DATE_DISPLAY.format(teilnehmer.streckeBis())
                : "";
        String metaText = abgelaufen ? "abgelaufen" : streckeBisAnzeige;
        return new TeilnehmerBadge(
                teilnehmer.carrierId(),
                teilnehmer.name(),
                teilnehmer.streckeBis() != null ? DATE_ISO.format(teilnehmer.streckeBis()) : "",
                streckeBisAnzeige,
                metaText,
                teilnehmer.statusDot(),
                teilnehmer.notified(),
                abgelaufen);
    }

    private static PoolBadge toPoolBadge(CarrierPoolView carrier, int warnDays) {
        boolean abgelaufen =
                carrier.daysUntilExpiry() != null && carrier.daysUntilExpiry() < 0;
        boolean warnung = !abgelaufen
                && carrier.daysUntilExpiry() != null
                && carrier.daysUntilExpiry() >= 0
                && carrier.daysUntilExpiry() <= warnDays;
        String metaText = abgelaufen
                ? "abgelaufen"
                : (carrier.streckeBis() != null ? DATE_DISPLAY.format(carrier.streckeBis()) : "");
        return new PoolBadge(
                carrier.carrierId(),
                carrier.name(),
                carrier.streckeBis() != null ? DATE_ISO.format(carrier.streckeBis()) : "",
                metaText,
                carrier.statusDot(),
                abgelaufen,
                warnung);
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
