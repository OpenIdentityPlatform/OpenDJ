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
package org.opends.server;

import java.io.*;

/**
 * This class manages the redirection of the output.
 */
public class OpenDSTestOutput
{
  private PrintStream std_out;
  private PrintStream std_err;
  private PrintStream ps;

  /**
   *  Creates a new OpenDSTestOutput.
   */
  public OpenDSTestOutput()
  {
    std_out = System.out;
    std_err = System.out;
  }

  /**
   *  Redirects standard out and standard error to a file.
   *
   *  @param  dirname      Directory
   *  @param  filename     Filename
   */
  public void redirectOutput(String dirname, String filename) throws Exception
  {
    ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(new File(dirname, filename))));
    System.setErr(ps);
    System.setOut(ps);
  }

  /**
   *  Resets output streams to standard out and standard error.
   */
  public void resetOutput()
  {
    if((ps != null) && (std_err != null) && (std_out != null))
    { 
      ps.close();
      System.setErr(std_err);
      System.setOut(std_out);
    }
  }

}
