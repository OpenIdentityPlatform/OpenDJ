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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.replication.common;

import static org.assertj.core.api.Assertions.*;
import static org.testng.Assert.*;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.util.Pair;
import org.opends.server.replication.ReplicationTestCase;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class MultiDomainServerStateTest extends ReplicationTestCase
{

  private static final CSN csn1 = new CSN(1, 2, 3);
  private static final CSN csn2 = new CSN(4, 5, 6);
  private static final CSN csn3 = new CSN(7, 8, 3);

  private static DN dn1;
  private static DN dn2;
  private static DN dn3;

  @BeforeClass
  public void setBaseDNs() throws Exception
  {
    dn1 = DN.valueOf("o=test1");
    dn2 = DN.valueOf("o=test2");
    dn3 = DN.valueOf("o=test3");
  }

  @DataProvider
  public Object[][] decodeAndEncodeData()
  {
    String cookie = "o=test1:" + csn1 + ";o=test2:" + csn2 + ";o=test6:";
    return new Object[][] {
      { "", "" },
      { "o=test1:", "o=test1:;" },
      { ";;o=test1:;;", ":;o=test1:;" },
      { cookie, cookie + ";" },
      { "o=test1:" + csn1 + ";o=test2:" + csn2 + ";;o=test6:",
        ":;o=test1:" + csn1 + ";o=test2:" + csn2 + ";o=test6:;" },
    };
  }

  @Test(dataProvider = "decodeAndEncodeData")
  public void decodeAndEncode(String cookie, String expectedCookie) throws Exception
  {
    final MultiDomainServerState state = new MultiDomainServerState(cookie);
    assertEquals(state.toString(), expectedCookie);
  }

  @DataProvider
  public Object[][] copyCtorData()
  {
    return new Object[][] {
      { "" },
      { "o=test1:" + csn1 + ";" },
      { "o=test1:" + csn1 + ";o=test2:" + csn2 + ";" },
      { "o=test1:" + csn1 + ";o=test2:" + csn2 + ";o=test6:;" },
    };
  }

  @Test(dataProvider = "copyCtorData")
  public void copyCtor(String cookie) throws Exception
  {
    final MultiDomainServerState state = new MultiDomainServerState(cookie);
    final MultiDomainServerState copy = new MultiDomainServerState(state);
    assertEquals(copy.toString(), cookie);
  }

  @Test
  public void testUpdateCSN() throws Exception
  {
    final MultiDomainServerState state = new MultiDomainServerState();
    assertTrue(state.update(dn1, csn1));
    assertTrue(state.update(dn2, csn2));

    assertFalse(state.update(dn1, (CSN) null));
    assertFalse(state.update(dn1, csn1));
    assertTrue(state.update(dn1, csn3));
    final String expected = "o=test1:" + csn3 + ";o=test2:" + csn2 + ";";
    assertEquals(state.toString(), expected);
  }

  @Test
  public void testUpdateServerState() throws Exception
  {
    final MultiDomainServerState state = new MultiDomainServerState();
    final ServerState ss1 = new ServerState();
    assertTrue(ss1.update(csn3));
    final ServerState ss2 = new ServerState();
    assertTrue(ss2.update(csn2));
    state.update(dn1, ss1);
    state.update(dn2, ss2);

    final String expected = "o=test1:" + csn3 + ";o=test2:" + csn2 + ";";
    assertEquals(state.toString(), expected);
  }

  @Test
  public void testUpdateMultiDomainServerState() throws Exception
  {
    final MultiDomainServerState state1 = new MultiDomainServerState();
    state1.update(dn1, csn3);
    state1.update(dn2, csn2);
    final MultiDomainServerState state2 = new MultiDomainServerState();
    state2.update(state1);

    assertSame(csn3, state2.getCSN(dn1, csn3.getServerId()));
    assertSame(csn2, state2.getCSN(dn2, csn2.getServerId()));
    assertTrue(state1.equalsTo(state2));
  }

  @Test(dependsOnMethods = { "testUpdateCSN" })
  public void testEqualsTo() throws Exception
  {
    final MultiDomainServerState state1 = new MultiDomainServerState();
    assertTrue(state1.update(dn1, csn3));

    final MultiDomainServerState state2 = new MultiDomainServerState();
    assertFalse(state1.equalsTo(state2));

    assertTrue(state2.update(dn1, csn1));
    assertFalse(state1.equalsTo(state2));

    assertTrue(state2.update(dn1, csn3));
    assertTrue(state1.equalsTo(state2));

    assertTrue(state2.update(dn2, csn2));
    assertFalse(state1.equalsTo(state2));
  }

  @Test(dependsOnMethods = { "testUpdateCSN" })
  public void testIsEmpty() throws Exception
  {
    final MultiDomainServerState state = new MultiDomainServerState();
    assertTrue(state.isEmpty());

    assertTrue(state.update(dn1, csn1));
    assertFalse(state.isEmpty());
    assertThat(state).containsOnly(dn1);

    assertTrue(state.update(dn2, csn2));
    assertFalse(state.isEmpty());
    assertThat(state).containsOnly(dn1, dn2);

    state.clear();
    assertTrue(state.isEmpty());
  }

  @Test(dependsOnMethods = { "testUpdateCSN" })
  public void testRemoveCSN() throws Exception
  {
    final MultiDomainServerState state = getLastAliveCSNs();
    assertNull(state.getCSN(dn3, 42));

    assertFalse(state.removeCSN(dn3, csn1));
    assertSame(csn1, state.getCSN(dn1, csn1.getServerId()));
    assertSame(csn1, state.getCSN(dn2, csn1.getServerId()));
    assertSame(csn2, state.getCSN(dn2, csn2.getServerId()));

    assertFalse(state.removeCSN(dn1, csn2));
    assertSame(csn1, state.getCSN(dn1, csn1.getServerId()));
    assertSame(csn1, state.getCSN(dn2, csn1.getServerId()));
    assertSame(csn2, state.getCSN(dn2, csn2.getServerId()));

    assertTrue(state.removeCSN(dn2, csn1));
    assertSame(csn1, state.getCSN(dn1, csn1.getServerId()));
    assertNull(state.getCSN(dn2, csn1.getServerId()));
    assertSame(csn2, state.getCSN(dn2, csn2.getServerId()));
  }

  private MultiDomainServerState getLastAliveCSNs()
  {
    final MultiDomainServerState lastAliveCSNs = new MultiDomainServerState();
    assertTrue(lastAliveCSNs.update(dn1, csn1));
    assertTrue(lastAliveCSNs.update(dn2, csn1));
    assertTrue(lastAliveCSNs.update(dn2, csn2));
    return lastAliveCSNs;
  }

  @Test(dependsOnMethods = { "testUpdateCSN" })
  public void testGetOldestCSNExcluding_null() throws Exception
  {
    final MultiDomainServerState lastAliveCSNs = getLastAliveCSNs();
    assertEquals(lastAliveCSNs.getOldestCSNExcluding(null), Pair.of(dn1, csn1));
  }

  @Test(dependsOnMethods = { "testUpdateCSN" })
  public void testGetOldestCSNExcluding_empty() throws Exception
  {
    final MultiDomainServerState lastAliveCSNs = getLastAliveCSNs();
    final MultiDomainServerState excluded = new MultiDomainServerState();

    assertEquals(lastAliveCSNs.getOldestCSNExcluding(excluded), Pair.of(dn1, csn1));
  }

  @Test(dependsOnMethods = { "testUpdateCSN" })
  public void testGetOldestCSNExcluding_currentOldestCSN_givesNewOldestCSN() throws Exception
  {
    final MultiDomainServerState lastAliveCSNs = getLastAliveCSNs();

    final MultiDomainServerState excluded = new MultiDomainServerState();
    excluded.update(dn1, csn1);

    assertEquals(lastAliveCSNs.getOldestCSNExcluding(excluded), Pair.of(dn2, csn1));
  }

  @Test(dependsOnMethods = { "testUpdateCSN" })
  public void testGetOldestCSNExcluding_CSNOlderThanCurrentOldestCSN_givesNewOldestCSN() throws Exception
  {
    final MultiDomainServerState lastAliveCSNs = getLastAliveCSNs();

    final MultiDomainServerState excluded = new MultiDomainServerState();
    excluded.update(dn1, csn1);
    final CSN olderThanCSN1 = new CSN(0, 2, 3);
    assertEquals(olderThanCSN1.getServerId(), csn1.getServerId());
    assertTrue(olderThanCSN1.isOlderThan(csn1));
    excluded.update(dn2, olderThanCSN1);

    assertEquals(lastAliveCSNs.getOldestCSNExcluding(excluded), Pair.of(dn2, csn1));
  }

  @Test(dependsOnMethods = { "testUpdateCSN" })
  public void testGetOldestCSNExcluding_CSNNewerThanCurrentOldestCSN_givesNewOldestCSN() throws Exception
  {
    final MultiDomainServerState lastAliveCSNs = getLastAliveCSNs();

    final MultiDomainServerState excluded = new MultiDomainServerState();
    excluded.update(dn1, csn1);
    final CSN newerThanCSN1 = new CSN(42, 2, 3);
    assertEquals(newerThanCSN1.getServerId(), csn1.getServerId());
    assertTrue(newerThanCSN1.isNewerThan(csn1));
    excluded.update(dn2, newerThanCSN1);

    assertEquals(lastAliveCSNs.getOldestCSNExcluding(excluded), Pair.of(dn2, csn1));
  }

}
