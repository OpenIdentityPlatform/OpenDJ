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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.build.tools;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Arrays;

import org.apache.tools.ant.Task;
import org.apache.tools.ant.BuildException;

public class PrepTestNG extends Task
{

  /** Template for inserting children elements of default test tag */
  static private final String DEFAULT_TAGS_TEMPLATE =
    "<!-- DO NOT REMOVE! - GENERATED DEFAULT TAGS (see PrepTestNG class) -->";

  /** Template for inserting global children elements of run tags */
  static private final String GLOBAL_RUN_TAGS_TEMPLATE =
    "<!-- DO NOT REMOVE! - GENERATED GLOBAL RUN TAGS (see PrepTestNG class) -->";

  /** Indentation used in testng.xml */
  static private final int INDENT = 4;

  private String file;
  private String toFile;
  private String groupList;
  private String packageList;
  private String classList;
  private String methodList;

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

  public void setPackageList(String packageList)
  {
    this.packageList = packageList;
  }

  public void setClassList(String classList)
  {
    this.classList = classList;
  }

  public void setMethodList(String methodList)
  {
    this.methodList = methodList;
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
    String[] packages;
    String[] classes;
    String[] methods;
    String[] groupLine;
    String[] methodLine;
    String methodClass;
    String methodName;
    int methodNameStartIdx;
    int groupCount = 0;
    int packageCount = 0;
    int classCount = 0;
    int methodCount = 0;

    try
    {
      reader = new BufferedReader(new FileReader(file));
      outFile = new FileOutputStream(toFile);

      writer = new PrintStream(outFile);

      line = reader.readLine();

      if(groupList != null && !groupList.trim().equals("") &&
          !groupList.startsWith("${"))
      {
        groups = groupList.split(",");
      }
      else
      {
        groups = new String[0];
      }
      if(packageList != null && !packageList.trim().equals("") &&
          !packageList.startsWith("${"))
      {
        packages = packageList.split(",");
      }
      else
      {
        packages = new String[0];
      }

      if(classList != null && !classList.trim().equals("") &&
          !classList.startsWith("${"))
      {
        classes = classList.split(",");
      }
      else
      {
        classes = new String[0];
      }

      if(methodList != null && !methodList.trim().equals("") &&
          !methodList.startsWith("${"))
      {
        methods = methodList.split(";");
      }
      else
      {
        methods = new String[0];
      }

      while(line != null)
      {
        if(line.indexOf(DEFAULT_TAGS_TEMPLATE) >= 0)
        {
          int level = 2;
          if(groups.length > 0)
          {
            boolean windowsClause = false;
            println(writer, level, "<groups>");
            println(writer, ++level,   "<run>");
            level++;
            for(String group : groups)
            {
              groupLine = group.split("=");
              if(groupLine.length == 2)
              {
                String inc_exc = groupLine[0].trim();
                if (inc_exc == null ||
                        !("include".equals(inc_exc.toLowerCase()) ||
                                "exclude".equals(inc_exc.toLowerCase()))) {
                  System.out.println("Error:  illegal group clause " + group);
                } else {
                  String gr = groupLine[1].trim();
                  println(writer, level, "<" +inc_exc +" "+
                          "name=\""+gr+ "\" />");
                  windowsClause |= "windows".equals(gr);
                  groupCount++;
                }
              }
            }

            // Exclude windows specific tests if the user has not provided
            // an explicit windows clause and we're not on windows.
            if (!windowsClause && !isWindows()) {
              println(writer, level, "<exclude name=\"windows\"/>");
              groupCount++;
            }

            println(writer, --level,   "</run>");
            println(writer, --level, "</groups>");
          } else {

            // No explicit groups have been specified so see if we need
            // to exclude the windows tests.
            if (!isWindows()) {
              println(writer, level,   "<groups>");
              println(writer, ++level,   "<run>");
              println(writer, ++level,     "<exclude name=\"windows\"/>");
              println(writer, --level,   "</run>");
              println(writer, --level, "</groups>");
              groupCount++;
            }
          }

          if(packages.length > 0)
          {
            println(writer, level, "<packages>");
            level++;
            for(String pkg : packages)
            {
              println(writer, level, "<package name=\"" + pkg.trim() + "\" />");
              packageCount++;
            }
            println(writer, --level, "</packages>");
          }

          if(classes.length > 0 || methods.length > 0)
          {
            println(writer, level, "<classes>");

            if(classes.length > 0)
            {
              level++;
              for(String cls : classes)
              {
                println(writer, level, "<class name=\"" + cls.trim() + "\" />");
                classCount++;
              }
            }

            if(methods.length > 0)
            {
              level++;
              for(String mhd : methods)
              {
                methodLine = mhd.split(",");
                if(methodLine.length > 0)
                {
                  // Allow class.method or class#method
                  methodNameStartIdx = methodLine[0].lastIndexOf("#");
                  if (methodNameStartIdx == -1)
                  {
                    methodNameStartIdx = methodLine[0].lastIndexOf(".");
                  }
                  methodClass = methodLine[0].substring(0,
                                  methodNameStartIdx);
                  methodName = methodLine[0].substring(methodNameStartIdx + 1,
                                methodLine[0].length());
                  println(writer, level, "<class name=\"" +
                      methodClass.trim() + "\" >");
                  println(writer, ++level, "<methods>");
                  println(writer, ++level, "<include name=\"" +
                      methodName.trim() + "\" />");
                  methodCount++;
                  classCount++;
                  for(int i = 1; i < methodLine.length; i ++)
                  {
                    println(writer, level, "<include name=\"" +
                      methodLine[i].trim() + "\" />");
                    methodCount++;
                  }
                  println(writer, --level, "</methods>");
                  println(writer, --level, "</class>");
                }
              }
            }

            println(writer, --level, "</classes>");
          }
        }
        else if (line.indexOf(GLOBAL_RUN_TAGS_TEMPLATE) != -1)
        {
          if (!isWindows()) {
            int index = line.indexOf(GLOBAL_RUN_TAGS_TEMPLATE);
            println(writer, levelForIndex(index),
                    "<exclude name=\"windows\"/>");
          }
        }
        else
        {
          println(writer, 0, line);
        }

        line = reader.readLine();
      }

      System.out.println("Adding " + groupCount + " group tags, " +
          packageCount + " package tags, " + classCount + " class tags, " +
          methodCount + " method tags to " + toFile);
    }
    catch(Exception e)
    {
      throw new BuildException("File Error: " + e.toString());
    }
  }

  static private boolean isWindows() {
    String os = System.getProperty("os.name");
    return (os != null && os.toLowerCase().indexOf("windows") != -1);
  }

  static private String indent(int indent) {
    char[] blankArray = new char[indent];
    Arrays.fill(blankArray, ' ');
    return new String(blankArray);
  }

  static private void println(PrintStream writer, int level, String txt) {
    writer.print(indent(INDENT * level));
    writer.print(txt);
    writer.print(System.getProperty("line.separator"));
  }

  static private int levelForIndex(int index) {
    return index / INDENT;
  }

}
