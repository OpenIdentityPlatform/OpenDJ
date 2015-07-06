/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS.
 */

package org.opends.quicksetup;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;

import static org.opends.messages.AdminToolMessages.*;
import static com.forgerock.opendj.cli.Utils.wrapText;

import org.opends.quicksetup.util.Utils;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.Menu;
import com.forgerock.opendj.cli.MenuBuilder;
import com.forgerock.opendj.cli.MenuResult;

import java.util.List;

import org.forgerock.i18n.slf4j.LocalizedLogger;

/**
 * Supports user interactions for a command line driven application.
 */
public class CliUserInteraction extends ConsoleApplication
        implements UserInteraction {
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final boolean isInteractive;
  private final boolean isForceOnError;

  /**
   * Creates an instance that will use standard streams for interaction and with
   * the provided CLI arguments.
   * @param ud The CLI arguments.
   */
  public CliUserInteraction(UserData ud) {
    isInteractive = ud == null || ud.isInteractive();
    isForceOnError = ud != null && ud.isForceOnError();
  }

  /** {@inheritDoc} */
  public Object confirm(LocalizableMessage summary, LocalizableMessage details,
                        LocalizableMessage title, MessageType type, LocalizableMessage[] options,
                        LocalizableMessage def) {
    return confirm(summary, details, null, title, type, options, def, null);
  }

  /** {@inheritDoc} */
  public Object confirm(LocalizableMessage summary, LocalizableMessage details, LocalizableMessage fineDetails,
                        LocalizableMessage title, MessageType type, LocalizableMessage[] options,
                        LocalizableMessage def, LocalizableMessage viewDetailsOption) {
    MenuBuilder<Integer> builder = new MenuBuilder<>(this);

    LocalizableMessageBuilder b = new LocalizableMessageBuilder();
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
      LocalizableMessage detailsPrompt = viewDetailsOption;
      if (detailsPrompt == null)
      {
        detailsPrompt = INFO_CLI_VIEW_DETAILS.get();
      }
      builder.addNumberedOption(detailsPrompt,
          MenuResult.success(options.length + 1));
    }

    builder.setDefault(LocalizableMessage.raw(String.valueOf(defInt)),
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
      catch (ClientException ce)
      {
        respInt = defInt;
        logger.warn(LocalizableMessage.raw("Error reading input: "+ce, ce));
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

  /** {@inheritDoc} */
  public String createUnorderedList(List<?> list) {
    StringBuilder sb = new StringBuilder();
    if (list != null) {
      for (Object o : list) {
        sb.append(/*bullet=*/"* ");
        sb.append(o);
        sb.append(Constants.LINE_SEPARATOR);
      }
    }
    return sb.toString();
  }

  private void println(String text) {
    text = Utils.convertHtmlBreakToLineSeparator(text);
    text = Utils.stripHtml(text);
    text = wrapText(text, Utils.getCommandLineMaxLineWidth());
    getErrorStream().println(text);
  }

  /** {@inheritDoc} */
  public boolean isAdvancedMode() {
    return false;
  }

  /** {@inheritDoc} */
  public boolean isInteractive() {
    return isInteractive;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isMenuDrivenMode() {
    return true;
  }

  /** {@inheritDoc} */
  public boolean isQuiet() {
    return false;
  }

  /** {@inheritDoc} */
  public boolean isScriptFriendly() {
    return false;
  }

  /** {@inheritDoc} */
  public boolean isVerbose() {
    return true;
  }

  /** {@inheritDoc} */
  public boolean isCLI()
  {
    return true;
  }

  /** {@inheritDoc} */
  public boolean isForceOnError() {
    return isForceOnError;
  }
}
