package com.financetracker.dashboard.exception;

public class DashboardLoadException extends RuntimeException {
    public DashboardLoadException(String message) {
        super(message);
    }

    public DashboardLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}