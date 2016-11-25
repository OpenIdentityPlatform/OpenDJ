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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
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

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.types.Attribute;
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
  private static final String ATTRIBUTE_NAME = "description";
  private static final boolean CONFLICT = true;
  private static final boolean SUCCESS = false;

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
    entry = TestCaseUtils.makeEntry(
        "dn: uid=test.user",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "userPassword: password");
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
    ByteString att1 = ByteString.valueOfUtf8("string");
    ByteString att2 = ByteString.valueOfUtf8("value");
    ByteString att3 = ByteString.valueOfUtf8("again");

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
  public void attrInfo(ByteString attrValue, CSN deleteTime, CSN updateTime) throws Exception
  {
    AttributeType attrType = CoreSchema.getDescriptionAttributeType();
    // Create an empty AttrInfo
    AttrHistoricalMultiple attrInfo1 = new AttrHistoricalMultiple();

    // Check
    attrInfo1.add(attrValue, attrType, updateTime);
    assertThat(attrInfo1.getValuesHistorical())
        .containsOnly(new AttrValueHistorical(attrValue, attrType, updateTime, null));

    // Check constructor with parameter
    AttrValueHistorical valueInfo2 = new AttrValueHistorical(attrValue, attrType, updateTime, deleteTime);
    AttrHistoricalMultiple attrInfo2 = new AttrHistoricalMultiple(
        deleteTime, updateTime, Collections.singleton(valueInfo2));

    // Check equality
    //assertTrue(attrInfo1.getDeleteTime().compareTo(attrInfo2.getDeleteTime())==0);

    //  Check constructor with time parameter and not Value
    AttrHistoricalMultiple attrInfo3 = new AttrHistoricalMultiple(deleteTime, updateTime, null);
    attrInfo3.add(attrValue, attrType, updateTime);
    assertThat(attrInfo3.getValuesHistorical())
        .containsOnly(new AttrValueHistorical(attrValue, attrType, updateTime, null));

    // Check duplicate
    AttrHistoricalMultiple attrInfo4 = duplicate(attrInfo3);
    assertEquals(attrInfo4.getDeleteTime().compareTo(attrInfo3.getDeleteTime()), 0);
    assertThat(attrInfo4.getValuesHistorical()).isEqualTo(attrInfo3.getValuesHistorical());

    // Check
    attrInfo4.delete(attrValue, attrType, updateTime);
    assertEquals(attrInfo4.getValuesHistorical().size(), 1);

    // Check
    AttributeType type = DirectoryServer.getInstance().getServerContext().getSchema().getAttributeType(ATTRIBUTE_NAME);
    attrInfo3.delete(Attributes.create(type, attrValue), updateTime);
    assertEquals(attrInfo3.getValuesHistorical().size(), 1);

    // Check
    attrInfo2.delete(updateTime);
    assertThat(attrInfo2.getValuesHistorical()).isEmpty();
  }

  private AttrHistoricalMultiple duplicate(AttrHistoricalMultiple attrMul)
  {
    return new AttrHistoricalMultiple(
        attrMul.getDeleteTime(), attrMul.getLastUpdateTime(), attrMul.getValuesHistorical());
  }

  @Test
  public void replay_addDeleteSameTime() throws Exception
  {
    mod = newModification(ADD, "X");
    replayOperation(csn, entry, mod, SUCCESS);
    assertAttributeValues(entry, "X");

    mod = newModification(DELETE, "X");
    replayOperation(csn, entry, mod, SUCCESS);
    assertNoAttributeValue(entry);
  }

  @Test
  public void replay_addThenAddThenOlderDelete() throws Exception
  {
    CSN[] t = newCSNs(3);

    mod = newModification(ADD, "X");
    replayOperation(t[0], entry, mod, SUCCESS);
    assertAttributeValues(entry, "X");

    mod = newModification(ADD, "Y");
    replayOperation(t[2], entry, mod, SUCCESS);
    assertAttributeValues(entry, "X", "Y");

    mod = newModification(DELETE, "Y");
    replayOperationSuppressMod(t[1], entry, mod, CONFLICT);
    assertAttributeValues(entry, "X", "Y");
  }

  @Test
  public void replay_addThenDeleteNoValueThenOlderAdd() throws Exception
  {
    CSN[] t = newCSNs(3);

    replay_addDeleteNoValue(t[0], t[2]);

    mod = newModification(ADD, "Y");
    replayOperationSuppressMod(t[1], entry, mod, CONFLICT);
    assertNoAttributeValue(entry);
  }

  @Test
  public void replay_addThenDeleteWithValueThenOlderAdd() throws Exception
  {
    CSN[] t = newCSNs(3);

    mod = newModification(ADD, "X");
    replayOperation(t[0], entry, mod, SUCCESS);
    assertAttributeValues(entry, "X");

    mod = newModification(DELETE, "X");
    replayOperation(t[2], entry, mod, SUCCESS);
    assertNoAttributeValue(entry);

    mod = newModification(ADD, "X");
    replayOperationSuppressMod(t[1], entry, mod, CONFLICT);
    assertNoAttributeValue(entry);
  }

  @Test
  public void replay_addThenAdd() throws Exception
  {
    CSN[] t = newCSNs(2);

    mod = newModification(ADD, "X");
    replayOperation(t[0], entry, mod, SUCCESS);
    assertAttributeValues(entry, "X");

    mod = newModification(ADD, "X");
    replayOperationSuppressMod(t[1], entry, mod, CONFLICT);
    assertAttributeValues(entry, "X");
  }

  @Test
  public void replay_addThenDeleteThenAdd() throws Exception
  {
    CSN[] t = newCSNs(3);

    mod = newModification(ADD, "X");
    replayOperation(t[0], entry, mod, SUCCESS);
    assertAttributeValues(entry, "X");

    mod = newModification(DELETE, "X");
    replayOperation(t[1], entry, mod, SUCCESS);
    assertNoAttributeValue(entry);

    mod = newModification(ADD, "X");
    replayOperation(t[2], entry, mod, SUCCESS);
    assertAttributeValues(entry, "X");
  }

  /** Use case: user changes the case of a family name for example. */
  @Test
  public void replay_addThenDeleteThenAdd_differentCaseWithCaseIgnoreAttributeType() throws Exception
  {
    CSN[] t = newCSNs(3);

    mod = newModification(ADD, "x");
    replayOperation(t[0], entry, mod, SUCCESS);
    assertAttributeValues(entry, "x");

    mod = newModification(DELETE, "X");
    replayOperation(t[1], entry, mod, SUCCESS);
    assertNoAttributeValue(entry);

    mod = newModification(ADD, "X");
    replayOperation(t[2], entry, mod, SUCCESS);
    assertAttributeValues(entry, "X");
  }

  @Test
  public void replay_deleteNoPreviousHistory() throws Exception
  {
    mod = newModification(DELETE, "Y");
    replayOperationSuppressMod(csn, entry, mod, CONFLICT);
    assertNoAttributeValue(entry);
  }

  @Test
  public void replay_addThenDelete() throws Exception
  {
    CSN[] t = newCSNs(2);

    mod = newModification(ADD, "X");
    replayOperation(t[0], entry, mod, SUCCESS);
    assertAttributeValues(entry, "X");

    mod = newModification(DELETE, "X");
    replayOperation(t[1], entry, mod, SUCCESS);
    assertNoAttributeValue(entry);
  }

  @Test
  public void replay_addThenDeleteThenOlderDelete() throws Exception
  {
    CSN[] t = newCSNs(3);

    replay_addDeleteNoValue(t[0], t[2]);

    mod = newModification(DELETE, "X");
    replayOperationSuppressMod(t[1], entry, mod, CONFLICT);
    assertNoAttributeValue(entry);
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
    replayOperation(tAdd, entry, mod, SUCCESS);
    assertAttributeValues(entry, "X");

    mod = newModification(DELETE);
    replayOperation(tDel, entry, mod, SUCCESS);
    assertNoAttributeValue(entry);
  }

  @Test
  public void replay_replace() throws Exception
  {
    mod = newModification(REPLACE, "X");
    replayOperation(csn, entry, mod, SUCCESS);
    assertAttributeValues(entry, "X");
  }

  @Test
  public void replay_addThenOlderReplace() throws Exception
  {
    CSN[] t = newCSNs(2);

    mod = newModification(ADD, "X");
    replayOperation(t[1], entry, mod, SUCCESS);
    assertAttributeValues(entry, "X");

    mod = newModification(REPLACE, "Y");
    replayOperation(t[0], entry, mod, SUCCESS);
    assertAttributeValues(entry, "X", "Y");
  }

  @Test
  public void replay_addThenDeleteThenOlderReplace() throws Exception
  {
    CSN[] t = newCSNs(3);

    replay_addDeleteNoValue(t[0], t[2]);

    mod = newModification(REPLACE, "Y");
    replayOperationSuppressMod(t[1], entry, mod, CONFLICT);
    assertNoAttributeValue(entry);
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
    return new Modification(modType, Attributes.create(ATTRIBUTE_NAME, attrValue));
  }

  private Modification newModification(ModificationType modType)
  {
    return new Modification(modType, Attributes.empty(ATTRIBUTE_NAME));
  }

  private void replayOperationSuppressMod(CSN csn, Entry entry, Modification mod, boolean shouldConflict)
      throws Exception
  {
    Iterator<Modification> itMod = mock(Iterator.class);
    replayOperation(itMod, csn, entry, mod, shouldConflict);
    verifyModSuppressed(itMod);
  }

  private void replayOperation(CSN csn, Entry entry, Modification mod, boolean shouldConflict) throws Exception
  {
    Iterator<Modification> itMod = mock(Iterator.class);
    replayOperation(itMod, csn, entry, mod, shouldConflict);
    verifyZeroInteractions(itMod);
  }

  private void replayOperation(Iterator<Modification> modsIterator, CSN csn, Entry entry, Modification mod,
      boolean shouldConflict) throws Exception
  {
    boolean result = attrHist.replayOperation(modsIterator, csn, entry, mod);
    assertEquals(result, shouldConflict,
        "Expected " + (shouldConflict ? "a" : "no") + " conflict when applying " + mod + " to " + entry);
    if (entry != null && !shouldConflict)
    {
      entry.applyModification(mod);
      assertAttributeValues(entry, mod);
      conformsToSchema(entry);
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
    return getValues(entry.getAllAttributes(mod.getAttribute().getAttributeDescription()));
  }

  private List<ByteString> getValues(Iterable<Attribute> attributes)
  {
    Iterator<Attribute> it = attributes.iterator();
    if (it.hasNext())
    {
      assertThat(attributes).hasSize(1);
      return getValues(it.next());
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

  private void conformsToSchema(Entry entry)
  {
    LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
    final boolean isValid = entry.conformsToSchema(null, false, false, false, invalidReason);
    assertThat(isValid).as(invalidReason.toString()).isTrue();
  }

  private void assertNoAttributeValue(Entry entry)
  {
    assertAttributeValues(entry);
  }

  private void assertAttributeValues(Entry entry, String... expectedValues)
  {
    List<ByteString> actualValues = getValues(entry.getAllAttributes(ATTRIBUTE_NAME));
    assertThat(actualValues).containsOnly(toByteStrings(expectedValues));
  }

  private ByteString[] toByteStrings(String... strings)
  {
    ByteString[] results = new ByteString[strings.length];
    for (int i = 0; i < results.length; i++)
    {
      results[i] = ByteString.valueOfUtf8(strings[i]);
    }
    return results;
  }

  private void verifyModSuppressed(Iterator<Modification> it)
  {
    verify(it, times(1)).remove();
    verify(it, only()).remove();
  }
}
