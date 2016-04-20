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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */

package org.opends.quicksetup;

import static com.forgerock.opendj.cli.Utils.*;

import static org.opends.messages.AdminToolMessages.*;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.quicksetup.util.Utils;

import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.Menu;
import com.forgerock.opendj.cli.MenuBuilder;
import com.forgerock.opendj.cli.MenuResult;

/** Supports user interactions for a command line driven application. */
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

  @Override
  public Object confirm(LocalizableMessage summary, LocalizableMessage details,
                        LocalizableMessage title, MessageType type, LocalizableMessage[] options,
                        LocalizableMessage def) {
    return confirm(summary, details, null, title, type, options, def, null);
  }

  @Override
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

  @Override
  public String createUnorderedList(List<?> list) {
    StringBuilder sb = new StringBuilder();
    if (list != null) {
      for (Object o : list) {
        sb.append("* ");
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

  @Override
  public boolean isAdvancedMode() {
    return false;
  }

  @Override
  public boolean isInteractive() {
    return isInteractive;
  }

  @Override
  public boolean isMenuDrivenMode() {
    return true;
  }

  @Override
  public boolean isQuiet() {
    return false;
  }

  @Override
  public boolean isScriptFriendly() {
    return false;
  }

  @Override
  public boolean isVerbose() {
    return true;
  }

  @Override
  public boolean isCLI()
  {
    return true;
  }

  @Override
  public boolean isForceOnError() {
    return isForceOnError;
  }
}
