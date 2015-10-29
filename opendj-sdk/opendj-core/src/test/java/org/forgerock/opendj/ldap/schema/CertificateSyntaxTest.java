/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2014-2015 ForgeRock AS.
 *      Portions Copyright 2014 Manuel Gaupp
 */
package org.forgerock.opendj.ldap.schema;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteString;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.schema.Schema.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_CERTIFICATE_OID;
import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;
import static org.testng.Assert.*;

/**
 * Certificate syntax tests.
 */
public class CertificateSyntaxTest extends AbstractSchemaTestCase {

    /**
     * Create data for the testAcceptableValues test. This should be a table of
     * tables with 2 elements. The first one should be the value to test, the
     * second the expected result of the test.
     *
     * @return a table containing data for the testAcceptableValues Test.
     */
    @DataProvider(name = "acceptableValues")
    public Object[][] createAcceptableValues() {
        String validcert1
            = "MIICpTCCAg6gAwIBAgIJALeoA6I3ZC/cMA0GCSqGSIb3DQEBBQUAMFYxCzAJBgNV"
            + "BAYTAlVTMRMwEQYDVQQHEwpDdXBlcnRpb25lMRwwGgYDVQQLExNQcm9kdWN0IERl"
            + "dmVsb3BtZW50MRQwEgYDVQQDEwtCYWJzIEplbnNlbjAeFw0xMjA1MDIxNjM0MzVa"
            + "Fw0xMjEyMjExNjM0MzVaMFYxCzAJBgNVBAYTAlVTMRMwEQYDVQQHEwpDdXBlcnRp"
            + "b25lMRwwGgYDVQQLExNQcm9kdWN0IERldmVsb3BtZW50MRQwEgYDVQQDEwtCYWJz"
            + "IEplbnNlbjCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEApysa0c9qc8FB8gIJ"
            + "8zAb1pbJ4HzC7iRlVGhRJjFORkGhyvU4P5o2wL0iz/uko6rL9/pFhIlIMbwbV8sm"
            + "mKeNUPitwiKOjoFDmtimcZ4bx5UTAYLbbHMpEdwSpMC5iF2UioM7qdiwpAfZBd6Z"
            + "69vqNxuUJ6tP+hxtr/aSgMH2i8ECAwEAAaN7MHkwCQYDVR0TBAIwADAsBglghkgB"
            + "hvhCAQ0EHxYdT3BlblNTTCBHZW5lcmF0ZWQgQ2VydGlmaWNhdGUwHQYDVR0OBBYE"
            + "FLlZD3aKDa8jdhzoByOFMAJDs2osMB8GA1UdIwQYMBaAFLlZD3aKDa8jdhzoByOF"
            + "MAJDs2osMA0GCSqGSIb3DQEBBQUAA4GBAE5vccY8Ydd7by2bbwiDKgQqVyoKrkUg"
            + "6CD0WRmc2pBeYX2z94/PWO5L3Fx+eIZh2wTxScF+FdRWJzLbUaBuClrxuy0Y5ifj"
            + "axuJ8LFNbZtsp1ldW3i84+F5+SYT+xI67ZcoAtwx/VFVI9s5I/Gkmu9f9nxjPpK7"
            + "1AIUXiE3Qcck";

        String invalidcert1
            = "MIICpTCCAg6gAwIBBQIJALeoA6I3ZC/cMA0GCSqGSIb3DQEBBQUAMFYxCzAJBgNV"
            + "BAYTAlVTMRMwEQYDVQQHEwpDdXBlcnRpb25lMRwwGgYDVQQLExNQcm9kdWN0IERl"
            + "dmVsb3BtZW50MRQwEgYDVQQDEwtCYWJzIEplbnNlbjAeFw0xMjA1MDIxNjM0MzVa"
            + "Fw0xMjEyMjExNjM0MzVaMFYxCzAJBgNVBAYTAlVTMRMwEQYDVQQHEwpDdXBlcnRp"
            + "b25lMRwwGgYDVQQLExNQcm9kdWN0IERldmVsb3BtZW50MRQwEgYDVQQDEwtCYWJz"
            + "IEplbnNlbjCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEApysa0c9qc8FB8gIJ"
            + "8zAb1pbJ4HzC7iRlVGhRJjFORkGhyvU4P5o2wL0iz/uko6rL9/pFhIlIMbwbV8sm"
            + "mKeNUPitwiKOjoFDmtimcZ4bx5UTAYLbbHMpEdwSpMC5iF2UioM7qdiwpAfZBd6Z"
            + "69vqNxuUJ6tP+hxtr/aSgMH2i8ECAwEAAaN7MHkwCQYDVR0TBAIwADAsBglghkgB"
            + "hvhCAQ0EHxYdT3BlblNTTCBHZW5lcmF0ZWQgQ2VydGlmaWNhdGUwHQYDVR0OBBYE"
            + "FLlZD3aKDa8jdhzoByOFMAJDs2osMB8GA1UdIwQYMBaAFLlZD3aKDa8jdhzoByOF"
            + "MAJDs2osMA0GCSqGSIb3DQEBBQUAA4GBAE5vccY8Ydd7by2bbwiDKgQqVyoKrkUg"
            + "6CD0WRmc2pBeYX2z94/PWO5L3Fx+eIZh2wTxScF+FdRWJzLbUaBuClrxuy0Y5ifj"
            + "axuJ8LFNbZtsp1ldW3i84+F5+SYT+xI67ZcoAtwx/VFVI9s5I/Gkmu9f9nxjPpK7"
            + "1AIUXiE3Qcck";

        String brokencert1
            = "MIICpTCCAg6gAwIBAgIJALeoA6I3ZC/cMA0GCSqGSIb3DQEBBQUAMFYxCzAJBgNV";

        return new Object[][]{
            {ByteString.valueOfBase64(validcert1), true},
            {ByteString.valueOfUtf8(validcert1), false},
            {ByteString.valueOfBase64(invalidcert1), false},
            {ByteString.valueOfBase64(brokencert1), false},
            {ByteString.valueOfUtf8("invalid"), false}
        };
    }

