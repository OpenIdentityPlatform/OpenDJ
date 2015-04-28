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
 *      Portions Copyright 2015 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.controls;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlsTestCase;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests the account usability request control.
 */
@SuppressWarnings("javadoc")
public class AccountUsabilityRequestControlTestCase extends ControlsTestCase {
    @Test
    public void testControl() throws Exception {
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
        final Control ctrl = entry.getControls().get(0);
        assertEquals(ctrl.getOID(), "1.3.6.1.4.1.42.2.27.9.5.8", "expected control response 1.3.6.1.4.1.42.2.27.9.5.8");
    }
}
