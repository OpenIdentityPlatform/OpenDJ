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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.schema;



import java.io.File;
import java.util.List;
import java.util.TreeSet;
import java.util.StringTokenizer;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.MatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SchemaConfigManager;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.NameForm;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Schema;
import org.opends.server.util.LDIFReader;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines a set of generic tests that may be used to examine the
 * server schema.
 */
public class GenericSchemaTestCase
       extends SchemaTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void setUp()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests to ensure that all attribute syntaxes defined in the schema have
   * valid OIDs.
   */
  @Test()
  public void testEnsureValidSyntaxOIDs()
  {
    TreeSet<String> invalidOIDs = new TreeSet<String>();

    Schema schema = DirectoryServer.getSchema();
    for (AttributeSyntax as : schema.getSyntaxes().values())
    {
      if (! isNumericOID(as.getOID()))
      {
        invalidOIDs.add(as.getSyntaxName());
      }
    }

    if (! invalidOIDs.isEmpty())
    {
      StringBuilder message = new StringBuilder();
      message.append("All attribute syntaxes defined in OpenDS must have " +
                     "valid OIDs assigned.");
      message.append(EOL);
      message.append("Attribute syntaxes without valid OIDs:");
      message.append(EOL);
      for (String s : invalidOIDs)
      {
        message.append("- " + s);
        message.append(EOL);
      }

      throw new AssertionError(message.toString());
    }
  }



  /**
   * Tests to ensure that all matching rules defined in the schema have valid
   * OIDs.
   */
  @Test()
  public void testEnsureValidMatchingRuleOIDs()
  {
    TreeSet<String> invalidOIDs = new TreeSet<String>();

    Schema schema = DirectoryServer.getSchema();
    for (MatchingRule mr : schema.getMatchingRules().values())
    {
      if (! isNumericOID(mr.getOID()))
      {
        invalidOIDs.add(mr.getNameOrOID());
      }
    }

    if (! invalidOIDs.isEmpty())
    {
      StringBuilder message = new StringBuilder();
      message.append("All matching rules defined in OpenDS must have valid " +
                     "OIDs assigned.");
      message.append(EOL);
      message.append("Matching rules without valid OIDs:");
      message.append(EOL);
      for (String s : invalidOIDs)
      {
        message.append("- " + s);
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
  @Test()
  public void testEnsureValidAttributeTypeOIDs()
         throws Exception
  {
    TreeSet<String> invalidOIDs = new TreeSet<String>();

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

      AttributeType attrType =
           DirectoryServer.getAttributeType("attributetypes", false);
      assertNotNull(attrType);
      List<Attribute> attrList = e.getAttribute(attrType);
      if (attrList == null)
      {
        // No attribute types in the schema file.  This is OK.
        continue;
      }

      for (Attribute a : attrList)
      {
        for (AttributeValue v : a.getValues())
        {
          AttributeType at = AttributeTypeSyntax.decodeAttributeType(
                                  v.getValue(), DirectoryServer.getSchema(),
                                  true);
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
      message.append("All attribute types defined in OpenDS must have valid " +
                     "OIDs assigned.");
      message.append(EOL);
      message.append("Attribute types without valid OIDs:");
      message.append(EOL);
      for (String s : invalidOIDs)
      {
        message.append("- " + s);
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
  @Test()
  public void testEnsureValidObjectClassOIDs()
         throws Exception
  {
    TreeSet<String> invalidOIDs = new TreeSet<String>();

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

      AttributeType attrType =
           DirectoryServer.getAttributeType("objectclasses", false);
      assertNotNull(attrType);
      List<Attribute> attrList = e.getAttribute(attrType);
      if (attrList == null)
      {
        // No attribute types in the schema file.  This is OK.
        continue;
      }

      for (Attribute a : attrList)
      {
        for (AttributeValue v : a.getValues())
        {
          ObjectClass oc = ObjectClassSyntax.decodeObjectClass(
                                v.getValue(), DirectoryServer.getSchema(),
                                true);
          if (! isNumericOID(oc.getOID()))
          {
            invalidOIDs.add(oc.getNameOrOID());
          }
        }
      }
    }

    if (! invalidOIDs.isEmpty())
    {
      StringBuilder message = new StringBuilder();
      message.append("All object classes defined in OpenDS must have valid " +
                     "OIDs assigned.");
      message.append(EOL);
      message.append("Object classes without valid OIDs:");
      message.append(EOL);
      for (String s : invalidOIDs)
      {
        message.append("- " + s);
        message.append(EOL);
      }

      throw new AssertionError(message.toString());
    }
  }



  /**
   * Tests to ensure that all name forms defined in the schema have valid OIDs.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testEnsureValidNameFormOIDs()
         throws Exception
  {
    TreeSet<String> invalidOIDs = new TreeSet<String>();

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

      AttributeType attrType =
           DirectoryServer.getAttributeType("nameforms", false);
      assertNotNull(attrType);
      List<Attribute> attrList = e.getAttribute(attrType);
      if (attrList == null)
      {
        // No attribute types in the schema file.  This is OK.
        continue;
      }

      for (Attribute a : attrList)
      {
        for (AttributeValue v : a.getValues())
        {
          NameForm nf = NameFormSyntax.decodeNameForm(v.getValue(),
                             DirectoryServer.getSchema(), true);
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
      message.append("All name forms defined in OpenDS must have valid OIDs " +
                     "assigned.");
      message.append(EOL);
      message.append("Name forms without valid OIDs:");
      message.append(EOL);
      for (String s : invalidOIDs)
      {
        message.append("- " + s);
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
    if ((oid == null) || (oid.length() == 0))
    {
      return false;
    }

    // It must start and end with numeric digits.
    if ((! Character.isDigit(oid.charAt(0))) ||
        (! Character.isDigit(oid.charAt(oid.length()-1))))
    {
      return false;
    }

    // It must contain at least one period.
    if (oid.indexOf(".") < 0)
    {
      return false;
    }

    // It must not contain any double periods.
    if (oid.indexOf("..") >= 0)
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

