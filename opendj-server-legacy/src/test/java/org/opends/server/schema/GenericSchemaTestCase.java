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
import java.util.List;
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
  public void setUp()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests to ensure that all attribute syntaxes defined in the schema have
   * valid OIDs.
   */
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

    if (! invalidOIDs.isEmpty())
    {
      StringBuilder message = new StringBuilder();
      message.append("All attribute syntaxes defined in OpenDS must have valid OIDs assigned.").append(EOL);
      message.append("Attribute syntaxes without valid OIDs:").append(EOL);
      for (String s : invalidOIDs)
      {
        message.append("- ").append(s).append(EOL);
      }

      throw new AssertionError(message.toString());
    }
  }



  /**
   * Tests to ensure that all matching rules defined in the schema have valid
   * OIDs.
   */
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

    if (! invalidOIDs.isEmpty())
    {
      StringBuilder message = new StringBuilder();
      message.append("All matching rules defined in OpenDS must have valid ").append("OIDs assigned.");
      message.append(EOL);
      message.append("Matching rules without valid OIDs:");
      message.append(EOL);
      for (String s : invalidOIDs)
      {
        message.append("- ").append(s);
        message.append(EOL);
      }

      throw new AssertionError(message.toString());
    }
  }



  /**
   * Tests to ensure that all attribute types defined in the schema have valid
   * OIDs.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testEnsureValidAttributeTypeOIDs()
         throws Exception
  {
    TreeSet<String> invalidOIDs = new TreeSet<>();

    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    File schemaDir = new File(new File(buildRoot, "resource"), "schema");
    for (File f : schemaDir.listFiles())
    {
      if (! f.getName().toLowerCase().endsWith(".ldif"))
      {
        // This could be some other kind of file, like ".svn".
        continue;
      }

      LDIFImportConfig importConfig = new LDIFImportConfig(f.getAbsolutePath());
      LDIFReader reader = new LDIFReader(importConfig);
      Entry e = reader.readEntry();
      reader.close();

      if (e == null)
      {
        // An empty schema file.  This is OK.
        continue;
      }

      List<Attribute> attrList = e.getAttribute(getAttributeTypesAttributeType());
      if (attrList.isEmpty())
      {
        // No attribute types in the schema file.  This is OK.
        continue;
      }

      for (Attribute a : attrList)
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

    if (! invalidOIDs.isEmpty())
    {
      StringBuilder message = new StringBuilder();
      message.append("All attribute types defined in OpenDS must have valid ").append("OIDs assigned.");
      message.append(EOL);
      message.append("Attribute types without valid OIDs:");
      message.append(EOL);
      for (String s : invalidOIDs)
      {
        message.append("- ").append(s);
        message.append(EOL);
      }

      throw new AssertionError(message.toString());
    }
  }



  /**
   * Tests to ensure that all object classes defined in the schema have valid
   * OIDs.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testEnsureValidObjectClassOIDs()
         throws Exception
  {
    TreeSet<String> invalidOIDs = new TreeSet<>();

    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    File schemaDir = new File(new File(buildRoot, "resource"), "schema");
    for (File f : schemaDir.listFiles())
    {
      if (! f.getName().toLowerCase().endsWith(".ldif"))
      {
        // This could be some other kind of file, like ".svn".
        continue;
      }

      LDIFImportConfig importConfig = new LDIFImportConfig(f.getAbsolutePath());
      Entry e;
      try (LDIFReader reader = new LDIFReader(importConfig))
      {
        e = reader.readEntry();
        if (e == null)
        {
          // An empty schema file. This is OK.
          continue;
        }
      }

      List<Attribute> attrList = e.getAttribute("objectclasses");
      if (attrList.isEmpty())
      {
        // No attribute types in the schema file.  This is OK.
        continue;
      }

      for (Attribute a : attrList)
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

    if (! invalidOIDs.isEmpty())
    {
      StringBuilder message = new StringBuilder()
          .append("All object classes defined in OpenDJ must have valid OIDs assigned.").append(EOL)
          .append("Object classes without valid OIDs:").append(EOL);
      for (String s : invalidOIDs)
      {
        message.append("- ").append(s).append(EOL);
      }
      throw new AssertionError(message.toString());
    }
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

    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    File schemaDir = new File(new File(buildRoot, "resource"), "schema");
    for (File f : schemaDir.listFiles())
    {
      if (! f.getName().toLowerCase().endsWith(".ldif"))
      {
        // This could be some other kind of file, like ".svn".
        continue;
      }

      LDIFImportConfig importConfig = new LDIFImportConfig(f.getAbsolutePath());
      LDIFReader reader = new LDIFReader(importConfig);
      Entry e = reader.readEntry();
      reader.close();

      if (e == null)
      {
        // An empty schema file.  This is OK.
        continue;
      }

      List<Attribute> attrList = e.getAttribute(getNameFormsAttributeType());
      if (attrList.isEmpty())
      {
        // No attribute types in the schema file.  This is OK.
        continue;
      }

      for (Attribute a : attrList)
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

    if (! invalidOIDs.isEmpty())
    {
      StringBuilder message = new StringBuilder();
      message.append("All name forms defined in OpenDS must have valid OIDs ").append("assigned.");
      message.append(EOL);
      message.append("Name forms without valid OIDs:");
      message.append(EOL);
      for (String s : invalidOIDs)
      {
        message.append("- ").append(s);
        message.append(EOL);
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
    // It must not be null, and it must not be empty.
    if (oid == null || oid.length() == 0)
    {
      return false;
    }

    // It must start and end with numeric digits.
    if (!Character.isDigit(oid.charAt(0)) ||
        !Character.isDigit(oid.charAt(oid.length()-1)))
    {
      return false;
    }

    // It must contain at least one period.
    if (!oid.contains("."))
    {
      return false;
    }

    // It must not contain any double periods.
    if (oid.contains(".."))
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
}

