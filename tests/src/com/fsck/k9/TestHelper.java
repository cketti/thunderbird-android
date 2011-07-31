package com.fsck.k9;

import android.content.Context;
import android.test.AndroidTestCase;
import java.lang.reflect.Method;

public class TestHelper {

    public static Context getTestContext(AndroidTestCase testCase) {
        Context context = null;
        try {
            Method m = AndroidTestCase.class.getMethod("getTestContext", new Class[0]);
            context = (Context) m.invoke(testCase, new Object[0]);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return context;
    }
}
