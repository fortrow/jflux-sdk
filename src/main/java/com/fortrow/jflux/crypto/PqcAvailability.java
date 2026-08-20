package com.fortrow.jflux.crypto;

final class PqcAvailability {

    private PqcAvailability() {
    }

    static boolean available(String className) {
        try {
            Class.forName(className, false, PqcAvailability.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
