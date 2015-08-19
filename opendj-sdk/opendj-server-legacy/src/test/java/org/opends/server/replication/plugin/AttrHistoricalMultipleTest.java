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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.util.TimeThread;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Test AttrHistoricalMultiple. */
@SuppressWarnings("javadoc")
public class AttrHistoricalMultipleTest extends ReplicationTestCase
{
  private static enum E
  {
    CONFLICT(true), CONFLICT_BUT_SHOULD_NOT_BE(true), NO_CONFLICT(false);

    private final boolean expectedConflictStatus;

    private E(boolean expectedResultForReplay)
    {
      this.expectedConflictStatus = expectedResultForReplay;
    }

    private boolean getExpectedResult()
    {
      return this.expectedConflictStatus;
    }
  }

  private CSNGenerator csnGen = new CSNGenerator(1025, System.currentTimeMillis());
  private AttrHistoricalMultiple attrHist;
  private CSN csn;
  private Entry entry;
  /** Avoids declaring the variable in the tests. */
  private Modification mod;

  @BeforeMethod
  public void localSetUp() throws Exception
  {
    attrHist = new AttrHistoricalMultiple();
    csn = csnGen.newCSN();
    entry = new Entry(null, null, null, null);
  }

  @AfterMethod
  public void localTearDown() throws Exception
  {
    attrHist = null;
    csn = null;
  }

  /** Build some data for the AttrInfo test below. */
  @DataProvider(name = "attrInfo")
  public Object[][] createData()
  {
    ByteString att1 = ByteString.valueOf("string");
    ByteString att2 = ByteString.valueOf("value");
    ByteString att3 = ByteString.valueOf("again");

    CSN del1 = new CSN(1,  0,  1);
    CSN del2 = new CSN(1,  1,  1);
    CSN del3 = new CSN(1,  0,  2);

    CSN upd1 = new CSN(TimeThread.getTime(), 123, 45);
    CSN upd2 = new CSN(TimeThread.getTime() + 1000, 123,  45);
    CSN upd3 = new CSN(TimeThread.getTime(), 321, 54);

    return new Object[][]
    {
    { att1, del1, upd1 },
    { att2, del2, upd2 },
    { att3, del3, upd3 },
    { att3, upd3, upd3 } };
  }

  /** Create a AttrInfo and check the methods. */
  @Test(dataProvider = "attrInfo")
  public void attrInfo(ByteString att, CSN deleteTime, CSN updateTime) throws Exception
  {
    // Create an empty AttrInfo
    AttrHistoricalMultiple attrInfo1 = new AttrHistoricalMultiple();

    // Check
    attrInfo1.add(att, updateTime);
    Set<AttrValueHistorical> values1 = attrInfo1.getValuesHistorical();
    assertEquals(values1.size(), 1);
    AttrValueHistorical valueInfo1 = new AttrValueHistorical(att, updateTime, null);
    assertTrue(values1.contains(valueInfo1));

    // Check constructor with parameter
    AttrValueHistorical valueInfo2 = new AttrValueHistorical(att, updateTime, deleteTime);
    AttrHistoricalMultiple attrInfo2 = new AttrHistoricalMultiple(
        deleteTime, updateTime, Collections.singletonMap(valueInfo2, valueInfo2));

    // Check equality
    //assertTrue(attrInfo1.getDeleteTime().compareTo(attrInfo2.getDeleteTime())==0);

    //  Check constructor with time parameter and not Value
    AttrHistoricalMultiple attrInfo3 = new AttrHistoricalMultiple(deleteTime, updateTime, null);
    attrInfo3.add(att, updateTime);
    Set<AttrValueHistorical> values3 = attrInfo3.getValuesHistorical();
    assertEquals(values3.size(), 1);
    valueInfo1 = new AttrValueHistorical(att, updateTime, null);
    assertTrue(values3.contains(valueInfo1));

    // Check duplicate
    AttrHistoricalMultiple attrInfo4 = attrInfo3.duplicate();
    Set<AttrValueHistorical> values4 = attrInfo4.getValuesHistorical();
    assertEquals(attrInfo4.getDeleteTime().compareTo(attrInfo3.getDeleteTime()), 0);
    assertEquals(values4.size(), values3.size());

    // Check
    attrInfo4.delete(att, updateTime);
    assertEquals(attrInfo4.getValuesHistorical().size(), 1);

    // Check
    AttributeType type = DirectoryServer.getAttributeType("description");
    attrInfo3.delete(Attributes.create(type, att), updateTime) ;
    assertEquals(attrInfo3.getValuesHistorical().size(), 1);

    // Check
    attrInfo2.delete(updateTime);
    assertEquals(attrInfo2.getValuesHistorical().size(), 0);
  }

