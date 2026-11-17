package br.com.clube_quinze.api.model.enumeration;

public enum AppointmentStatus {
    /** Agendado, ainda sem confirmação do cliente. */
    SCHEDULED,
    /** Confirmado pelo cliente (ou mantido pelo admin). */
    CONFIRMED,
    /** Cliente propôs outro horário; aguardando aprovação do admin. O horário antigo segue reservado. */
    RESCHEDULE_REQUESTED,
    COMPLETED,
    CANCELED
}
