package de.feuerwehr.einsatzapp.data

class SessionExpiredException(
    message: String = "Sitzung abgelaufen",
) : IllegalStateException(message)
