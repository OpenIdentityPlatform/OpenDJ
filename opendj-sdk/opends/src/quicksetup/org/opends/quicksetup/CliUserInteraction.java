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

import org.opends.quicksetup.util.Utils;
import org.opends.server.util.StaticUtils;

import java.util.List;
import java.util.ArrayList;
import java.io.PrintStream;
import java.io.InputStream;

/**
 * Supports user interactions for a command line driven application.
 */
public class CliUserInteraction extends CliApplicationHelper
        implements UserInteraction {

  private PrintStream out;
  private PrintStream err;
  private InputStream in;

  /**
   * Creates an instance that will use standard streams for interaction.
   */
  public CliUserInteraction() {
    this.out = System.out;
    this.err = System.err;
    this.in = System.in;
  }

  /**
   * Creates an instance using specific streams.
   * @param out OutputStream where prompts will be written
   * @param err OutputStream where errors will be written
   * @param in InputStream from which information will be read
   */
  public CliUserInteraction(PrintStream out, PrintStream err, InputStream in) {
    this.out = out;
    this.err = err;
    this.in = in;
  }

  /**
   * {@inheritDoc}
   */
  public Object confirm(String summary, String details,
                        String title, MessageType type, String[] options,
                        String def) {
    return confirm(summary, details, null, title, type, options, def, null);
  }

  /**
   * {@inheritDoc}
   */
  public Object confirm(String summary, String details, String fineDetails,
                        String title, MessageType type, String[] options,
                        String def, String viewDetailsOption) {
    List<String> sOptions = new ArrayList<String>();
    int defInt = -1;
    for (int i = 0; i < options.length; i++) {
      sOptions.add(createOption(i + 1, options[i]));
      if (options[i].equals(def)) {
        defInt = i + 1;
      }
    }
    if (fineDetails != null) {
      sOptions.add(createOption(options.length + 1,
              viewDetailsOption != null ? viewDetailsOption : "View Details"));
    }

    println();
    println(Utils.stripHtml(summary));
    println();
    println(Utils.stripHtml(details));

    String returnValue = null;
    while (returnValue == null) {
      println();
      for (String o : sOptions) {
        println(o);
      }
      System.out.print(getMsg("cli-uninstall-confirm-prompt",
          new String[] {"Enter a number or press Enter to accept the default",
                  Integer.toString(defInt)}));

      System.out.flush();

      String response = readLine(in, err);
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
        println(fineDetails);
      } else if (respInt > 0 && respInt <= options.length) {
        returnValue = options[respInt - 1];
      } else {
        println("Illegal response " + response);
      }
    }
    return returnValue;
  }



  private String createOption(int index, String option) {
    return new StringBuilder().
            append(Integer.toString(index)).
            append(". ").
            append(option).toString();
  }

  private void println() {
    out.println();
  }

  private void println(String text) {
    out.println(StaticUtils.wrapText(text, Utils.getCommandLineMaxLineWidth()));
  }

}
