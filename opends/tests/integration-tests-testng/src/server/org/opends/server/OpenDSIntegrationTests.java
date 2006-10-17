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
import org.opends.server.OpenDSMgr;
import org.opends.server.tools.StopDS;
import org.opends.server.tools.LDAPSearch;
import org.testng.Assert.*;

/**
 * This class defines an abstract test case that should be subclassed by all
 * integration tests used by OpenDS.
 */
public abstract class OpenDSIntegrationTests {
  // The print stream to use for printing error messages.
  private PrintStream errorStream;
  protected OpenDSTestOutput ds_output = new OpenDSTestOutput();
  protected OpenDSMgr dsMgr = null;

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

  /**
   * Compares the return code from an ldap operation with the expected value.
   *
   * @param retCode  The return code received from the ldap operation.
   * @param expCode  The expected value for the return code.
   */
  public void compareExitCode(int retCode, int expCode)
  {
    System.out.println("Return code is " + Integer.toString(retCode) + ", expecting " + Integer.toString(expCode));
    if (retCode != expCode )
    {
      if (retCode == 999)
	System.out.println("OpenDS could not restart");
      // throw a fail in the testng framework
      org.testng.Assert.assertEquals(retCode,expCode);
    }
  }

  /**
   * Converts a string array of ldap paramters to a string.
   *
   * @param cmd[]    The string array of ldap parameters
   */
  public String cmdArrayToString(String cmd[])
  {
    String outStr = cmd[0];
    for(int i = 1; i < cmd.length; i++)
    {
      outStr = outStr + " " + cmd[i];
    }

    return outStr;
  }

  /**
   * Starts OpenDS
   *
   *  @param  dsee_home              The home directory for the OpenDS
   *                                 installation.
   *  @param  hostname               The hostname for the server where OpenDS
   *                                 is installed.
   *  @param  port                   The port number for OpenDS.
   *  @param  bindDN                 The bind DN.
   *  @param  bindPW                 The password for the bind DN.
   *  @param  logDir                 The directory for the log files that are
   *
   *  @return                        0 if OpenDS started successfully, 1 if not.
   */
  public int startOpenDS(String dsee_home, String hostname, String port, String bindDN, String bindPW, String logDir) throws Exception
  {
    int isAliveCounter = 0;
    String osName = new String(System.getProperty("os.name"));
    System.out.println("OpenDS is starting.....");

    if (osName.indexOf("Windows") >= 0)  // For Windows
    {
      dsMgr = new OpenDSMgr(dsee_home, port);
      dsMgr.start();
    }
    else
    {
      String exec_cmd = dsee_home + "/bin/start-ds.sh -nodetach";
      Runtime rtime = Runtime.getRuntime();
      Process child = rtime.exec(exec_cmd);
    }

    ds_output.redirectOutput(logDir, "Redirect.txt");
    while(isAlive(hostname, port, bindDN, bindPW) != 0)
    {
      if(isAliveCounter % 50 == 0)
      {
        ds_output.resetOutput();
        System.out.println("OpenDS has not yet started.....");
        ds_output.redirectOutput(logDir, "Redirect.txt");
      }

      if(isAliveCounter++ > 5000)
        return 1;
    }

    ds_output.resetOutput();
    System.out.println("OpenDS has started.");
    return 0;
  }

  /**
   * Stops OpenDS
   *
   *  @param  dsee_home              The home directory for the OpenDS
   *                                 installation.
   *  @param  port                   The port number for OpenDS.
   */
  public void stopOpenDS(String dsee_home, String port) throws Exception
  {
    String myArgs[] = {"-p", port};
    System.out.println("OpenDS is stopping.....");
    org.opends.server.tools.StopDS.stopDS(myArgs);
    System.out.println("OpenDS has stopped.");
  }

  /**
   * Tests OpenDS to see if it has started.
   *
   *  @param  hostname               The hostname for the server where OpenDS
   *                                 is installed.
   *  @param  port                   The port number for OpenDS.
   *  @param  bindDN                 The bind DN.
   *  @param  bindPW                 The password for the bind DN.
   *
   *  @return                        0 if OpenDS has started, 1 if not.
   */
  public int isAlive(String hostname, String port, String bindDN, String bindPW)
  {
    String isAlive_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", "", "-s", "base", "(objectclass=*)"};

    return(LDAPSearch.mainSearch(isAlive_args));
  }

}
