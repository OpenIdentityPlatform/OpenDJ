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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.util.cli;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.quicksetup.util.PlainTextProgressMessageFormatter;
import org.opends.quicksetup.util.ProgressMessageFormatter;

import com.forgerock.opendj.cli.ConsoleApplication;

/** Class used to add points periodically to the end of the output. */
public class PointAdder implements Runnable
{
  private final ConsoleApplication app;
  private Thread t;
  private boolean stopPointAdder;
  private boolean pointAdderStopped;
  private final long periodTime;
  private final ProgressMessageFormatter formatter;

  /** The default period time used to write points in the output. */
  private static final long DEFAULT_PERIOD_TIME = 3000;

  /**
   * Default constructor.
   *
   * @param app
   *          The console application to be used. Creates a PointAdder that
   *          writes to the standard output with the default period time.
   */
  public PointAdder(ConsoleApplication app)
  {
    this(app, DEFAULT_PERIOD_TIME, new PlainTextProgressMessageFormatter());
  }

  /**
   * Default constructor.
   *
   * @param app
   *          The console application to be used.
   * @param periodTime
   *          The time between printing two points.
   * @param formatter
   *          The text formatter.
   */
  private PointAdder(ConsoleApplication app, long periodTime, ProgressMessageFormatter formatter)
  {
    this.app = app;
    this.periodTime = periodTime;
    this.formatter = formatter;
  }

  /** Starts the PointAdder: points are added at the end of the logs periodically. */
  public void start()
  {
    LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
    mb.append(formatter.getSpace());
    for (int i=0; i< 5; i++)
    {
      mb.append(formatter.getFormattedPoint());
    }
    app.print(mb.toMessage());
    t = new Thread(this);
    t.start();
  }

  /** Stops the PointAdder: points are no longer added at the end of the logs periodically. */
  public synchronized void stop()
  {
    stopPointAdder = true;
    while (!pointAdderStopped)
    {
      try
      {
        t.interrupt();
        // To allow the thread to set the boolean.
        Thread.sleep(100);
      }
      catch (Throwable t)
      {
      }
    }
  }

  @Override
  public void run()
  {
    while (!stopPointAdder)
    {
      try
      {
        Thread.sleep(periodTime);
        app.print(formatter.getFormattedPoint());
      }
      catch (Throwable t)
      {
      }
    }
    pointAdderStopped = true;
  }
}
