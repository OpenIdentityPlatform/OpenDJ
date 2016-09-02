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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import java.util.Iterator;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class AttrHistoricalSingleTest extends ReplicationTestCase
{
  private static final String ATTRIBUTE_NAME = "displayName";
  private static final boolean CONFLICT = true;
  private static final boolean SUCCESS = false;

  private CSNGenerator csnGen = new CSNGenerator(1025, System.currentTimeMillis());
  private AttrHistoricalSingle attrHist;
  private CSN csn;
  private Entry entry;
  /** Avoids declaring the variable in the tests */
  private Modification mod;

  @BeforeMethod
  public void localSetUp() throws Exception
  {
    AttributeType attrType = Schema.getDefaultSchema().getAttributeType(ATTRIBUTE_NAME);
    attrHist = new AttrHistoricalSingle(attrType);
    csn = csnGen.newCSN();
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

  @Test
  public void replay_addDeleteSameTime() throws Exception
  {
    mod = newModification(ADD, "X");
    replayOperation(csn, entry, mod, SUCCESS);
    assertAttributeValue(entry, "X");

    mod = newModification(DELETE, "X");
    replayOperation(csn, entry, mod, SUCCESS);
    assertNoAttributeValue(entry);
  }

  @Test
  public void replay_addThenDeleteThenOlderAdd() throws Exception
  {
    CSN[] t = newCSNs(3);

    mod = newModification(ADD, "X");
    replayOperation(t[0], entry, mod, SUCCESS);
    assertAttributeValue(entry, "X");

    mod = newModification(DELETE);
    replayOperation(t[2], entry, mod, SUCCESS);
    assertNoAttributeValue(entry);

    mod = newModification(ADD, "Z");
    replayOperationSuppressMod(t[1], entry, mod, CONFLICT);
    assertNoAttributeValue(entry);
  }

  @Test
  public void replay_addThenDeleteThenAddThenOlderAdd() throws Exception
  {
    CSN[] t = newCSNs(4);

    mod = newModification(ADD, "X");
    replayOperation(t[0], entry, mod, SUCCESS);
    assertAttributeValue(entry, "X");

    mod = newModification(DELETE, "X");
    replayOperation(t[1], entry, mod, SUCCESS);
    assertNoAttributeValue(entry);

    mod = newModification(ADD, "X");
    replayOperation(t[3], entry, mod, SUCCESS);
    assertAttributeValue(entry, "X");

    mod = newModification(ADD, "Y");
    replayOperation(t[2], entry, mod, CONFLICT);
    assertAttributeValue(entry, "X");
  }

  @Test
  public void replay_addThenDeleteThenDelete() throws Exception
  {
    CSN[] t = newCSNs(3);

    mod = newModification(ADD, "X");
    replayOperation(t[0], entry, mod, SUCCESS);
    assertAttributeValue(entry, "X");

    mod = newModification(DELETE, "X");
    replayOperation(t[1], entry, mod, SUCCESS);
    assertNoAttributeValue(entry);

    mod = newModification(DELETE, "X");
    replayOperationSuppressMod(t[2], entry, mod, CONFLICT);
    assertNoAttributeValue(entry);
  }

  @Test
  public void replay_addThenOlderDelete() throws Exception
  {
    CSN[] t = newCSNs(2);

    mod = newModification(ADD, "X");
    replayOperation(t[1], entry, mod, SUCCESS);
    assertAttributeValue(entry, "X");

    mod = newModification(DELETE, "X");
    replayOperationSuppressMod(t[0], entry, mod, CONFLICT);
    assertAttributeValue(entry, "X");
  }

  @Test
  public void replay_deleteMissingAttribute() throws Exception
  {
    mod = newModification(DELETE, "X");
    replayOperationSuppressMod(csn, entry, mod, CONFLICT);
    assertNoAttributeValue(entry);
  }

  @Test
  public void replay_deleteMissingAttributeValue() throws Exception
  {
    CSN[] t = newCSNs(2);

    mod = newModification(ADD, "X");
    replayOperation(t[0], entry, mod, SUCCESS);
    assertAttributeValue(entry, "X");

    mod = newModification(DELETE, "Y");
    replayOperationSuppressMod(t[1], entry, mod, CONFLICT);
    assertAttributeValue(entry, "X");
  }

  /**
   * TODO JNR this looks dubious, is it ever possible in the server?
   * <p>
   * Could multi-threading make this scenario possible?
   * <p>
   * Or is it due to {@link AttrHistoricalSingle#assign(HistAttrModificationKey, ByteString, CSN)} ?
   */
  @Test
  public void replay_deleteValueThatDoesNotExistOnEntry() throws Exception
  {
    CSN[] t = newCSNs(2);

    mod = newModification(ADD, "X");
    entry.applyModification(mod);
    assertAttributeValue(entry, "X");

    mod = newModification(DELETE, "Y");
    replayOperationSuppressMod(t[1], entry, mod, CONFLICT);
    assertAttributeValue(entry, "X");
  }

  /**
   * TODO JNR this looks dubious, is it ever possible in the server?
   * <p>
   * Could multi-threading make this scenario possible?
   * <p>
   * Or is it due to {@link AttrHistoricalSingle#assign(HistoricalAttributeValue)} ?
   */
  @Test
  public void replay_deleteDubious() throws Exception
  {
    attrHist.assign(new HistoricalAttributeValue(ATTRIBUTE_NAME + ":" + csn + ":add:X"));
    mod = newModification(ADD, "X");
    entry.applyModification(mod);
    assertAttributeValue(entry, "X");

    mod = newModification(DELETE, "Y");
    replayOperationSuppressMod(csn, entry, mod, CONFLICT);
    assertAttributeValue(entry, "X");
  }

  @Test
  public void replay_replaceWithValue() throws Exception
  {
    mod = newModification(REPLACE, "X");
    replayOperation(csn, entry, mod, SUCCESS);
    assertAttributeValue(entry, "X");
  }

  @Test
  public void replay_replaceNoValue() throws Exception
  {
    mod = newModification(REPLACE);
    replayOperation(csn, entry, mod, SUCCESS);
    assertNoAttributeValue(entry);
  }

  @Test
  public void replay_addThenDeleteThenOlderReplace() throws Exception
  {
    CSN[] t = newCSNs(3);

    mod = newModification(ADD, "X");
    replayOperation(t[0], entry, mod, SUCCESS);
    assertAttributeValue(entry, "X");

    mod = newModification(DELETE);
    replayOperation(t[2], entry, mod, SUCCESS);
    assertNoAttributeValue(entry);

    mod = newModification(REPLACE);
    replayOperationSuppressMod(t[1], entry, mod, CONFLICT);
    assertNoAttributeValue(entry);
  }

  @Test
  public void replay_increment() throws Exception
  {
    mod = newModification(INCREMENT, "X");
    replayOperation(csn, null, mod, SUCCESS);
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

  private void replayOperation(CSN csn, Entry entry, Modification mod, boolean shouldConflict) throws Exception
  {
    Iterator<Modification> itMod = mock(Iterator.class);
    replayOperation(itMod, csn, entry, mod, shouldConflict);
    verifyZeroInteractions(itMod);
  }

  private void replayOperationSuppressMod(CSN csn, Entry entry, Modification mod, boolean shouldConflict)
      throws Exception
  {
    Iterator<Modification> itMod = mock(Iterator.class);
    replayOperation(itMod, csn, entry, mod, shouldConflict);
    verifyModSuppressed(itMod);
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
      assertAttributeValue(entry, mod);
      conformsToSchema(entry);
    }
  }

  private void assertAttributeValue(Entry entry, Modification mod)
  {
    ByteString actualValue = getActualValue(entry, mod);
    switch (mod.getModificationType().asEnum())
    {
    case ADD:
    {
      Attribute attribute = mod.getAttribute();
      assertThat(attribute).hasSize(1);
      ByteString expectedValue = attribute.iterator().next();
      assertEquals(actualValue, expectedValue);
      return;
    }

    case REPLACE:
    {
      Attribute attribute = mod.getAttribute();
      if (!attribute.isEmpty())
      {
        ByteString expectedValue = attribute.iterator().next();
        assertEquals(actualValue, expectedValue);
      }
      else
      {
        assertNull(actualValue);
      }
      return;
    }

    case DELETE:
      assertNull(actualValue);
      return;
    }
  }

  private ByteString getActualValue(Entry entry, Modification mod)
  {
    return getActualValue(entry.getAllAttributes(mod.getAttribute().getAttributeDescription()));
  }

  private ByteString getActualValue(Iterable<Attribute> attributes)
  {
    Iterator<Attribute> it = attributes.iterator();
    if (it.hasNext())
    {
      assertThat(attributes).hasSize(1);
      Attribute attribute = it.next();
      assertThat(attribute).hasSize(1);
      return attribute.iterator().next();
    }
    return null;
  }

  private void conformsToSchema(Entry entry)
  {
    LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
    final boolean isValid = entry.conformsToSchema(null, false, false, false, invalidReason);
    assertThat(isValid).as(invalidReason.toString()).isTrue();
  }

  private void assertNoAttributeValue(Entry entry)
  {
    assertAttributeValue(entry, (String) null);
  }

  private void assertAttributeValue(Entry entry, String expectedValue)
  {
    ByteString actualValue = getActualValue(entry.getAllAttributes(ATTRIBUTE_NAME));
    assertEquals(actualValue, expectedValue != null ? ByteString.valueOfUtf8(expectedValue) : null);
  }

  private void verifyModSuppressed(Iterator<Modification> it)
  {
    verify(it, times(1)).remove();
    verify(it, only()).remove();
  }
}
