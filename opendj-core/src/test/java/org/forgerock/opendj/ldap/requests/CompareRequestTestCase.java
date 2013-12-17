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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS
 */

package org.forgerock.opendj.ldap.requests;

import static org.fest.assertions.Assertions.assertThat;

import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.DN;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests compare requests.
 */
@SuppressWarnings("javadoc")
public class CompareRequestTestCase extends RequestsTestCase {
    private static final CompareRequest NEW_COMPARE_REQUEST2 = Requests.newCompareRequest(
            "uid=user.0,ou=people,o=test", "uid", "user.0");
    private static final CompareRequest NEW_COMPARE_REQUEST = Requests.newCompareRequest("uid=user.0,ou=people,o=test",
            "cn", "user.0");

    @DataProvider(name = "compareRequests")
    private Object[][] getCompareRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected CompareRequest[] newInstance() {
        return new CompareRequest[] {
            NEW_COMPARE_REQUEST,
            NEW_COMPARE_REQUEST2 };
    }

    @Override
    protected Request copyOf(Request original) {
        return Requests.copyOfCompareRequest((CompareRequest) original);
    }

    @Override
    protected Request unmodifiableOf(Request original) {
        return Requests.unmodifiableCompareRequest((CompareRequest) original);
    }

    @Test(dataProvider = "compareRequests")
    public void testModifiableRequest(final CompareRequest original) {
        final String newValue = "uid=user.0";
        final String attrDescription = "newattributedescription";
        final CompareRequest copy = (CompareRequest) copyOf(original);
        copy.setName(newValue);
        copy.setAttributeDescription(attrDescription);
        assertThat(copy.getName().toString()).isEqualTo(newValue);
        assertThat(original.getName().toString()).isNotEqualTo(newValue);
        assertThat(copy.getAttributeDescription().toString()).isEqualTo(attrDescription);
        assertThat(original.getAttributeDescription()).isNotEqualTo(attrDescription);
    }

    @Test(dataProvider = "compareRequests")
    public void testUnmodifiableRequest(final CompareRequest original) {
        final CompareRequest unmodifiable = (CompareRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getAssertionValue()).isEqualTo(original.getAssertionValue());
        assertThat(unmodifiable.getAttributeDescription()).isEqualTo(original.getAttributeDescription());
        assertThat(unmodifiable.getName().toString()).isEqualTo(original.getName().toString());
    }

    @Test(dataProvider = "compareRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetAssertionValue(final CompareRequest original) {
        final CompareRequest unmodifiable = (CompareRequest) unmodifiableOf(original);
        unmodifiable.setAssertionValue("newValue");
    }

    @Test(dataProvider = "compareRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetAttributeDescription(final CompareRequest original) {
        final CompareRequest unmodifiable = (CompareRequest) unmodifiableOf(original);
        unmodifiable.setAttributeDescription(AttributeDescription.valueOf("sn"));
    }

    @Test(dataProvider = "compareRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetAttributeDescription2(final CompareRequest original) {
        final CompareRequest unmodifiable = (CompareRequest) unmodifiableOf(original);
        unmodifiable.setAttributeDescription("sn");
    }

    @Test(dataProvider = "compareRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetName(final CompareRequest original) {
        final CompareRequest unmodifiable = (CompareRequest) unmodifiableOf(original);
        unmodifiable.setName("uid=user.0");
    }

    @Test(dataProvider = "compareRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetName2(final CompareRequest original) {
        final CompareRequest unmodifiable = (CompareRequest) unmodifiableOf(original);
        unmodifiable.setName(DN.valueOf("uid=user.0"));
    }
}
