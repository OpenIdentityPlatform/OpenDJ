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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.util;



import java.io.OutputStream;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;


/**
 * This class defines a simple {@code OutputStream} object that can be used to
 * write all messages to multiple targets at the same time, much like the UNIX
 * "tee" command.  Note that this class will never throw any exceptions
 */
public final class MultiOutputStream
       extends OutputStream
{



  // The set of target output streams to which all messages will be written;
  private final OutputStream[] targetStreams;



  /**
   * Creates a new {@code MultiOutputStream} object that will write all messages
   * to all of the target streams.
   *
   * @param  targetStreams  The set of print streams to which all messages
   *                        should be written.  This must not be {@code null},
   *                        nor may it contain any {@code null} elements.
   */
  public MultiOutputStream(OutputStream... targetStreams)
  {

    Validator.ensureNotNull(targetStreams);

    this.targetStreams = targetStreams;
  }



  /**
   * Closes all of the underlying output streams.
   */
  public void close()
  {

    for (OutputStream s : targetStreams)
    {
      try
      {
        s.close();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }
      }
    }
  }



  /**
   * Flushes all of the underlying output streams.
   */
  public void flush()
  {

    for (OutputStream s : targetStreams)
    {
      try
      {
        s.flush();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }
      }
    }
  }



  /**
   * Writes the contents of the provided byte array to all of the underlying
   * output streams.
   *
   * @param  b  The byte array containing the data to be written.
   */
  public void write(byte[] b)
  {

    for (OutputStream s : targetStreams)
    {
      try
      {
        s.write(b);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }
      }
    }
  }



  /**
   * Writes the specified portion of the provided byte array to all of the
   * underlying output streams.
   *
   * @param  b    The byte array containing the data to be written.
   * @param  off  The position at which the data to write begins in the array.
   * @param  len  The number of bytes to b written.
   */
  public void write(byte[] b, int off, int len)
  {

    for (OutputStream s : targetStreams)
    {
      try
      {
        s.write(b, off, len);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }
      }
    }
  }



  /**
   * Writes the specified byte to the set of target output streams.
   *
   * @param  b  The byte to be written.
   */
  public void write(int b)
  {

    for (OutputStream s : targetStreams)
    {
      try
      {
        s.write(b);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }
      }
    }
  }
}

