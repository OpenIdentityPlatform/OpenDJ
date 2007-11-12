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

package org.opends.quicksetup;

import org.opends.messages.Message;
import static org.opends.messages.AdminToolMessages.*;

import org.opends.quicksetup.util.Utils;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.cli.CLIException;
import org.opends.server.util.cli.ConsoleApplication;

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.PrintStream;
import java.io.InputStream;

/**
 * Supports user interactions for a command line driven application.
 */
public class CliUserInteraction extends ConsoleApplication
        implements UserInteraction {
  static private final Logger LOG =
    Logger.getLogger(CliUserInteraction.class.getName());

  /**
   * Creates an instance that will use standard streams for interaction.
   */
  public CliUserInteraction() {
    super(System.in, System.out, System.err);
  }

  /**
   * Creates an instance using specific streams.
   * @param out OutputStream where prompts will be written
   * @param err OutputStream where errors will be written
   * @param in InputStream from which information will be read
   */
  public CliUserInteraction(PrintStream out, PrintStream err, InputStream in) {
    super(in, out, err);
  }

  /**
   * {@inheritDoc}
   */
  public Object confirm(Message summary, Message details,
                        Message title, MessageType type, Message[] options,
                        Message def) {
    return confirm(summary, details, null, title, type, options, def, null);
  }

  /**
   * {@inheritDoc}
   */
  public Object confirm(Message summary, Message details, Message fineDetails,
                        Message title, MessageType type, Message[] options,
                        Message def, Message viewDetailsOption) {
    List<String> sOptions = new ArrayList<String>();
    int defInt = -1;
    for (int i = 0; i < options.length; i++) {
      sOptions.add(createOption(i + 1, options[i].toString()));
      if (options[i].equals(def)) {
        defInt = i + 1;
      }
    }
    if (fineDetails != null) {
      sOptions.add(createOption(options.length + 1,
              viewDetailsOption != null ?
                      viewDetailsOption.toString() :
                      INFO_CLI_VIEW_DETAILS.get().toString()));
    }

    println(summary);
    println();
    println(details);

    Object returnValue = null;
    while (returnValue == null) {
      println();
      for (String o : sOptions) {
        println(o);
      }
      print(
          Message.raw("%s%n[%s]:",
              INFO_CLI_NUMBER_PROMPT.get().toString(),
              Integer.toString(defInt)));

      String response = "";
      try
      {
        response = readLineOfInput(null);
      }
      catch (CLIException ce)
      {
        LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
      }
      int respInt = -1;
      if (response.equals("")) {
        respInt = defInt;
      } else {
        try {
          respInt = Integer.parseInt(response);
        } catch (Exception e) {
          // do nothing;
        }
      }
      if (fineDetails != null && respInt == options.length + 1) {
        println(String.valueOf(fineDetails));
      } else if (respInt > 0 && respInt <= options.length) {
        returnValue = options[respInt - 1];
      } else {
        println(INFO_CLI_INVALID_RESPONSE.get()+" " + response);
      }
    }
    return returnValue;
  }

  /**
   * {@inheritDoc}
   */
  public String createUnorderedList(List list) {
    StringBuilder sb = new StringBuilder();
    if (list != null) {
      for (Object o : list) {
        sb.append(/*bullet=*/"\u2022 ");
        sb.append(o.toString());
        sb.append(Constants.LINE_SEPARATOR);
      }
    }
    return sb.toString();
  }

  /**
   * {@inheritDoc}
   */
  public String promptForString(Message prompt, Message title,
                                String defaultValue) {

    return readInput(prompt, defaultValue, LOG);
  }

  private String createOption(int index, String option) {
    return new StringBuilder().
            append(Integer.toString(index)).
            append(". ").
            append(option).toString();
  }

  private void println(String text) {
    text = Utils.convertHtmlBreakToLineSeparator(text);
    text = Utils.stripHtml(text);
    text = StaticUtils.wrapText(text, Utils.getCommandLineMaxLineWidth());
    getErrorStream().println(text);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isAdvancedMode() {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isInteractive() {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isMenuDrivenMode() {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isQuiet() {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isScriptFriendly() {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isVerbose() {
    return true;
  }
}
