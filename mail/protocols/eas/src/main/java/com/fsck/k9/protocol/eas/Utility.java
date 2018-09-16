package com.fsck.k9.protocol.eas;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;


public class Utility {
    private static final ThreadLocalDateFormat mAbbrevEmailDateTimeFormat =
            new ThreadLocalDateFormat("yyyy-MM-dd");

    private static final ThreadLocalDateFormat mEmailDateTimeFormat =
            new ThreadLocalDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private static final ThreadLocalDateFormat mEmailDateTimeFormatWithMillis =
            new ThreadLocalDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private static final CursorGetter<String> STRING_GETTER = new CursorGetter<String>() {
        @Override
        public String get(Cursor cursor, int column) {
            return cursor.getString(column);
        }
    };

    /**
     * {@link #getFirstRowColumn} for a String with null as a default value.
     */
    public static String getFirstRowString(Context context, Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder, int column) {
        return getFirstRowString(context, uri, projection, selection, selectionArgs, sortOrder,
                column, null);
    }

    /**
     * {@link #getFirstRowColumn} for a String with a provided default value.
     */
    public static String getFirstRowString(Context context, Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder, int column,
            String defaultValue) {
        return getFirstRowColumn(context, uri, projection, selection, selectionArgs,
                sortOrder, column, defaultValue, STRING_GETTER);
    }

    /**
     * @return a generic in column {@code column} of the first result row, if the query returns at
     * least 1 row.  Otherwise returns {@code defaultValue}.
     */
    public static <T> T getFirstRowColumn(Context context, Uri uri,
            String[] projection, String selection, String[] selectionArgs, String sortOrder,
            int column, T defaultValue, CursorGetter<T> getter) {
        // Use PARAMETER_LIMIT to restrict the query to the single row we need
        uri = buildLimitOneUri(uri);
        Cursor c = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                sortOrder);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    return getter.get(c, column);
                }
            } finally {
                c.close();
            }
        }
        return defaultValue;
    }

    /**
     * @return if {@code original} is to the EmailProvider, add "?limit=1".  Otherwise just returns
     * {@code original}.
     *
     * Other providers don't support the limit param.  Also, changing URI passed from other apps
     * can cause permission errors.
     */
    /* package */ static Uri buildLimitOneUri(Uri original) {
        if ("content".equals(original.getScheme()) &&
                EmailContent.AUTHORITY.equals(original.getAuthority())) {
            return EmailContent.uriWithLimit(original, 1);
        }
        return original;
    }

    /**
     * Generate a time in milliseconds from an email date string that represents a date/time in GMT
     * @param date string in format 2010-02-23T16:00:00.000Z (ISO 8601, rfc3339)
     * @return the time in milliseconds (since Jan 1, 1970)
     */
    public static long parseEmailDateTimeToMillis(String date) throws ParseException {
        final GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
        if (date.length() <= 10) {
            cal.setTime(mAbbrevEmailDateTimeFormat.parse(date));
        } else if (date.length() <= 20) {
            cal.setTime(mEmailDateTimeFormat.parse(date));
        } else {
            cal.setTime(mEmailDateTimeFormatWithMillis.parse(date));
        }
        return cal.getTimeInMillis();
    }


    public interface CursorGetter<T> {
        T get(Cursor cursor, int column);
    }

    private static class ThreadLocalDateFormat extends ThreadLocal<SimpleDateFormat> {
        private final String mFormatStr;

        public ThreadLocalDateFormat(String formatStr) {
            mFormatStr = formatStr;
        }

        @Override
        protected SimpleDateFormat initialValue() {
            final SimpleDateFormat format = new SimpleDateFormat(mFormatStr);
            final GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
            format.setCalendar(cal);
            return format;
        }

        public Date parse(String date) throws ParseException {
            return super.get().parse(date);
        }
    }
}
