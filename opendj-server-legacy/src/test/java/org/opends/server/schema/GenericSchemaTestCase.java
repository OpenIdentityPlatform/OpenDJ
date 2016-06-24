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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.schema;

import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.opends.server.util.ServerConstants.*;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.NameForm;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Schema;
import org.opends.server.util.LDIFReader;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** This class defines a set of generic tests that may be used to examine the server schema. */
public class GenericSchemaTestCase
       extends SchemaTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Override
  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
  }

  /** Tests to ensure that all attribute syntaxes defined in the schema have valid OIDs. */
  @Test
  public void testEnsureValidSyntaxOIDs()
  {
    TreeSet<String> invalidOIDs = new TreeSet<>();

    Schema schema = DirectoryServer.getSchema();
    for (Syntax as : schema.getSyntaxes())
    {
      if (! isNumericOID(as.getOID()))
      {
        invalidOIDs.add(as.getName());
      }
    }

    throwIfInvalidOidsExist("attribute syntaxes", invalidOIDs);
  }

  /** Tests to ensure that all matching rules defined in the schema have valid OIDs. */
  @Test
  public void testEnsureValidMatchingRuleOIDs()
  {
    TreeSet<String> invalidOIDs = new TreeSet<>();

    Schema schema = DirectoryServer.getSchema();
    for (MatchingRule mr : schema.getMatchingRules())
    {
      if (! isNumericOID(mr.getOID()))
      {
        invalidOIDs.add(mr.getNameOrOID());
      }
    }

    throwIfInvalidOidsExist("matching rules", invalidOIDs);
  }

  /**
   * Tests to ensure that all attribute types defined in the schema have valid OIDs.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testEnsureValidAttributeTypeOIDs()
         throws Exception
  {
    TreeSet<String> invalidOIDs = new TreeSet<>();

    for (File f : getSchemaFiles())
    {
      for (Attribute a : getAttributesFromSchemaFile(f, getAttributeTypesAttributeType()))
      {
        for (ByteString v : a)
        {
          AttributeType at = DirectoryServer.getSchema().parseAttributeType(v.toString());
          if (! isNumericOID(at.getOID()))
          {
            invalidOIDs.add(at.getNameOrOID());
          }
        }
      }
    }

    throwIfInvalidOidsExist("attribute types", invalidOIDs);
  }

  /**
   * Tests to ensure that all object classes defined in the schema have valid OIDs.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testEnsureValidObjectClassOIDs()
         throws Exception
  {
    TreeSet<String> invalidOIDs = new TreeSet<>();

    for (File f : getSchemaFiles())
    {
      for (Attribute a : getAttributesFromSchemaFile(f, getObjectClassesAttributeType()))
      {
        for (ByteString v : a)
        {
          ObjectClass oc = DirectoryServer.getSchema().parseObjectClass(v.toString());
          if (! isNumericOID(oc.getOID()))
          {
            invalidOIDs.add(oc.getNameOrOID());
          }
        }
      }
    }

    throwIfInvalidOidsExist("object classes", invalidOIDs);
  }

  /**
   * Tests to ensure that all name forms defined in the schema have valid OIDs.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testEnsureValidNameFormOIDs()
         throws Exception
  {
    TreeSet<String> invalidOIDs = new TreeSet<>();

    for (File f : getSchemaFiles())
    {
      for (Attribute a : getAttributesFromSchemaFile(f, getNameFormsAttributeType()))
      {
        for (ByteString v : a)
        {
          NameForm nf = DirectoryServer.getSchema().parseNameForm(v.toString());
          if (! isNumericOID(nf.getOID()))
          {
            invalidOIDs.add(nf.getNameOrOID());
          }
        }
      }
    }

    throwIfInvalidOidsExist("name forms", invalidOIDs);
  }

  private File[] getSchemaFiles()
  {
    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    File schemaDir = new File(new File(buildRoot, "resource"), "schema");
    return schemaDir.listFiles();
  }

  private List<Attribute> getAttributesFromSchemaFile(File f, AttributeType attributeType) throws Exception
  {
    Entry e = readSchemaEntry(f);
    return e != null
        ? e.getAttribute(attributeType)
        // An empty schema file. This is OK.
        : Collections.<Attribute> emptyList();
  }

  private Entry readSchemaEntry(File f) throws Exception
  {
    if (!f.getName().toLowerCase().endsWith(".ldif"))
    {
      // This could be some other kind of file, like ".svn".
      return null;
    }

    LDIFImportConfig importConfig = new LDIFImportConfig(f.getAbsolutePath());
    try (LDIFReader reader = new LDIFReader(importConfig))
    {
      return reader.readEntry();
    }
  }

  private void throwIfInvalidOidsExist(String elementType, Set<String> invalidOIDs) throws AssertionError
  {
    if (! invalidOIDs.isEmpty())
    {
      StringBuilder message = new StringBuilder()
          .append("All ").append(elementType).append(" defined in OpenDJ must have valid OIDs assigned." + EOL)
          .append(elementType).append(" without valid OIDs:" + EOL);
      for (String oid : invalidOIDs)
      {
        message.append("- ").append(oid).append(EOL);
      }
      throw new AssertionError(message.toString());
    }
  }

  /**
   * Indicates whether the string represents a valid numeric OID.
   *
   * @param  oid  The string for which to make the determination.
   *
   * @return  {@code true} if the provided string represents a valid numeric
   *          OID, or {@code false} if not.
   */
  private boolean isNumericOID(String oid)
  {
    if (oid == null
        || oid.isEmpty()
        || !startsAndEndsWithDigit(oid)
        // It must contain at least one period.
        || !oid.contains(".")
        // It must not contain any double periods.
        || oid.contains(".."))
    {
      return false;
    }

    // It must not contain any characters other than digits and periods.
    StringTokenizer tokenizer = new StringTokenizer(oid, ".");
    while (tokenizer.hasMoreTokens())
    {
      String token = tokenizer.nextToken();
      for (char c : token.toCharArray())
      {
        if (! Character.isDigit(c))
        {
          return false;
        }
      }
    }

    // If we've gotten here, then it should be a valid numeric OID.
    return true;
  }

  private boolean startsAndEndsWithDigit(String oid)
  {
    return Character.isDigit(oid.charAt(0)) && Character.isDigit(oid.charAt(oid.length() - 1));
  }
}
