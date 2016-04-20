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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import org.opends.guitools.controlpanel.event.PrintStreamListener;

/**
 * This class is used to notify the ProgressUpdateListeners of events
 * that are written to the standard streams.
 */
public class ApplicationPrintStream extends PrintStream
{
  private ArrayList<PrintStreamListener> listeners = new ArrayList<>();
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private boolean notifyListeners = true;

  /** Default constructor. */
  public ApplicationPrintStream()
  {
    super(new ByteArrayOutputStream(), true);
  }

  @Override
  public void println(String msg)
  {
    notifyListenersNewLine(msg);
    logger.info(LocalizableMessage.raw(msg));
  }

  @Override
  public void write(byte[] b, int off, int len)
  {
    if (b == null)
    {
      throw new NullPointerException("b is null");
    }

    if (off + len > b.length)
    {
      throw new IndexOutOfBoundsException(
          "len + off are bigger than the length of the byte array");
    }
    println(new String(b, off, len));
  }

  /**
   * Adds a print stream listener.
   * @param listener the listener.
   */
  public void addListener(PrintStreamListener listener)
  {
    listeners.add(listener);
  }

  /**
   * Removes a print stream listener.
   * @param listener the listener.
   */
  public void removeListener(PrintStreamListener listener)
  {
    listeners.remove(listener);
  }

  private void notifyListenersNewLine(String msg)
  {
    if (notifyListeners)
    {
      for (PrintStreamListener listener : listeners)
      {
        listener.newLine(msg);
      }
    }
  }

  /**
   * Sets whether the listeners must be notified or not.
   * @param notifyListeners whether the listeners must be notified or not.
   */
  public void setNotifyListeners(boolean notifyListeners)
  {
    this.notifyListeners = notifyListeners;
  }
}
