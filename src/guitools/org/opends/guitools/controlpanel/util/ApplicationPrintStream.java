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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opends.guitools.controlpanel.event.PrintStreamListener;

/**
 * This class is used to notify the ProgressUpdateListeners of events
 * that are written to the standard streams.
 */
public class ApplicationPrintStream extends PrintStream
{
  private ArrayList<PrintStreamListener> listeners =
    new ArrayList<PrintStreamListener>();
  private static final Logger LOG =
    Logger.getLogger(ApplicationPrintStream.class.getName());

  private boolean notifyListeners = true;

  /**
   * Default constructor.
   *
   */
  public ApplicationPrintStream()
  {
    super(new ByteArrayOutputStream(), true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void println(String msg)
  {
    notifyListenersNewLine(msg);
    LOG.log(Level.INFO, msg);
  }

  /**
   * {@inheritDoc}
   */
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
