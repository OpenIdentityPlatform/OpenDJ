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
 * Copyright 2006-2008 Sun Microsystems, Inc.
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

