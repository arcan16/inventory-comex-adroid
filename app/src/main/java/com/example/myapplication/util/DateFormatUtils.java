package com.example.myapplication.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * java.time no esta disponible sin desugaring en minSdk 24, asi que se usa
 * SimpleDateFormat (compatible desde API 1).
 */
public final class DateFormatUtils {

    private static final SimpleDateFormat ISO_DATE = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final SimpleDateFormat SHORT_DATE_ES = new SimpleDateFormat("dd MMM yyyy", new Locale("es", "MX"));

    private DateFormatUtils() {
    }

    /** "2024-01-04" -> "04 ene 2024". Si no se puede parsear, regresa el valor original. */
    public static String toShortSpanishDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) {
            return "";
        }
        try {
            Date parsed = ISO_DATE.parse(isoDate);
            return SHORT_DATE_ES.format(parsed);
        } catch (ParseException e) {
            return isoDate;
        }
    }
}
