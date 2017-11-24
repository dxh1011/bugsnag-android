package com.bugsnag.android;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Coordinates sending/storing of tracked sessions
 */
class SessionSender {

    private final SessionTracker sessionTracker;
    private final SessionStore sessionStore;
    private final SessionTrackingApiClient apiClient;
    private final Context context;
    private final Configuration config;
    private final String endpoint;

    SessionSender(SessionTracker sessionTracker,
                  SessionStore sessionStore,
                  SessionTrackingApiClient apiClient,
                  Context context,
                  Configuration configuration) {
        this.sessionTracker = sessionTracker;
        this.sessionStore = sessionStore;
        this.apiClient = apiClient;
        this.context = context;
        this.config = configuration;
        this.endpoint = config.getSessionEndpoint();
    }

    /**
     * Attempts to send all sessions (both from memory + disk)
     */
    void send() {
        SessionTrackingPayload payload = getSessionTrackingPayload();

        if (payload != null) {
            send(payload);
        }
        flushStoredSessions();
    }

    void storeAllSessions() {
        SessionTrackingPayload payload = getSessionTrackingPayload();

        if (payload != null) {
            sessionStore.write(payload);
        }
    }

    private SessionTrackingPayload getSessionTrackingPayload() {
        List<Session> sessions = new ArrayList<>();
        sessions.addAll(sessionTracker.sessionQueue);

        if (sessions.isEmpty()) {
            return null;
        } else {
            sessionTracker.sessionQueue.clear();
            AppData appData = new AppData(context, config, sessionTracker);
            return new SessionTrackingPayload(sessions, appData);
        }
    }

    /**
     * Attempts to flush session payloads stored on disk
     */
    private synchronized void flushStoredSessions() {
        Collection<File> storedFiles = sessionStore.findStoredFiles();

        for (File storedFile : storedFiles) {
            SessionTrackingPayload payload = new SessionTrackingPayload(storedFile);

            try {
                apiClient.postSessionTrackingPayload(endpoint, payload, config.getSessionApiHeaders());
                storedFile.delete();
            } catch (NetworkException e) { // store for later sending
                Logger.info("Failed to post stored session payload");
            } catch (BadResponseException e) { // drop bad data
                Logger.warn("Invalid session tracking payload", e);
                storedFile.delete();
            }
        }
    }

    /**
     * Attempts to send any tracked sessions to the API, and store in the event of failure
     */
    private synchronized void send(SessionTrackingPayload payload) {
        try {
            apiClient.postSessionTrackingPayload(endpoint, payload, config.getSessionApiHeaders());
        } catch (NetworkException e) { // store for later sending
            Logger.info("Failed to post session payload, storing on disk");
            sessionStore.write(payload);
        } catch (BadResponseException e) { // drop bad data
            Logger.warn("Invalid session tracking payload", e);
        }
    }

}