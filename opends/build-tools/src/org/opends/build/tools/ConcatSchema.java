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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.build.tools;



import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedList;
import java.util.TreeSet;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;



/**
 * This class provides an implementation of an Ant task that concatenates the
 * contents of the files in the schema directory to create a base schema that
 * may be used during the upgrade process.  Each element will also include the
 * X-SCHEMA-FILE extension to indicate the source schema file.
 */
public class ConcatSchema
       extends Task
{
  // The path to the directory containing the schema files.
  private String schemaDirectory;

  // The path to the concatenated schema file to create.
  private String toFile;



  /**
   * Specifies the path to the directory containing the schema files.
   *
   * @param  schemaDirectory  The path to the directory containing the schema
   *                          files.
   */
  public void setSchemaDirectory(String schemaDirectory)
  {
    this.schemaDirectory = schemaDirectory;
  }



  /**
   * Specifies the path to the file to create containing the concatenated schema
   * elements.
   *
   * @param  toFile  The path to the file containing the concatenated schema
   *                 elements.
   */
  public void setToFile(String toFile)
  {
    this.toFile = toFile;
  }



  /**
   * Performs the appropriate processing needed for this task.  In this case,
   * it uses SVNKit to identify all modified files in the current workspace.
   * For all source files, look for comment lines containing the word
   * "copyright" and make sure at least one of them contains the current year.
   */
  @Override()
  public void execute()
  {
    // Get a sorted list of the files in the schema directory.
    TreeSet<String> schemaFileNames = new TreeSet<String>();
    for (File f : new File(schemaDirectory).listFiles())
    {
      if (f.isFile())
      {
        schemaFileNames.add(f.getName());
      }
    }


    // Create a set of lists that will hold the schema elements read from the
    // files.
    LinkedList<String> attributeTypes    = new LinkedList<String>();
    LinkedList<String> objectClasses     = new LinkedList<String>();
    LinkedList<String> nameForms         = new LinkedList<String>();
    LinkedList<String> ditContentRules   = new LinkedList<String>();
    LinkedList<String> ditStructureRules = new LinkedList<String>();
    LinkedList<String> matchingRuleUses  = new LinkedList<String>();


    // Open each of the files in order and read the elements that they contain,
    // appending them to the appropriate lists.
    for (String name : schemaFileNames)
    {
      // Read the contents of the file into a list with one schema element per
      // list element.
      LinkedList<StringBuilder> lines = new LinkedList<StringBuilder>();
      try
      {
        BufferedReader reader = new BufferedReader(new FileReader(
                                         new File(schemaDirectory, name)));

        while (true)
        {
          String line = reader.readLine();
          if (line == null)
          {
            break;
          }
          else if (line.startsWith("#") || (line.length() == 0))
          {
            continue;
          }
          else if (line.startsWith(" "))
          {
            lines.getLast().append(line.substring(1));
          }
          else
          {
            lines.add(new StringBuilder(line));
          }
        }

        reader.close();
      }
      catch (Exception e)
      {
        throw new BuildException("Error while reading schema file " + name +
                                 ":  " + e, e);
      }


      // Iterate through each line in the list.  Find the colon and get the
      // attribute name at the beginning.  If it's someting that we don't
      // recognize, then skip it.  Otherwise, add the X-SCHEMA-FILE extension
      // and add it to the appropriate schema element list.
      for (StringBuilder buffer : lines)
      {
        // Get the line and add the X-SCHEMA-FILE extension to the end of it.
        // All of them should end with " )" but some might have the parenthesis
        // crammed up against the last character so deal with that as well.
        String line = buffer.toString().trim();
        if (line.endsWith(" )"))
        {
         line = line.substring(0, line.length()-1) + "X-SCHEMA-FILE '" + name +
            "' )";
        }
        else if (line.endsWith(")"))
        {
         line = line.substring(0, line.length()-1) + " X-SCHEMA-FILE '" + name +
            "' )";
        }
        else
        {
          continue;
        }

        String lowerLine = line.toLowerCase();
        if (lowerLine.startsWith("attributetypes:"))
        {
          attributeTypes.add(line);
        }
        else if (lowerLine.startsWith("objectclasses:"))
        {
          objectClasses.add(line);
        }
        else if (lowerLine.startsWith("nameforms:"))
        {
          nameForms.add(line);
        }
        else if (lowerLine.startsWith("ditcontentrules:"))
        {
          ditContentRules.add(line);
        }
        else if (lowerLine.startsWith("ditstructurerules:"))
        {
          ditStructureRules.add(line);
        }
        else if (lowerLine.startsWith("matchingruleuse:"))
        {
          matchingRuleUses.add(line);
        }
      }
    }


    // Write the resulting output to the merged schema file.
    try
    {
      BufferedWriter writer = new BufferedWriter(new FileWriter(toFile));
      writer.write("dn: cn=schema");
      writer.newLine();
      writer.write("objectClass: top");
      writer.newLine();
      writer.write("objectClass: ldapSubentry");
      writer.newLine();
      writer.write("objectClass: subschema");
      writer.newLine();

      for (String line : attributeTypes)
      {
        writer.write(line);
        writer.newLine();
      }

      for (String line : objectClasses)
      {
        writer.write(line);
        writer.newLine();
      }

      for (String line : nameForms)
      {
        writer.write(line);
        writer.newLine();
      }

      for (String line : ditContentRules)
      {
        writer.write(line);
        writer.newLine();
      }

      for (String line : ditStructureRules)
      {
        writer.write(line);
        writer.newLine();
      }

      for (String line : matchingRuleUses)
      {
        writer.write(line);
        writer.newLine();
      }

      writer.close();
    }
    catch (Exception e)
    {
      throw new BuildException("Error while writing concatenated schema file " +
                               toFile + ":  " + e, e);
    }
  }
}

