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
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.controls;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Regression test for GHSA-q4wx-wj4j-4657 / OPENDJ-003 — unbounded array
 * allocation via VLV beforeCount/afterCount/offset (CWE-789 / CWE-770 / CWE-190).
 * <p>
 * A VLV-by-offset request carries attacker-controlled before/after counts. The
 * server computed {@code count = 1 + beforeCount + afterCount} and allocated
 * {@code new long[count]} without clamping it to the actual list size. With
 * {@code afterCount = Integer.MAX_VALUE} the sum overflowed to a negative int,
 * yielding a {@code NegativeArraySizeException} (or, with large non-overflowing
 * values, a multi-gigabyte allocation / {@code OutOfMemoryError}) from a single
 * search request. The fix computes the count with long arithmetic and clamps it
 * to the number of entries actually available, so the oversized window is now
 * bounded instead of faulting the search handler.
 * <p>
 * Note: this exercises the default in-memory sort path
 * ({@code EntryContainer.sortByOffset}), which requires no VLV index — the same
 * unclamped allocation also existed in {@code VLVIndex.readRange}.
 */
@SuppressWarnings("javadoc")
public class VLVOffsetAllocationDoSTest extends ControlsTestCase
{
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  private void populateDB() throws Exception
  {
    TestCaseUtils.clearBackend("userRoot", "dc=example,dc=com");
    TestCaseUtils.addEntries(
        "dn: uid=albert.zimmerman,dc=example,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: albert.zimmerman",
        "givenName: Albert",
        "sn: Zimmerman",
        "cn: Albert Zimmerman",
        "",
        "dn: uid=aaron.zimmerman,dc=example,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: aaron.zimmerman",
        "givenName: Aaron",
        "sn: Zimmerman",
        "cn: Aaron Zimmerman",
        "",
        "dn: uid=mary.jones,dc=example,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: mary.jones",
        "givenName: Mary",
        "sn: Jones",
        "cn: Mary Jones");
  }

  /**
   * A single VLV-by-offset search with {@code afterCount = Integer.MAX_VALUE}
   * (which previously overflowed {@code 1 + beforeCount + afterCount} into a
   * negative array size) must now be clamped to the list size and return the
   * same bounded page as a normal request — not fault the search handler with a
   * {@code NegativeArraySizeException} / {@code OTHER}.
   */
  @Test
  public void offsetAllocationOverflowIsClamped() throws Exception
  {
    populateDB();

    // Baseline: a normal, bounded window.
    SearchRequest ok = newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
        .addControl(new ServerSideSortRequestControl("givenName"))
        .addControl(new VLVRequestControl(0, 3, 1, 0));
    InternalSearchOperation okOp = getRootConnection().processSearch(ok);
    assertEquals(okOp.getResultCode(), ResultCode.SUCCESS);

    // Oversized window: afterCount = Integer.MAX_VALUE. Before the fix this
    // overflowed to new long[-2147483648]; a larger non-overflowing count would
    // instead force a multi-gigabyte allocation (OutOfMemoryError).
    SearchRequest bad = newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
        .addControl(new ServerSideSortRequestControl("givenName"))
        .addControl(new VLVRequestControl(0, Integer.MAX_VALUE, 1, 0));
    InternalSearchOperation badOp = getRootConnection().processSearch(bad);

    assertEquals(badOp.getResultCode(), ResultCode.SUCCESS,
        "oversized VLV afterCount must be clamped to the list size, not fault the search handler "
            + "(GHSA-q4wx-wj4j-4657): " + badOp.getErrorMessage());
    // The page is bounded by the number of entries that actually exist (offset 1
    // onwards), i.e. the same result as the bounded baseline request.
    assertEquals(badOp.getSearchEntries().size(), okOp.getSearchEntries().size());
  }
}
