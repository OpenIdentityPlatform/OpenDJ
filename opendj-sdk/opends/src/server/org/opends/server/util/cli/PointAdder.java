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
 *      Copyright 2010 Sun Microsystems, Inc.
 */
package org.opends.server.util.cli;

import org.opends.messages.MessageBuilder;
import org.opends.quicksetup.util.PlainTextProgressMessageFormatter;
import org.opends.quicksetup.util.ProgressMessageFormatter;

/**
 * Class used to add points periodically to the end of the output.
 *
 */
public class PointAdder implements Runnable
{
  private final ConsoleApplication app;
  private Thread t;
  private boolean stopPointAdder;
  private boolean pointAdderStopped;
  private long periodTime = DEFAULT_PERIOD_TIME;
  private final boolean isError;
  private final ProgressMessageFormatter formatter;


  /**
   * The default period time used to write points in the output.
   */
  public static final long DEFAULT_PERIOD_TIME = 3000;

  /**
   * Default constructor.
   * @param app the console application to be used.
   * Creates a PointAdder that writes to the standard output with the default
   * period time.
   */
  public PointAdder(ConsoleApplication app)
  {
    this(app, DEFAULT_PERIOD_TIME, false,
        new PlainTextProgressMessageFormatter());
  }

  /**
   * Default constructor.
   * @param app the console application to be used.
   * @param periodTime the time between printing two points.
   * @param isError whether the points must be printed in error stream
   * or output stream.
   * @param formatter the text formatter.
   */
  public PointAdder(ConsoleApplication app,
      long periodTime, boolean isError,
      ProgressMessageFormatter formatter)
  {
    this.app = app;
    this.periodTime = periodTime;
    this.isError = isError;
    this.formatter = formatter;
  }

  /**
   * Starts the PointAdder: points are added at the end of the logs
   * periodically.
   */
  public void start()
  {
    MessageBuilder mb = new MessageBuilder();
    mb.append(formatter.getSpace());
    for (int i=0; i< 5; i++)
    {
      mb.append(formatter.getFormattedPoint());
    }
    if (isError)
    {
      app.print(mb.toMessage());
    }
    else
    {
      app.printProgress(mb.toMessage());
    }
    t = new Thread(this);
    t.start();
  }

  /**
   * Stops the PointAdder: points are no longer added at the end of the logs
   * periodically.
   */
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

  /**
   * {@inheritDoc}
   */
  public void run()
  {
    while (!stopPointAdder)
    {
      try
      {
        Thread.sleep(periodTime);
        if (isError)
        {
          app.print(formatter.getFormattedPoint());
        }
        else
        {
          app.printProgress(formatter.getFormattedPoint());
        }
      }
      catch (Throwable t)
      {
      }
    }
    pointAdderStopped = true;
  }
}
