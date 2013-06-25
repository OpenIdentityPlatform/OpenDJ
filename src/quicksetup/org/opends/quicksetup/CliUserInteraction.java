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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions copyright 2012-2013 ForgeRock AS.
 */

package org.opends.quicksetup;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

import static org.opends.messages.AdminToolMessages.*;

import org.opends.quicksetup.util.Utils;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.cli.CLIException;
import org.opends.server.util.cli.ConsoleApplication;
import org.opends.server.util.cli.Menu;
import org.opends.server.util.cli.MenuBuilder;
import org.opends.server.util.cli.MenuResult;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Supports user interactions for a command line driven application.
 */
public class CliUserInteraction extends ConsoleApplication
        implements UserInteraction {
  static private final Logger LOG =
    Logger.getLogger(CliUserInteraction.class.getName());

  private final boolean isInteractive;
  private final boolean isForceOnError;

  /**
   * Creates an instance that will use standard streams for interaction and with
   * the provided CLI arguments.
   * @param ud The CLI arguments.
   */
  public CliUserInteraction(UserData ud) {
    super(System.in, System.out, System.err);
    isInteractive = ud == null || ud.isInteractive();
    isForceOnError = ud != null && ud.isForceOnError();
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
    MenuBuilder<Integer> builder = new MenuBuilder<Integer>(this);

    MessageBuilder b = new MessageBuilder();
    b.append(summary);
    b.append(Constants.LINE_SEPARATOR);
    b.append(details);
    builder.setPrompt(b.toMessage());

    int defInt = -1;
    for (int i=0; i<options.length; i++)
    {
      builder.addNumberedOption(options[i], MenuResult.success(i+1));
      if (options[i].equals(def))
      {
        defInt = i+1;
      }
    }

    if (fineDetails != null) {
      Message detailsPrompt = viewDetailsOption;
      if (detailsPrompt == null)
      {
        detailsPrompt = INFO_CLI_VIEW_DETAILS.get();
      }
      builder.addNumberedOption(detailsPrompt,
          MenuResult.success(options.length + 1));
    }

    builder.setDefault(Message.raw(String.valueOf(defInt)),
        MenuResult.success(defInt));

    Menu<Integer> menu = builder.toMenu();

    Object returnValue = null;
    boolean menuDisplayed = false;
    while (returnValue == null) {
      int respInt;
      try
      {
        if (menuDisplayed)
        {
          println();
          builder.setPrompt(null);
          menu = builder.toMenu();
        }
        MenuResult<Integer> m = menu.run();
        menuDisplayed = true;

        if (m.isSuccess())
        {
          respInt = m.getValue();
        }
        else
        {
          // Should never happen.
          throw new RuntimeException();
        }
      }
      catch (CLIException ce)
      {
        respInt = defInt;
        LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
      }
      if (fineDetails != null && respInt == options.length + 1) {
        println();
        println(String.valueOf(fineDetails));
      } else {
        returnValue = options[respInt - 1];
      }
    }
    return returnValue;
  }

  /**
   * {@inheritDoc}
   */
  public String createUnorderedList(List<?> list) {
    StringBuilder sb = new StringBuilder();
    if (list != null) {
      for (Object o : list) {
        sb.append(/*bullet=*/"* ");
        sb.append(o.toString());
        sb.append(Constants.LINE_SEPARATOR);
      }
    }
    return sb.toString();
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
    return isInteractive;
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

  /**
   * {@inheritDoc}
   */
  public boolean isCLI()
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isForceOnError() {
    return isForceOnError;
  }
}