    /**
     * Test acceptable values for this syntax.
     *
     * @param value
     *            The ByteString containing the value that will be tested
     * @param result
     *            The expected result of the test
     */
    @Test(dataProvider = "acceptableValues")
    public void testAcceptableValues(ByteString value, boolean result) {
        // Make sure that the specified class can be instantiated as a task.
        final Syntax syntax = getRule();

        final LocalizableMessageBuilder reason = new LocalizableMessageBuilder();

        // test the valueIsAcceptable method
        final boolean liveResult = syntax.valueIsAcceptable(value, reason);
        assertEquals(liveResult, result,
                syntax + ".valueIsAcceptable gave bad result for " + value + "reason : " + reason);

    }

    /**
     * Test acceptable values for this syntax allowing malformed certificates.
     *
     * @param value
     *            The ByteString containing the value that will be tested
     * @param result
     *            Expected result is ignored.
     */
    @Test(dataProvider = "acceptableValues")
    public void testAllowMalformedCertificates(ByteString value, Boolean result) {
        SchemaBuilder builder = new SchemaBuilder(getCoreSchema()).setOption(ALLOW_MALFORMED_CERTIFICATES, true);
        final Syntax syntax = builder.toSchema().getSyntax(SYNTAX_CERTIFICATE_OID);

        final LocalizableMessageBuilder reason = new LocalizableMessageBuilder();

        // test the valueIsAcceptable method
        final boolean liveResult = syntax.valueIsAcceptable(value, reason);
        assertTrue(liveResult, syntax + ".valueIsAcceptable gave bad result for " + value + "reason : " + reason);
    }


    /**
     * Get an instance of the attribute syntax that must be tested.
     *
     * @return An instance of the attribute syntax that must be tested.
     */
    protected Syntax getRule() {
        SchemaBuilder builder = new SchemaBuilder(getCoreSchema()).setOption(ALLOW_MALFORMED_CERTIFICATES, false);

        return builder.toSchema().getSyntax(SYNTAX_CERTIFICATE_OID);
    }
}
