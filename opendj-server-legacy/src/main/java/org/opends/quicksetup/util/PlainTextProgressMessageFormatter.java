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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.quicksetup.util;

import static org.opends.messages.QuickSetupMessages.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.quicksetup.Constants;

/**
 * This is an implementation of the ProgressMessageFormatter class that
 * provides format in plain text.
 */
public class PlainTextProgressMessageFormatter implements ProgressMessageFormatter
{
  private LocalizableMessage doneText;
  private LocalizableMessage errorText;

  /** The space in plain text. */
  private static String SPACE = " ";

  @Override
  public LocalizableMessage getFormattedText(LocalizableMessage text)
  {
    return text;
  }

  @Override
  public LocalizableMessage getFormattedSummary(LocalizableMessage text)
  {
    return text;
  }

  @Override
  public LocalizableMessage getFormattedError(LocalizableMessage text, boolean applyMargin)
  {
    if (applyMargin)
    {
      return new LocalizableMessageBuilder().append(Constants.LINE_SEPARATOR).append(text).toMessage();
    }
    return text;
  }

  @Override
  public LocalizableMessage getFormattedWarning(LocalizableMessage text, boolean applyMargin)
  {
    if (applyMargin)
    {
      return new LocalizableMessageBuilder(Constants.LINE_SEPARATOR).append(text).toMessage();
    }
    return text;
  }

  @Override
  public LocalizableMessage getFormattedSuccess(LocalizableMessage text)
  {
    return text;
  }

  @Override
  public LocalizableMessage getFormattedLogError(LocalizableMessage text)
  {
    return text;
  }

  @Override
  public LocalizableMessage getFormattedLog(LocalizableMessage text)
  {
    return text;
  }

  @Override
  public LocalizableMessage getFormattedDone()
  {
    if (doneText == null)
    {
      doneText = INFO_PROGRESS_DONE.get();
    }
    return doneText;
  }

  @Override
  public LocalizableMessage getFormattedError()
  {
    if (errorText == null)
    {
      errorText = INFO_PROGRESS_ERROR.get();
    }
    return errorText;
  }

  @Override
  public LocalizableMessage getFormattedWithPoints(LocalizableMessage text)
  {
    return new LocalizableMessageBuilder(text).append(SPACE)
            .append(INFO_PROGRESS_POINTS.get()).append(SPACE).toMessage();
  }

  @Override
  public LocalizableMessage getFormattedPoint()
  {
    return LocalizableMessage.raw(".");
  }

  @Override
  public LocalizableMessage getSpace()
  {
    return LocalizableMessage.raw(SPACE);
  }

  @Override
  public LocalizableMessage getFormattedProgress(LocalizableMessage text)
  {
    return text;
  }

  @Override
  public LocalizableMessage getFormattedError(Throwable t, boolean applyMargin)
  {
    String msg = t.getMessage();
    if (msg == null)
    {
      msg = t.toString();
    }
    String result;
    if (applyMargin)
    {
      result = Constants.LINE_SEPARATOR+msg;
    } else
    {
      result = msg;
    }
    return LocalizableMessage.raw(result);
  }

  @Override
  public LocalizableMessage getLineBreak()
  {
    return LocalizableMessage.raw(Constants.LINE_SEPARATOR);
  }

  @Override
  public LocalizableMessage getTab()
  {
    return LocalizableMessage.raw("     ");
  }

  @Override
  public LocalizableMessage getTaskSeparator()
  {
    return LocalizableMessage.raw(
        Constants.LINE_SEPARATOR+
        "-----------------------------------------------------------------"+
        Constants.LINE_SEPARATOR+Constants.LINE_SEPARATOR);
  }

  @Override
  public LocalizableMessage getFormattedAfterUrlClick(String url, LocalizableMessage lastText)
  {
    throw new IllegalStateException(
        "PlainTextProgressMessageFormatter.getFormattedAfterUrlClick must not "+
        "be called");
  }
}
