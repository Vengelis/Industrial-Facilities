package fr.vengelis.industrialfacilities.client;

public final class CountdownText {
    public static String remaining(long until, long now) {
        if (until <= now || until <= 0) return "";
        long seconds = (until - now - 1) / 20 + 1;
        long remainder = seconds % 60;
        return seconds / 60 + ":" + (remainder < 10 ? "0" : "") + remainder;
    }

    private CountdownText() {}
}
