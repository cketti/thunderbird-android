package com.fsck.k9.mail.message.basic;


import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;


public class BasicHeaderTest {
    @Test
    public void sizeWithEmptyHeaderShouldReturnZero() throws Exception {
        BasicHeader basicHeader = createSimpleHeader();

        int size = basicHeader.size();

        assertEquals(0, size);
    }

    @Test
    public void sizeWithNonEmptyHeaderShouldReturnCorrectValue() throws Exception {
        BasicHeader basicHeader = createSimpleHeader(
                "X-Multiple", "One",
                "Content-Type", "text/html",
                "X-Multiple", "Two"
        );

        int size = basicHeader.size();

        assertEquals(3, size);
    }

    @Test
    public void valueWithNonExistentNameShouldReturnNull() throws Exception {
        BasicHeader basicHeader = createSimpleHeader();

        String value = basicHeader.value("Subject");

        assertNull(value);
    }

    @Test
    public void valueShouldReturnCorrectValue() throws Exception {
        BasicHeader basicHeader = createSimpleHeader("Content-Type", "text/plain");

        String value = basicHeader.value("Content-Type");

        assertEquals("text/plain", value);
    }

    @Test
    public void valueWithMixedCaseNameShouldReturnCorrectValue() throws Exception {
        BasicHeader basicHeader = createSimpleHeader("Content-Type", "text/plain");

        String value = basicHeader.value("CONTENT-type");

        assertEquals("text/plain", value);
    }

    @Test
    public void valueShouldReturnFirstValue() throws Exception {
        BasicHeader basicHeader = createSimpleHeader(
                "x-multiple", "One",
                "X-Multiple", "Two"
        );

        String value = basicHeader.value("X-Multiple");

        assertEquals("One", value);
    }

    @Test
    public void valuesWithNonExistentNameShouldReturnEmptyList() throws Exception {
        BasicHeader basicHeader = createSimpleHeader("Content-Type", "text/plain");

        List<String> values = basicHeader.values("Subject");

        assertNotNull(values);
        assertEquals(0, values.size());
    }

    @Test
    public void valuesWithSingleMatchShouldReturnCorrectValue() throws Exception {
        BasicHeader basicHeader = createSimpleHeader(
                "Content-Type", "text/plain",
                "Subject", "Hi there"
        );

        List<String> values = basicHeader.values("Subject");

        assertEquals(Collections.singletonList("Hi there"), values);
    }

    @Test
    public void valuesWithMultipleMatchesShouldReturnCorrectValues() throws Exception {
        BasicHeader basicHeader = createSimpleHeader(
                "X-Multiple", "One",
                "Content-Type", "text/html",
                "x-multiple", "Two"
        );

        List<String> values = basicHeader.values("X-Multiple");

        assertEquals(Arrays.asList("One", "Two"), values);
    }

    @Test
    public void valuesWithMixedCaseNameShouldReturnCorrectValue() throws Exception {
        BasicHeader basicHeader = createSimpleHeader(
                "Content-Type", "text/plain",
                "Subject", "Hi there"
        );

        List<String> values = basicHeader.values("content-TyPE");

        assertEquals(Collections.singletonList("text/plain"), values);
    }

    @Test
    public void fieldsWithEmptyHeaderShouldReturnEmptyList() throws Exception {
        BasicHeader basicHeader = createSimpleHeader();

        List<BasicHeaderField> fields = basicHeader.fields();

        assertNotNull(fields);
        assertEquals(0, fields.size());
    }

    @Test
    public void fieldsWithMultipleHeadersShouldReturnCorrectValues() throws Exception {
        BasicHeader basicHeader = createSimpleHeader(
                "X-Multiple", "One",
                "Content-Type", "text/html",
                "x-multiple", "Two"
        );

        List<BasicHeaderField> fields = basicHeader.fields();

        assertNotNull(fields);
        assertEquals(3, fields.size());
        assertEquals("X-Multiple: One", fields.get(0).toString());
        assertEquals("Content-Type: text/html", fields.get(1).toString());
        assertEquals("x-multiple: Two", fields.get(2).toString());
    }

    private BasicHeader createSimpleHeader(String... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("Need key/value pairs, i.e. array length needs to be a multiple of 2");
        }

        BasicHeader.Builder builder = new BasicHeader.Builder();
        for (int i = 0, end = values.length; i < end; i += 2) {
            String name = values[i];
            String value = values[i + 1];

            builder.add(name, value);
        }

        return builder.build();
    }
}
