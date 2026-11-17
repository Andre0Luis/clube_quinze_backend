package br.com.clube_quinze.api.model.google;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Conta Google conectada pelo administrador (uma só para o clube). O refresh token nunca é gravado em texto puro. */
@Entity
@Table(name = "google_calendar_connection")
public class GoogleCalendarConnection {

    public enum Status {
        /** Conta conectada, ainda sem agenda escolhida: nada é sincronizado. */
        NEEDS_CALENDAR,
        /** Conta e agenda definidas: agendamentos são sincronizados. */
        READY,
        /** O Google recusou o token (acesso revogado): precisa reconectar. */
        REVOKED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connected_by_user_id", nullable = false)
    private Long connectedByUserId;

    @Column(name = "google_email")
    private String googleEmail;

    @Column(name = "refresh_token_enc", nullable = false, length = 2000)
    private String refreshTokenEnc;

    @Column(name = "scopes", length = 1000)
    private String scopes;

    @Column(name = "calendar_id")
    private String calendarId;

    @Column(name = "calendar_summary")
    private String calendarSummary;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status = Status.NEEDS_CALENDAR;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isReady() {
        return status == Status.READY && calendarId != null;
    }

    public Long getId() { return id; }
    public Long getConnectedByUserId() { return connectedByUserId; }
    public void setConnectedByUserId(Long connectedByUserId) { this.connectedByUserId = connectedByUserId; }
    public String getGoogleEmail() { return googleEmail; }
    public void setGoogleEmail(String googleEmail) { this.googleEmail = googleEmail; }
    public String getRefreshTokenEnc() { return refreshTokenEnc; }
    public void setRefreshTokenEnc(String refreshTokenEnc) { this.refreshTokenEnc = refreshTokenEnc; }
    public String getScopes() { return scopes; }
    public void setScopes(String scopes) { this.scopes = scopes; }
    public String getCalendarId() { return calendarId; }
    public void setCalendarId(String calendarId) { this.calendarId = calendarId; }
    public String getCalendarSummary() { return calendarSummary; }
    public void setCalendarSummary(String calendarSummary) { this.calendarSummary = calendarSummary; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
