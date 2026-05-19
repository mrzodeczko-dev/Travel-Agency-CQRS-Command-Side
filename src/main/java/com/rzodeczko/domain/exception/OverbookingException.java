package com.rzodeczko.domain.exception;

/**
 * Wyjatek oznaczajacy naruszenie reguly biznesowej - brak miejsc.
 * Jest to RuntimeException, co spowoduje rollback transakcji w Springu.
 */
public class OverbookingException extends RuntimeException {
    public OverbookingException(String message) {
        super(message);
    }
}
