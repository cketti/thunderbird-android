/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.fsck.k9.protocol.eas;


import java.io.IOException;

import android.content.Context;

import com.fsck.k9.protocol.eas.adapter.Serializer;
import com.fsck.k9.protocol.eas.adapter.Tags;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertArrayEquals;


public class EasProvisionTests extends RobolectricTest {

    private Context context;


    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.application;
    }

    /**
     * This test case will test PHASE_INITIAL along with a protocol version of Ex2007.
     */
    @Test
    public void testPopulateRequestEntitySerializerPhaseInitialEx2007() throws IOException {
        // Set up some parameters for the test case
        final String policyType = "Test_Policy";
        final String userAgent = "User_Agent";
        final String status = "Test_Status";
        final String policyKey = "Test_Policy_Key";
        final int phase = EasProvision.PHASE_INITIAL;
        final double protocolVersion = Eas.SUPPORTED_PROTOCOL_EX2007_DOUBLE;

        // Build the result that we are expecting
        final Serializer expectedResult = new Serializer();
        expectedResult.start(Tags.PROVISION_PROVISION);
        expectedResult.start(Tags.PROVISION_POLICIES);
        expectedResult.start(Tags.PROVISION_POLICY);
        expectedResult.data(Tags.PROVISION_POLICY_TYPE, policyType);
        // PROVISION_POLICY, PROVISION_POLICIES, PROVISION_PROVISION
        expectedResult.end().end().end().done();
        final byte[] expectedBytes = expectedResult.toByteArray();

        // Now run it through the code that we are testing
        final Serializer generatedResult = EasProvision.generateRequestEntitySerializer(
                context, userAgent, policyKey, policyType, status, phase, protocolVersion);

        // Now let's analyze the results
        assertArrayEquals(generatedResult.toByteArray(), expectedBytes);
    }

    /**
     * This test case will test PHASE_INITIAL along with a protocol version of Ex2010.
     */
    @Test
    public void testPopulateRequestEntitySerializerPhaseInitialEx2010() throws IOException {
        // Set up some parameters for the test case
        final String policyType = "Test_Policy";
        final String userAgent = "User_Agent";
        final String status = "Test_Status";
        final String policyKey = "Test_Policy_Key";
        final int phase = EasProvision.PHASE_INITIAL;
        final double protocolVersion = Eas.SUPPORTED_PROTOCOL_EX2010_SP1_DOUBLE;

        // Build the result that we are expecting
        final Serializer expectedResult = new Serializer();
        expectedResult.start(Tags.PROVISION_PROVISION);
        EasProvision.expandedAddDeviceInformationToSerializer(expectedResult, context, userAgent);
        expectedResult.start(Tags.PROVISION_POLICIES);
        expectedResult.start(Tags.PROVISION_POLICY);
        expectedResult.data(Tags.PROVISION_POLICY_TYPE, policyType);
        // PROVISION_POLICY, PROVISION_POLICIES, PROVISION_PROVISION
        expectedResult.end().end().end().done();
        final byte[] expectedBytes = expectedResult.toByteArray();

        // Now run it through the code that we are testing
        final Serializer generatedResult = EasProvision.generateRequestEntitySerializer(
                context, userAgent, policyKey, policyType, status, phase, protocolVersion);

        // Now let's analyze the results
        assertArrayEquals(generatedResult.toByteArray(), expectedBytes);
    }

    /**
     * This test case will test PHASE_WIPE.
     */
    @Test
    public void testPopulateRequestEntitySerializerPhaseWipe() throws IOException {
        // Set up some parameters for the test case
        final String policyType = "Test_Policy";
        final String userAgent = "User_Agent";
        final String status = "Test_Status";
        final String policyKey = "Test_Policy_Key";
        final int phase = EasProvision.PHASE_WIPE;
        final double protocolVersion = Eas.SUPPORTED_PROTOCOL_EX2007_DOUBLE;

        // Build the result that we are expecting
        final Serializer expectedResult = new Serializer();
        expectedResult.start(Tags.PROVISION_PROVISION);
        expectedResult.start(Tags.PROVISION_REMOTE_WIPE);
        expectedResult.data(Tags.PROVISION_STATUS, EasProvision.PROVISION_STATUS_OK);
        expectedResult.end().end().done(); // PROVISION_REMOTE_WIPE, PROVISION_PROVISION
        final byte[] expectedBytes = expectedResult.toByteArray();

        // Now run it through the code that we are testing
        final Serializer generatedResult = EasProvision.generateRequestEntitySerializer(
                context, userAgent, policyKey, policyType, status, phase, protocolVersion);

        // Now let's analyze the results
        assertArrayEquals(generatedResult.toByteArray(), expectedBytes);
    }

    /**
     * This test case will test PHASE_ACKNOWLEDGE.
     */
    @Test
    public void testPopulateRequestEntitySerializerPhaseAcknowledge() throws IOException {
        // Set up some parameters for the test case
        final String policyType = "Test_Policy";
        final String userAgent = "User_Agent";
        final String status = "Test_Status";
        final String policyKey = "Test_Policy_Key";
        final int phase = EasProvision.PHASE_ACKNOWLEDGE;
        final double protocolVersion = Eas.SUPPORTED_PROTOCOL_EX2007_DOUBLE;

        // Build the result that we are expecting
        final Serializer expectedResult = new Serializer();
        expectedResult.start(Tags.PROVISION_PROVISION);
        expectedResult.start(Tags.PROVISION_POLICIES);
        expectedResult.start(Tags.PROVISION_POLICY);
        expectedResult.data(Tags.PROVISION_POLICY_TYPE, policyType);
        expectedResult.data(Tags.PROVISION_POLICY_KEY, policyKey);
        expectedResult.data(Tags.PROVISION_STATUS, status);
        // PROVISION_POLICY, PROVISION_POLICIES, PROVISION_PROVISION
        expectedResult.end().end().end().done();
        final byte[] expectedBytes = expectedResult.toByteArray();

        // Now run it through the code that we are testing
        final Serializer generatedResult = EasProvision.generateRequestEntitySerializer(
                context, userAgent, policyKey, policyType, status, phase, protocolVersion);

        // Now let's analyze the results
        assertArrayEquals(generatedResult.toByteArray(), expectedBytes);
    }

}
