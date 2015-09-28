/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fsck.k9.mail.store.eas.adapter;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertArrayEquals;


@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SerializerTest {

    @Test
    public void testEmptyDocument() throws Exception {
        byte[] expectedBytes = document();

        Serializer serializer = new Serializer();
        serializer.done();
        byte[] bytes = serializer.toByteArray();

        assertArrayEquals("Serializer mismatch", bytes, expectedBytes);
    }

    @Test
    public void testDegeneratedTag() throws Exception {
        byte[] expectedBytes = document(
                Wbxml.SWITCH_PAGE,
                Tags.EMAIL,
                Tags.EMAIL_SUBJECT & Tags.PAGE_MASK);

        Serializer serializer = new Serializer();
        serializer.tag(Tags.EMAIL_SUBJECT);
        serializer.done();
        byte[] bytes = serializer.toByteArray();

        assertArrayEquals("Serializer mismatch", bytes, expectedBytes);
    }

    @Test
    public void testTagWithData() throws Exception {
        byte[] expectedBytes = document(
                Tags.SYNC_STATUS & Tags.PAGE_MASK | Wbxml.WITH_CONTENT,
                Wbxml.STR_I,
                '1',
                0,
                Wbxml.END);

        Serializer serializer = new Serializer();
        serializer.data(Tags.SYNC_STATUS, "1");
        serializer.done();
        byte[] bytes = serializer.toByteArray();

        assertArrayEquals("Serializer mismatch", bytes, expectedBytes);
    }

    @Test(expected = IOException.class)
    public void testTextWithNull() throws Exception {
        Serializer serializer = new Serializer();
        serializer.text(null);
    }

    @Test
    public void testOpaque() throws Exception {
        byte[] data = new byte[] { 1, 2, 3 };
        byte[] expectedBytes = document(
                Wbxml.SWITCH_PAGE,
                Tags.EMAIL,
                Tags.EMAIL_BODY & Tags.PAGE_MASK | Wbxml.WITH_CONTENT,
                Wbxml.OPAQUE,
                2,
                data[0],
                data[1],
                Wbxml.END);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);

        Serializer serializer = new Serializer();
        serializer.start(Tags.EMAIL_BODY);
        serializer.opaque(inputStream, 2);
        serializer.end();
        serializer.done();
        byte[] bytes = serializer.toByteArray();

        assertArrayEquals("Serializer mismatch", bytes, expectedBytes);
    }

    @Test
    public void testOpaqueWithZeroLengthData() throws Exception {
        byte[] data = new byte[0];
        byte[] expectedBytes = document(
                Wbxml.SWITCH_PAGE,
                Tags.EMAIL,
                Tags.EMAIL_BODY & Tags.PAGE_MASK);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);

        Serializer serializer = new Serializer();
        serializer.start(Tags.EMAIL_BODY);
        serializer.opaque(inputStream, data.length);
        serializer.end();
        serializer.done();
        byte[] bytes = serializer.toByteArray();

        assertArrayEquals("Serializer mismatch", bytes, expectedBytes);
    }

    @Test(expected = IOException.class)
    public void testOpaqueReadingBeyondEndOfStream() throws Exception {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[] { 1 });

        Serializer serializer = new Serializer();
        serializer.start(Tags.EMAIL_BODY);
        serializer.opaque(inputStream, 2);
    }

    @Test(expected = IOException.class)
    public void testOpaqueWithNegativeLength() throws Exception {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[] { 1 });

        Serializer serializer = new Serializer();
        serializer.start(Tags.EMAIL_BODY);
        serializer.opaque(inputStream, -1);
    }

    @Test(expected = IOException.class)
    public void testDocumentWithUnclosedTag() throws Exception {
        Serializer serializer = new Serializer();
        serializer.start(Tags.SYNC_SYNC);
        serializer.done();
    }

    @Test
    public void testWriteIntegerWithSmallInteger() throws Exception {
        byte[] expectedBytes = { 23 };
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Serializer.writeInteger(outputStream, 23);

        assertArrayEquals(expectedBytes, outputStream.toByteArray());
    }

    @Test
    public void testWriteIntegerWithTwoByteInteger() throws Exception {
        byte[] expectedBytes = { (byte) 0x81, 0x20 };
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Serializer.writeInteger(outputStream, 0xA0);

        assertArrayEquals(expectedBytes, outputStream.toByteArray());
    }

    @Test
    public void testWriteIntegerWithThreeByteInteger() throws Exception {
        byte[] expectedBytes = { (byte) 0x87, (byte) 0xC4, 0x40 };
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Serializer.writeInteger(outputStream, 123456);

        assertArrayEquals(expectedBytes, outputStream.toByteArray());
    }

    private byte[] document(int... data) {
        byte[] bytes = new byte[4 + data.length];
        bytes[0] = 3;   // Version 1.3
        bytes[1] = 1;   // unknown or missing public identifier
        bytes[2] = 106; // UTF-8
        bytes[3] = 0;   // String array length

        for (int i = 0, end = data.length; i < end; i++) {
            bytes[4 + i] = (byte) (data[i] & 0xFF);
        }

        return bytes;
    }
}
