package com.financetracker.usuario.util;

public class PasswordUtils {
    public static final String PASSWORD_REGEX_UPPER = ".*[A-Z].*";
    public static final String PASSWORD_REGEX_LOWER = ".*[a-z].*";
    public static final String PASSWORD_REGEX_DIGIT = ".*\\d.*";
    public static final String PASSWORD_REGEX_SPECIAL = ".*[^A-Za-z0-9].*";

    public static boolean isStrongPassword(String password) {
        if (password == null) {
            return false;
        }
        boolean hasUpper = password.matches(PASSWORD_REGEX_UPPER);
        boolean hasLower = password.matches(PASSWORD_REGEX_LOWER);
        boolean hasDigit = password.matches(PASSWORD_REGEX_DIGIT);
        boolean hasSpecial = password.matches(PASSWORD_REGEX_SPECIAL);
        return password.length() >= 8 && hasUpper && hasLower && hasDigit && hasSpecial;
    }
}
