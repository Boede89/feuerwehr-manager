package de.feuerwehr.manager.unit;

import de.feuerwehr.manager.print.PrintMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "unit_print_settings")
public class UnitPrintSettings {

    @Id
    private Long unitId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "print_mode", nullable = false, length = 16)
    private PrintMode printMode = PrintMode.DIALOG;

    @Column(name = "cups_printer_name", length = 128)
    private String cupsPrinterName;

    @Column(name = "cups_server", length = 128)
    private String cupsServer;

    @Column(name = "cups_use_postscript", nullable = false)
    private boolean cupsUsePostscript;
}
