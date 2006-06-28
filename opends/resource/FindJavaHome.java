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
import java.io.*;



/**
 * This program provides a simple utility that determines the location of the
 * Java installation.  The output will be in a form suitable for use in a
 * JAVA_HOME environment variable.
 */
public class FindJavaHome
{
  public static void main(String[] args)
         throws Exception
  {
    String javaHome = System.getProperty("java.home");
    File javaHomeDir = new File(javaHome);
    if (! javaHomeDir.exists())
    {
      throw new Exception("System property java.home doesn't reference a " +
                          "real directory");
    }

    String javacPath = File.separator + "bin" + File.separator + "javac";
    File javacFile = new File(javaHome + javacPath);
    if (! javacFile.exists())
    {
      javacFile = new File(javaHomeDir.getParent() + javacPath);
      if (javacFile.exists())
      {
        javaHomeDir = new File(javaHomeDir.getParent());
      }
      else
      {
        throw new Exception("Unable to determine Java compiler location " +
                            "from java.home property value " + javaHome);
      }
    }

    System.out.println(javaHomeDir.getAbsolutePath());
  }
}

