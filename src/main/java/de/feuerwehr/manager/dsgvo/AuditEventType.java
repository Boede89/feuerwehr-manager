package de.feuerwehr.manager.dsgvo;

/** Protokolltypen ohne personenbezogene Klartext-Passwörter oder Chip-IDs. */
public enum AuditEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    RFID_LOGIN_SUCCESS,
    RFID_LOGIN_FAILURE,
    PRIVACY_CONSENT,
    USER_ANONYMIZED,
    RFID_CARD_REGISTERED,
    RFID_CARD_REVOKED
}
