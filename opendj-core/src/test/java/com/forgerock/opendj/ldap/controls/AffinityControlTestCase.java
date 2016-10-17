/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.controls;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.forgerock.opendj.ldap.controls.ControlsTestCase;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.testng.annotations.Test;

import static org.testng.Assert.*;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("javadoc")
public class AffinityControlTestCase extends ControlsTestCase {

    @Test
    public void testControl() throws Exception {
        // Send this control with a search request to ensure it is encoded/decoded correctly
        final SearchRequest req = Requests.newSearchRequest(DN.valueOf("uid=user.1,ou=people,o=test"),
                SearchScope.BASE_OBJECT, Filter.objectClassPresent());
        final AffinityControl control = AffinityControl.newControl(ByteString.valueOfUtf8("value"), false);
        req.addControl(control);
        try (Connection con = TestCaseUtils.getInternalConnection()) {
            final List<SearchResultEntry> entries = new ArrayList<>();
            con.search(req, entries);
            assertThat(entries).hasAtLeastOneElementOfType(SearchResultEntry.class);
            final SearchResultEntry entry = entries.get(0);
            assertThat(entry.getControls()).hasSize(0);
        }
    }

    @Test
    public void testControlGeneratesRandomValue() throws Exception {
        final AffinityControl control = AffinityControl.newControl(true);
        assertTrue(control.isCritical());
        ByteString value1 = control.getAffinityValue();
        assertThat(value1).isNotNull();
        assertThat(value1.length()).isNotZero();

        final AffinityControl control2 = AffinityControl.newControl(false);
        ByteString value2 = control2.getAffinityValue();
        assertFalse(control2.isCritical());
        assertThat(value2).isNotNull();
        assertThat(value2.length()).isNotZero();

        assertThat(value1).isNotEqualTo(value2);
    }
}
