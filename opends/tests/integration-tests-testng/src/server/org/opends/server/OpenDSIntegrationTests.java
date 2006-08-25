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
package org.opends.server;

import java.io.*;
import org.opends.server.OpenDSAdmin;
import  org.opends.server.tools.StopDS;

/**
 * This class defines a base test case that should be subclassed by all
 * unit tests used by the Directory Server.
 * <p>
 * This class adds the ability to print error messages and automatically
 * have them include the class name.
 */
public abstract class OpenDSIntegrationTests {
  // The print stream to use for printing error messages.
  private PrintStream errorStream;
  protected OpenDSTestOutput ds_output = new OpenDSTestOutput();
  protected OpenDSAdmin dsAdmin = null;

  /**
   * Creates a new instance of this test case with the provided name.
   */
  protected OpenDSIntegrationTests() {
    this.errorStream = System.err;
  }

  /**
   * Prints the provided message to the error stream, prepending the
   * fully-qualified class name.
   *
   * @param message
   *          The message to be printed to the error stream.
   */
  public final void printError(String message) {
    errorStream.print(getClass().getName());
    errorStream.print(" -- ");
    errorStream.println(message);
  }

  /**
   * Prints the stack trace for the provided exception to the error
   * stream.
   *
   * @param exception
   *          The exception to be printed to the error stream.
   */
  public final void printException(Throwable exception) {
    exception.printStackTrace(errorStream);
  }

  /**
   * Specifies the error stream to which messages will be printed.
   *
   * @param errorStream
   *          The error stream to which messages will be printed.
   */
  public final void setErrorStream(PrintStream errorStream) {
    this.errorStream = errorStream;
  }

  public void compareExitCode(int retCode, int expCode)
  {
    System.out.println("Return code is " + Integer.toString(retCode) + ", expecting " + Integer.toString(expCode));
    if (retCode != expCode )
    {
      // throw a fail in the testng framewok
      assert retCode==expCode;
    }
  }

  public String cmdArrayToString(String cmd[])
  {
    String outStr = cmd[0];
    for(int i = 1; i < cmd.length; i++)
    {
      outStr = outStr + " " + cmd[i];
    }

    return outStr;
  }

  public void startOpenDS(String dsee_home, String port) throws Exception
  {
    String osName = new String(System.getProperty("os.name"));
    String exec_cmd = "";
    System.out.println("OpenDS is starting.....");
      
    if (osName.indexOf("Windows") >= 0)  // For Windows
    {
      exec_cmd = "CMD /C " + dsee_home + "\\bin\\start-ds";
    }
    else
    {
      exec_cmd = dsee_home + "/bin/start-ds.sh -nodetach";
    }

    Runtime rtime = Runtime.getRuntime();
    Process child = rtime.exec(exec_cmd);
    dsAdmin.sleep(30000);
    System.out.println("OpenDS has started.");
  }

  public void stopOpenDS(String dsee_home, String port) throws Exception
  {
    String myArgs[] = {"-p", port};
    System.out.println("OpenDS is stopping.....");
    org.opends.server.tools.StopDS.stopDS(myArgs);
    System.out.println("OpenDS has stopped.");
  }

}

