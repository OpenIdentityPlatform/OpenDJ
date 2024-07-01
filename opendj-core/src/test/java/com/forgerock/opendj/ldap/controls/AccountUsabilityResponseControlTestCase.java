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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions Copyright 2015 ForgeRock AS.
 */

package com.forgerock.opendj.ldap.controls;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.forgerock.opendj.ldap.controls.ControlsTestCase;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.testng.annotations.Test;

/**
 * Tests the account usability response control.
 */
@SuppressWarnings("javadoc")
public class AccountUsabilityResponseControlTestCase extends ControlsTestCase {
    @Test
    public void testInvalidResponseControl() throws Exception {
        // Don't send the request control and hence there
        // shouldn't be response.
        final SearchRequest req =
                Requests.newSearchRequest(DN.valueOf("uid=user.1,ou=people,o=test"),
                        SearchScope.BASE_OBJECT, Filter.objectClassPresent());
        final Connection con = TestCaseUtils.getInternalConnection();
        final List<SearchResultEntry> entries = new ArrayList<>();
        con.search(req, entries);
        assertTrue(entries.size() > 0);
        final SearchResultEntry entry = entries.get(0);
        final AccountUsabilityResponseControl aurctrl =
                entry.getControl(AccountUsabilityResponseControl.DECODER, new DecodeOptions());
        assertNull(aurctrl);
    }

    @Test
    public void testValidResponseControl() throws Exception {
        // Send this control with a search request and see that you get a valid response.
        final SearchRequest req =
                Requests.newSearchRequest(DN.valueOf("uid=user.1,ou=people,o=test"),
                        SearchScope.BASE_OBJECT, Filter.objectClassPresent());
        final AccountUsabilityRequestControl control =
                AccountUsabilityRequestControl.newControl(false);
        req.addControl(control);
        final Connection con = TestCaseUtils.getInternalConnection();
        final List<SearchResultEntry> entries = new ArrayList<>();
        con.search(req, entries);
        assertTrue(entries.size() > 0);
        final SearchResultEntry entry = entries.get(0);
        final AccountUsabilityResponseControl aurctrl =
                entry.getControl(AccountUsabilityResponseControl.DECODER, new DecodeOptions());
        assertFalse(aurctrl.isExpired());
        assertFalse(aurctrl.isLocked());
        assertFalse(aurctrl.isInactive());
    }
}
