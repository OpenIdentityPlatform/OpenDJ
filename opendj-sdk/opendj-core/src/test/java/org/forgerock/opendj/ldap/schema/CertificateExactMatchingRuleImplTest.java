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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2014 Manuel Gaupp
 *      Portions Copyright 2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * This class tests the certificateExactMatch matching rule.
 */
@SuppressWarnings("javadoc")
public class CertificateExactMatchingRuleImplTest extends AbstractSchemaTestCase {

    /**
     * Generate data for the certificateExactMatch matching rule test.
     */
    @DataProvider(name = "certificateExactMatchingRules")
    public Object[][] createCertificateExactMatchingRuleTest() {
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

        String incompleteCert = "MIICpTCCAg6gAwIBAgIJALeoA6I3ZC/cMA0GCSqGSIb3DQEBBQUAMFYxCzAJBgNV";

        String assertion = "{ serialNumber 13233831500277100508, issuer rdnSequence:\""
                + "CN=Babs Jensen,OU=Product Development,L=Cupertione,C=US\" }";
        String assertionWithSpaces = "{    serialNumber     13233831500277100508,  issuer  rdnSequence:\""
                + "CN=Babs Jensen,OU=Product Development, L=Cupertione,C=US\" }";
        String assertionDNencoded = "{ serialNumber 13233831500277100508, issuer rdnSequence:\""
                + "cn=BABS Jensen,ou=Product Development,L=Cupertione,c=#5553\" }";
        String assertionWrong = "{ serialNumber 13233831511277100508, issuer rdnSequence:\""
                + "CN=Babs Jensen,OU=Product Development,L=Cupertione,C=US\" }";

        return new Object[][]{
            {ByteString.valueOfBase64(validcert1), ByteString.valueOfUtf8(assertion), ConditionResult.TRUE},
            {ByteString.valueOfBase64(validcert1), ByteString.valueOfUtf8(assertionWithSpaces), ConditionResult.TRUE},
            {ByteString.valueOfBase64(validcert1), ByteString.valueOfUtf8(assertionDNencoded), ConditionResult.TRUE},
            {ByteString.valueOfBase64(validcert1), ByteString.valueOfUtf8(assertionWrong), ConditionResult.FALSE},
            {ByteString.valueOfBase64(incompleteCert), ByteString.valueOfBase64(incompleteCert), ConditionResult.TRUE},
            {ByteString.valueOfBase64(validcert1), ByteString.valueOfBase64(validcert1), ConditionResult.TRUE}
        };
    }

    /**
     * Generate valid assertion values for the certificateExactMatch matching
     * rule test.
     */
    @DataProvider(name = "certificateExactMatchValidAssertionValues")
    public Object[][] createCertificateExactMatchingRuleValidAssertionValues() {
        return new Object[][]{
            {"{serialNumber 123,issuer rdnSequence:\"c=DE\"}"},
            {"{serialNumber 123,issuer rdnSequence:\"\"}"},
            {"{serialNumber 0123,issuer rdnSequence:\"cn=issuer\"}"},
            {"{  serialNumber  123,  issuer  rdnSequence:\"c=DE\"  }"},
            {"{serialNumber 123,issuer rdnSequence:\"cn=escaped\"\"dquotes\"}"},
            {"{serialNumber 123,issuer rdnSequence:\"cn=\u00D6\u00C4\"}"}
        };
    }

    /**
     * Generate invalid assertion values for the certificateExactMatch matching
     * rule test.
     */
    @DataProvider(name = "certificateExactMatchInvalidAssertionValues")
    public Object[][] createCertificateExactMatchingRuleInvalidAssertionValues() {
        return new Object[][]{
            {"{serialnumber 123,issuer rdnSequence:\"c=DE\"}"},
            {"{serialNumber 123,issuer rdnSequence:\"invalid\"}"},
            {"{serialNumber 0123,issuer rdnSequence: \"cn=issuer\"}"},
            {"{  serialNumber  123  ,  issuer  rdnSequence:\"c=DE\"  }  trailing"}
        };
    }

    /**
     * Generate invalid atribute values for the certificateExactMatch matching
     * rule test.
     */
    @DataProvider(name = "certificateExactMatchInvalidAttributeValues")
    public Object[][] createCertificateExactMatchingRuleInvalidAttributeValues()
            throws Exception {
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
            {ByteString.valueOfBase64(invalidcert1)},
            {ByteString.valueOfBase64(brokencert1)}
        };
    }

    /**
     * Get an instance of the matching rule.
     *
     * @return An instance of the matching rule to test.
     */
    protected MatchingRule getRule() {
        return CoreSchema.getCertificateExactMatchingRule();
    }

    /**
     * Test the normalization and the comparison of valid values.
     */
    @Test(dataProvider = "certificateExactMatchingRules")
    public void certificateExactMatchingRules(ByteString attributeValue,
            ByteString assertionValue, ConditionResult result) throws DecodeException {
        MatchingRule rule = getRule();

        // normalize the 2 provided values and check that they are equal
        assertEquals(rule.getAssertion(assertionValue).matches(rule.normalizeAttributeValue(attributeValue)), result);
    }

    /**
     * Test that valid assertion values are accepted.
     */
    @Test(dataProvider = "certificateExactMatchValidAssertionValues")
    public void certificateExactMatchingRuleValidAssertionValues(String value)
            throws DecodeException {
        // Get the instance of the rule to be tested.
        MatchingRule rule = getRule();

        // normalize the provided assertion values
        rule.getAssertion(ByteString.valueOfUtf8(value));
    }

    /**
     * Test that invalid assertion values are rejected.
     */
    @Test(dataProvider = "certificateExactMatchInvalidAssertionValues",
            expectedExceptions = { DecodeException.class })
    public void certificateExactMatchingRuleInvalidAssertionValues(String value)
            throws DecodeException {
        // Get the instance of the rule to be tested.
        MatchingRule rule = getRule();

        // normalize the provided assertion value
        rule.getAssertion(ByteString.valueOfUtf8(value));
    }

    /**
     * Test that invalid attribute values are returned with the original
     * ByteString.
     */
    @Test(dataProvider = "certificateExactMatchInvalidAttributeValues")
    public void certificateExactMatchingRuleInvalidAttributeValues(ByteString value)
            throws DecodeException {
        // Get the instance of the rule to be tested.
        MatchingRule rule = getRule();

        // normalize the provided assertion value
        Assertion normalizedAssertionValue = rule.getAssertion(value);
        assertEquals(normalizedAssertionValue.matches(value), ConditionResult.TRUE);
    }
}