  @Test
  public void replay_addDeleteSameTime() throws Exception
  {
    mod = newModification(ADD, "X");
    replayOperation(csn, entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);

    mod = newModification(DELETE, "X");
    replayOperation(csn, entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);
  }

  @Test
  public void replay_addThenAddThenOlderDelete() throws Exception
  {
    CSN[] t = newCSNs(3);

    mod = newModification(ADD, "X");
    replayOperation(t[0], entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);

    mod = newModification(ADD, "Y");
    replayOperation(t[2], entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);

    mod = newModification(DELETE, "Y");
    replayOperationSuppressMod(t[1], entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);
  }

  @Test
  public void replay_addThenDeleteNoValueThenOlderAdd() throws Exception
  {
    CSN[] t = newCSNs(3);

    replay_addDeleteNoValue(t[0], t[2]);

    mod = newModification(ADD, "Y");
    replayOperationSuppressMod(t[1], entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);
  }

  @Test
  public void replay_addThenDeleteWithValueThenOlderAdd() throws Exception
  {
    CSN[] t = newCSNs(3);

    mod = newModification(ADD, "X");
    replayOperation(t[0], entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);

    mod = newModification(DELETE, "X");
    replayOperation(t[2], entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);

    mod = newModification(ADD, "X");
    replayOperationSuppressMod(t[1], entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);
  }

  @Test
  public void replay_addThenAdd() throws Exception
  {
    CSN[] t = newCSNs(2);

    mod = newModification(ADD, "X");
    replayOperation(t[0], entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);

    mod = newModification(ADD, "X");
    replayOperationSuppressMod(t[1], entry, mod, E.CONFLICT);
  }

  @Test
  public void replay_addThenDeleteThenAdd() throws Exception
  {
    CSN[] t = newCSNs(3);

    mod = newModification(ADD, "X");
    replayOperation(t[0], entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);

    mod = newModification(DELETE, "X");
    replayOperation(t[1], entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);

    mod = newModification(ADD, "X");
    replayOperation(t[1], entry, mod, E.CONFLICT);
  }

  @Test
  public void replay_deleteNoPreviousHistory() throws Exception
  {
    mod = newModification(DELETE, "Y");
    replayOperationSuppressMod(csn, entry, mod, E.CONFLICT);
  }

  @Test
  public void replay_addThenDelete() throws Exception
  {
    CSN[] t = newCSNs(2);

    mod = newModification(ADD, "X");
    replayOperation(t[0], entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);

    mod = newModification(DELETE, "X");
    replayOperation(t[1], entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);
  }

  @Test
  public void replay_addThenDeleteThenOlderDelete() throws Exception
  {
    CSN[] t = newCSNs(3);

    replay_addDeleteNoValue(t[0], t[2]);

    mod = newModification(DELETE, "X");
    replayOperationSuppressMod(t[1], entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);
  }

  @Test
  public void replay_addDeleteNoValueSameTimeNotConflict() throws Exception
  {
    replay_addDeleteNoValue(csn, csn);
  }

  @Test
  public void replay_addThenDeleteNoValue() throws Exception
  {
    CSN[] t = newCSNs(2);
    replay_addDeleteNoValue(t[0], t[1]);
  }

