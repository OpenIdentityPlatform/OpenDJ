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
import org.opends.server.tools.StopDS;

public class OpenDSMgr
       extends Thread
{
  private String dsee_home;
  private String port;

  public OpenDSMgr(String in_dsee_home, String in_port)
  {
    dsee_home = in_dsee_home;
    port = in_port;
  }

  public void run()
  {
    try
    {
      startDS();
    }
    catch(Exception e)
    {
      e.printStackTrace();
    }
  }

  public void startDS() throws Exception
  {
    String osName = new String(System.getProperty("os.name"));
    String exec_cmd = "";
      
    if (osName.indexOf("Windows") >= 0)  // For Windows
    {
      exec_cmd = "CMD /C " + dsee_home + "\\bin\\start-ds";
      System.out.println(exec_cmd);
    }
    else
    {
      exec_cmd = dsee_home + "/bin/start-ds.sh -nodetach";
    }

    Runtime rtime = Runtime.getRuntime();
    Process child = rtime.exec(exec_cmd);
  }

}
