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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */

package org.opends.build.tools;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.FileReader;

import org.apache.tools.ant.Task;
import org.apache.tools.ant.BuildException;

public class PrepTestNG extends Task
{
  private String file;
  private String toFile;
  private String groupList;

  public void setFile(String file)
  {
    this.file = file;
  }

  public void setToFile(String toFile)
  {
    this.toFile = toFile;
  }

  public void setGroupList(String groupList)
  {
    this.groupList = groupList;
  }

  public void execute() throws BuildException
  {
    if(file == null)
    {
      throw new BuildException("Attribute file must be set to the orginal " +
          "TestNG XML file");
    }

    if(toFile == null)
    {
      throw new BuildException("Attribute toFile must be set to the modified " +
          "TestNG XML file");
    }

    BufferedReader reader;
    FileOutputStream outFile;
    PrintStream writer;
    String line;
    String[] groups;
    String[] groupLine;
    int replaced = 0;

    try
    {
      reader = new BufferedReader(new FileReader(file));
      outFile = new FileOutputStream(toFile);

      writer = new PrintStream(outFile);

      line = reader.readLine();

      if(groupList != null && groupList.trim() != "")
      {
        groups = groupList.split(",");
      }
      else
      {
        groups = new String[0];
      }

      while(line != null)
      {
        if(line.indexOf("<!-- THIS WILL BE REPLACED WITH GROUP INFO BY " +
            "ANT -->") >= 0)
        {
          if(groups.length > 0 && groupList.trim() != "")
          {
            writer.println("<groups>\n<run>");
            for(String group : groups)
            {
              groupLine = group.split("=");
              if(groupLine.length == 2)
              {
                writer.println("<"+groupLine[0]+" " +
                               "name=\""+groupLine[1] + "\" />\n");
                replaced++;
              }
            }
            writer.println("</run>\n</groups>");
          }
        }
        else
        {
          writer.println(line);
        }

        line = reader.readLine();
      }

      System.out.println("Adding " + replaced + " group tags to " + toFile);
    }
    catch(Exception e)
    {
      throw new BuildException("File Error: " + e.toString());
    }
  }
}
