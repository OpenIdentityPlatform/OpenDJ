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
package org.opends.server.api;

import static org.opends.server.schema.SchemaConstants.*;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.Attribute;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Tests for {@link MonitorData} class. */
@SuppressWarnings("javadoc")
public class MonitorDataTestCase extends APITestCase
{
  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startFakeServer();
  }

  @AfterClass
  public void tearDown() throws DirectoryException
  {
    TestCaseUtils.shutdownFakeServer();
  }

  @Test
  public void verifyInferredAttributeSyntaxes() throws Exception
  {
    final MonitorData attrs = new MonitorData();
    attrs.add("string", "this is a string");
    attrs.add("bytestring", ByteString.valueOfUtf8("this is a string"));
    attrs.add("int", 1);
    attrs.add("long", 1L);
    attrs.add("float", 1f);
    attrs.add("double", 1d);
    attrs.add("boolean", true);
    attrs.add("dn", DN.valueOf("cn=test"));
    attrs.add("uuid", DN.valueOf("cn=test").toUUID());
    attrs.add("date", new Date());
    attrs.add("calender", Calendar.getInstance());
    attrs.add("other", 'a');

    assertSyntaxes(attrs);
    assertEquals(attrs.size(), 12);
  }

  private void assertSyntaxes(final MonitorData attrs)
  {
    for (Attribute attr : attrs)
    {
      final AttributeType attrType = attr.getAttributeDescription().getAttributeType();
      final Syntax syntax = attrType.getSyntax();

      switch (attrType.getNameOrOID())
      {
      case "int":
      case "long":
        assertSyntax(attr, syntax, SYNTAX_INTEGER_OID);
        continue;

      case "string":
      case "bytestring":
      case "float":
      case "double":
      case "other":
        assertSyntax(attr, syntax, SYNTAX_DIRECTORY_STRING_OID);
        continue;

      case "boolean":
        assertSyntax(attr, syntax, SYNTAX_BOOLEAN_OID);
        continue;

      case "dn":
        assertSyntax(attr, syntax, SYNTAX_DN_OID);
        continue;

      case "date":
      case "calender":
        assertSyntax(attr, syntax, SYNTAX_GENERALIZED_TIME_OID);
        continue;

      case "uuid":
        assertSyntax(attr, syntax, SYNTAX_UUID_OID);
        continue;

      default:
        fail("Untested type: \"" + attrType.getNameOrOID() + "\"");
      }
    }
  }

  private void assertSyntax(Attribute attr, final Syntax syntax, String syntaxOid)
  {
    assertEquals(syntax.getOID(), syntaxOid, "For attribute: " + attr);

    final LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
    final ByteString firstAttrValue = attr.iterator().next();
    final boolean result = syntax.valueIsAcceptable(firstAttrValue, invalidReason);
    assertTrue(result, "For attribute: " + attr + " expected value to be acceptable, but got:" + invalidReason);
  }

  @Test
  public void addBean() throws Exception
  {
    class MyBean
    {
      // converted to attributes
      public int getint() { return 1; }
      public long getlong() { return 1; }
      public String getstring() { return "this is a test"; }
      public boolean isboolean() { return true; }

      // not converted to attributes
      public DN getAnother() { return DN.valueOf("cn=test"); }
      public DN isAnother() { return getAnother(); }
    }

    final MonitorData attrs = new MonitorData();
    attrs.addBean(new MyBean(), "");
    assertSyntaxes(attrs);

    List<String> names = new ArrayList<>();
    for (Attribute attr : attrs)
    {
      names.add(attr.getAttributeDescription().getAttributeType().getNameOrOID());
    }
    Assertions.assertThat(names).containsOnly("int", "long", "string", "boolean");
    assertEquals(attrs.size(), names.size());
  }

  @Test
  public void addCollectionOfValues() throws Exception
  {
    final MonitorData attrs = new MonitorData();
    attrs.add("string", Arrays.asList("this", "is", "a", "test"));

    final Iterator<Attribute> it = attrs.iterator();
    assertTrue(it.hasNext());
    final Attribute attr = it.next();
    assertEquals(attr.getAttributeDescription().getAttributeType().getNameOrOID(), "string");
    Assertions.assertThat(attr).containsExactly(bs("this"), bs("is"), bs("a"), bs("test"));
    assertFalse(it.hasNext());
  }

  private ByteString bs(String s)
  {
    return ByteString.valueOfUtf8(s);
  }

  @Test
  public void addIfNotNull() throws Exception
  {
    final MonitorData attrs = new MonitorData();

    attrs.addIfNotNull("string", null);
    assertEquals(attrs.size(), 0);
    final Iterator<Attribute> it1 = attrs.iterator();
    assertFalse(it1.hasNext());

    attrs.addIfNotNull("string", "this is a test");
    assertEquals(attrs.size(), 1);
    final Iterator<Attribute> it2 = attrs.iterator();
    assertTrue(it2.hasNext());
    final Attribute attr = it2.next();
    assertEquals(attr.getAttributeDescription().getAttributeType().getNameOrOID(), "string");
    Assertions.assertThat(attr).containsExactly(bs("this is a test"));
    assertFalse(it2.hasNext());
  }
}
