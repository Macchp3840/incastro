package it.incastro;

/** Lingua dell'interfaccia: italiano o inglese. */
final class L {
    static boolean en;

    private L() {}

    static String t(String it, String english) {
        return en ? english : it;
    }
}
