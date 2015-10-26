package com.fsck.k9.helper;


import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;


public class EmailHelperTest {

    @Test
    public void getDomainFromEmailAddress_withSimpleAddress_shouldReturnDomain() throws Exception {
        String domain = EmailHelper.getDomainFromEmailAddress("hugo@example.org");

        assertEquals("example.org", domain);
    }

    @Test
    public void getDomainFromEmailAddress_withComplexAddress_shouldReturnDomain() throws Exception {
        String domain = EmailHelper.getDomainFromEmailAddress("\"@lex\"@localhost");

        assertEquals("localhost", domain);
    }

    @Test
    public void getDomainFromEmailAddress_withoutSeparator_shouldReturnNull() throws Exception {
        String domain = EmailHelper.getDomainFromEmailAddress("testexample.com");

        assertNull(domain);
    }

    @Test
    public void getDomainFromEmailAddress_withArgumentEndingWithSeparator_shouldReturnNull() throws Exception {
        String domain = EmailHelper.getDomainFromEmailAddress("test@");

        assertNull(domain);
    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = NullPointerException.class)
    public void getDomainFromEmailAddress_withNullArgument_shouldThrow() throws Exception {
        EmailHelper.getDomainFromEmailAddress(null);
    }
}
