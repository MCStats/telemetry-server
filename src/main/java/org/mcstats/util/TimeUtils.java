package org.mcstats.util;

public class TimeUtils {

    /**
     * Convert a given time in seconds to a more readable format
     *
     * @param time
     * @return the time in a more readable format (e.g 2 days 5 hours 1 minute 34  seconds)
     */
    public static String timeToString(long time) {
        String str = "";

        if ((System.currentTimeMillis() / 1000L) - time <= 0) {
            return "Not yet known";
        }

        long days = time / 86400;
        time -= days * 86400;

        long hours = time / 3600;
        time -= hours * 3600;

        long minutes = time / 60;
        time -= minutes * 60;

        long seconds = time;

        if (days > 0) {
            str += days + " day" + (days == 1 ? "" : "s") + " ";
        }

        if (hours > 0) {
            str += hours + " hour" + (hours == 1 ? "" : "s") + " ";
        }

        if (minutes > 0) {
            str += minutes + " minute" + (minutes == 1 ? "" : "s") + " ";
        }

        if (seconds > 0) {
            str += seconds + " second" + (seconds == 1 ? "" : "s") + " ";
        }

        if (str.equals("")) {
            return "less than a second";
        }

        return str.trim();
    }

}
