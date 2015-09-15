package com.fsck.k9.mail.store.eas;


import android.content.Context;
import android.database.Cursor;
import android.net.Uri;


public class Utility {
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


    public interface CursorGetter<T> {
        T get(Cursor cursor, int column);
    }
}
