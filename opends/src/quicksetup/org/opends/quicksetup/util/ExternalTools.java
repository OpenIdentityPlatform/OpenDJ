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

package org.opends.quicksetup.util;

import org.opends.quicksetup.Installation;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Class for invoking OpenDS tools in external processes.
 */
public class ExternalTools {

  static private final Logger LOG =
          Logger.getLogger(ServerController.class.getName());

  private Installation installation;

  /**
   * Creates a new instance that will invoke tools from a particular
   * installation in external JVM processes.
   * @param installation representing the tools location
   */
  public ExternalTools(Installation installation) {
    this.installation = installation;
  }

  /**
   * Backs up all the databases to a specified directory.
   * @param backupDir File representing the directory where the backups will
   * be stored
   * @return OperationOutput containing information about the operation
   * @throws java.io.IOException if the process could not be started
   * @throws InterruptedException if the process was prematurely interrupted
   */
  public OperationOutput backup(File backupDir)
          throws IOException, InterruptedException {
    String toolName = Installation.BACKUP;
    List<String> args = new ArrayList<String>();
    args.add(Utils.getPath(installation.getCommandFile(toolName)));
    args.add("-a"); // backup all
    args.add("-d"); // backup to directory
    args.add(Utils.getPath(backupDir));
    return startProcess(toolName, args);
  }

  /**
   * Backs up all the databases to a specified directory.
   * @param source File representing the source data
   * @param target File representing the target data
   * @param otherArgs File representing the output data
   * @return OperationOutput containing information about the operation
   * @throws java.io.IOException if the process could not be started
   * @throws InterruptedException if the process was prematurely interrupted
   */
  public OperationOutput ldifDiff(File source, File target, String... otherArgs)
          throws IOException, InterruptedException {
    String toolName = Installation.LDIF_DIFF;
    List<String> args = new ArrayList<String>();
    args.add(Utils.getPath(installation.getCommandFile(toolName)));
    args.add("-s"); // source LDIF
    args.add(Utils.getPath(source));
    args.add("-t"); // target LDIF
    args.add(Utils.getPath(target));
    if (otherArgs != null) {
      for (String otherArg : otherArgs) {
        args.add(otherArg);
      }
    }
    return startProcess(toolName, args);
  }

  private OperationOutput startProcess(final String toolName, List<String> args)
          throws IOException, InterruptedException
  {
    final OperationOutput oo = new OperationOutput();

    LOG.log(Level.INFO, "Invoking " + Utils.listToString(args, " "));

    ProcessBuilder pb = new ProcessBuilder(args);
    Process p = pb.start();

    BufferedReader out =
            new BufferedReader(new InputStreamReader(p.getErrorStream()));
    new OutputReader(out) {
      public void processLine(String line) {
        oo.addErrorMessage(line);
        LOG.log(Level.INFO, toolName + ": " + line);
      }
    };

    BufferedReader err =
            new BufferedReader(new InputStreamReader(p.getInputStream()));
    new OutputReader(err) {
      public void processLine(String line) {
        oo.addOutputMessage(line);
        LOG.log(Level.INFO, toolName + ": " + line);
      }
    };
    oo.setReturnCode(p.waitFor());
    return oo;
  }

}
