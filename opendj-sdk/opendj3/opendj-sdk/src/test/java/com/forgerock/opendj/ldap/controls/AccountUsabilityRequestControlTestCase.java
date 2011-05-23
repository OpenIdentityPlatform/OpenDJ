/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package com.forgerock.opendj.ldap.controls;



import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.opends.sdk.*;
import org.opends.sdk.controls.Control;
import org.opends.sdk.controls.ControlsTestCase;
import org.opends.sdk.requests.Requests;
import org.opends.sdk.requests.SearchRequest;
import org.opends.sdk.responses.SearchResultEntry;
import org.testng.annotations.Test;

import com.forgerock.opendj.ldap.controls.AccountUsabilityRequestControl;



/**
 * Tests the account usability request control.
 */
public class AccountUsabilityRequestControlTestCase extends ControlsTestCase
{
  @Test()
  public void testControl() throws Exception
  {
    // Send this control with a search request and see that you get
    // a valid response.
    final SearchRequest req = Requests.newSearchRequest(DN
        .valueOf("uid=user.1,ou=people,o=test"), SearchScope.BASE_OBJECT,
        Filter.getObjectClassPresentFilter());
    final AccountUsabilityRequestControl control = AccountUsabilityRequestControl
        .newControl(false);
    req.addControl(control);
    final Connection con = TestCaseUtils.getInternalConnection();
    final List<SearchResultEntry> entries = new ArrayList<SearchResultEntry>();
    con.search(req, entries);
    assertTrue(entries.size() > 0);
    final SearchResultEntry entry = entries.get(0);
    final Control ctrl = entry.getControls().get(0);
    if (!ctrl.getOID().equals("1.3.6.1.4.1.42.2.27.9.5.8"))
    {
      throw new Exception("expected control response 1.3.6.1.4.1.42.2.27.9.5.8");
    }
  }
}
