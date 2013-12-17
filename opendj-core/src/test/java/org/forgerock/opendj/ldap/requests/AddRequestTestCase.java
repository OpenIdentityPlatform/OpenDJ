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
 * Tests ADD requests.
 */
@SuppressWarnings("javadoc")
public class AddRequestTestCase extends RequestsTestCase {
    private static final AddRequest NEW_ADD_REQUEST = Requests.newAddRequest(DN.valueOf("uid=addrequest1"));
    private static final AddRequest NEW_ADD_REQUEST2 = Requests.newAddRequest("cn=addrequesttestcase");
    private static final AddRequest NEW_ADD_REQUEST3 = Requests.newAddRequest("dn: ou=People,o=test",
            "objectClass: top", "objectClass: organizationalUnit", "ou: People");

    @DataProvider(name = "addRequests")
    private Object[][] getAddRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected AddRequest[] newInstance() {
        return new AddRequest[] { NEW_ADD_REQUEST, NEW_ADD_REQUEST2, NEW_ADD_REQUEST3 };
    }

    @Override
    protected Request copyOf(final Request original) {
        return Requests.copyOfAddRequest((AddRequest) original);
    }

    @Override
    protected Request unmodifiableOf(final Request original) {
        return Requests.unmodifiableAddRequest((AddRequest) original);
    }

    @Test(dataProvider = "addRequests")
    public void testModifiableRequest(final AddRequest original) {
        final String newValue = "uid=newName";
        final AddRequest copy = (AddRequest) copyOf(original);

        copy.setName(newValue);
        assertThat(copy.getName().toString()).isEqualTo(newValue);
        assertThat(original.getName().toString()).isNotEqualTo(newValue);

        copy.addAttribute("cn", "Bob");
        assertThat(copy.getAttribute("cn")).isNotEmpty();
        assertThat(original.getAttribute("cn")).isNull();

        copy.clearAttributes();
        assertThat(copy.getAttribute("cn")).isNull();
        assertThat(copy.getAttributeCount()).isEqualTo(0);

        copy.addAttribute("sn", "Bobby");
        assertThat(original.getAttribute("sn")).isNull();
        assertThat(copy.containsAttribute("sn", "Bobby")).isTrue();

        copy.removeAttribute("sn");
        assertThat(copy.containsAttribute("sn", "Bobby")).isFalse();
    }

    @Test(dataProvider = "addRequests")
    public void testUnmodifiableRequest(final AddRequest original) {
        final AddRequest unmodifiable = (AddRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getName().toString()).isEqualTo(original.getName().toString());
    }

    @Test(dataProvider = "addRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetName(final AddRequest original) {
        final AddRequest unmodifiable = (AddRequest) unmodifiableOf(original);
        unmodifiable.setName("cn=myexample");
    }

    @Test(dataProvider = "addRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetDNName(final AddRequest original) {
        final AddRequest unmodifiable = (AddRequest) unmodifiableOf(original);
        unmodifiable.setName(DN.valueOf("cn=mynewexample"));
    }

    @Test(dataProvider = "addRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableAddAttribute(final AddRequest original) {
        final AddRequest unmodifiable = (AddRequest) unmodifiableOf(original);
        unmodifiable.addAttribute("sn", "Bobby");
    }

    @Test(dataProvider = "addRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableAddAttribute2(final AddRequest original) {
        final AddRequest unmodifiable = (AddRequest) unmodifiableOf(original);
        unmodifiable.addAttribute(org.forgerock.opendj.ldap.Attributes.emptyAttribute("description"));
    }

    @Test(dataProvider = "addRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableRemoveAttribute(final AddRequest original) {
        final AddRequest unmodifiable = (AddRequest) unmodifiableOf(original);
        unmodifiable.removeAttribute("sn", "Bobby");
    }

    @Test(dataProvider = "addRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableRemoveAttribute2(final AddRequest original) {
        final AddRequest unmodifiable = (AddRequest) unmodifiableOf(original);
        unmodifiable.removeAttribute(AttributeDescription.valueOf("description"));
    }

    @Test(dataProvider = "addRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableReplaceAttribute(final AddRequest original) {
        final AddRequest unmodifiable = (AddRequest) unmodifiableOf(original);
        unmodifiable.replaceAttribute("sn", "cn");
    }

    @Test(dataProvider = "addRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableReplaceAttribute2(final AddRequest original) {
        final AddRequest unmodifiable = (AddRequest) unmodifiableOf(original);
        unmodifiable.replaceAttribute("sn");
    }

    @Test(dataProvider = "addRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableReplaceAttribute3(final AddRequest original) {
        final AddRequest unmodifiable = (AddRequest) unmodifiableOf(original);
        unmodifiable.replaceAttribute(org.forgerock.opendj.ldap.Attributes.emptyAttribute("description"));
    }

    @Test(dataProvider = "addRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableClearAttribute(final AddRequest original) {
        final AddRequest unmodifiable = (AddRequest) unmodifiableOf(original);
        unmodifiable.clearAttributes();
    }
}