  private void replay_addDeleteNoValue(CSN tAdd, CSN tDel) throws Exception
  {
    mod = newModification(ADD, "X");
    replayOperation(tAdd, entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);

    mod = newModification(DELETE);
    replayOperation(tDel, entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);
  }

  @Test
  public void replay_replace() throws Exception
  {
    mod = newModification(REPLACE, "X");
    replayOperation(csn, entry, mod, E.NO_CONFLICT);
  }

  @Test
  public void replay_addThenOlderReplace() throws Exception
  {
    CSN[] t = newCSNs(2);

    mod = newModification(ADD, "X");
    replayOperation(t[1], entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);

    mod = newModification(REPLACE, "Y");
    replayOperation(t[0], entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);
  }

  @Test
  public void replay_addThenDeleteThenOlderReplace() throws Exception
  {
    CSN[] t = newCSNs(3);

    replay_addDeleteNoValue(t[0], t[2]);

    mod = newModification(REPLACE, "Y");
    replayOperationSuppressMod(t[1], entry, mod, E.CONFLICT_BUT_SHOULD_NOT_BE);
  }

  private CSN[] newCSNs(int nb)
  {
    CSN[] results = new CSN[nb];
    for (int i = 0; i < nb; i++)
    {
      results[i] = csnGen.newCSN();
    }
    return results;
  }

  private Modification newModification(ModificationType modType, String attrValue)
  {
    return new Modification(modType, Attributes.create("description", attrValue));
  }

  private Modification newModification(ModificationType modType)
  {
    return new Modification(modType, Attributes.empty("display"));
  }

  private void replayOperationSuppressMod(CSN csn, Entry entry, Modification mod, E conflictStatus)
      throws Exception
  {
    Iterator<Modification> itMod = mock(Iterator.class);
    replayOperation(itMod, csn, entry, mod, conflictStatus);
    verifyModNotReplayed(itMod);
  }

  private void replayOperation(CSN csn, Entry entry, Modification mod, E conflictStatus) throws Exception
  {
    replayOperation(null, csn, entry, mod, conflictStatus);
  }

  private void replayOperation(Iterator<Modification> modsIterator, CSN csn, Entry entry, Modification mod,
      E conflictStatus) throws Exception
  {
    boolean result = attrHist.replayOperation(modsIterator, csn, entry, mod);
    assertEquals(result, conflictStatus.getExpectedResult(),
        "Expected " + (conflictStatus == E.CONFLICT ? "a" : "no") + " conflict when applying " + mod + " to " + entry);
    if (entry != null && conflictStatus != E.CONFLICT)
    {
      entry.applyModification(mod);
      assertAttributeValues(entry, mod);
    }
  }

  private void assertAttributeValues(Entry entry, Modification mod)
  {
    List<ByteString> actualValues = getValues(entry, mod);
    List<ByteString> expectedValues = getValues(mod.getAttribute());
    switch (mod.getModificationType().asEnum())
    {
    case ADD:
      assertThat(actualValues).containsAll(expectedValues);
      return;

    case REPLACE:
      assertThat(actualValues).isEqualTo(expectedValues);
      return;

    case DELETE:
      if (expectedValues.isEmpty())
      {
        assertThat(actualValues).isEmpty();
      }
      else
      {
        assertThat(actualValues).doesNotContainAnyElementsOf(expectedValues);
      }
      return;

    case INCREMENT:
      return;
    }
  }

  private List<ByteString> getValues(Entry entry, Modification mod)
  {
    List<Attribute> attributes = entry.getAttribute(mod.getAttribute().getAttributeType());
    if (attributes != null)
    {
      assertThat(attributes).hasSize(1);
      return getValues(attributes.get(0));
    }
    return Collections.emptyList();
  }

  private List<ByteString> getValues(Attribute attribute)
  {
    List<ByteString> results = new ArrayList<>();
    for (ByteString value : attribute)
    {
      results.add(value);
    }
    return results;
  }

  private void verifyModNotReplayed(Iterator<Modification> it)
  {
    verify(it, times(1)).remove();
    verify(it, only()).remove();
  }
}
