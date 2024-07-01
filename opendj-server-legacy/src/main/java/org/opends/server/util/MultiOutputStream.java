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
package org.opends.server.util;



import java.io.OutputStream;

import org.forgerock.util.Reject;
import org.forgerock.i18n.slf4j.LocalizedLogger;


/**
 * This class defines a simple {@code OutputStream} object that can be used to
 * write all messages to multiple targets at the same time, much like the UNIX
 * "tee" command.  Note that this class will never throw any exceptions
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class MultiOutputStream
       extends OutputStream
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();



  /** The set of target output streams to which all messages will be written. */
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
    Reject.ifNull(targetStreams);

    this.targetStreams = targetStreams;
  }



  /** Closes all of the underlying output streams. */
  @Override
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
        logger.traceException(e);
      }
    }
  }



  /** Flushes all of the underlying output streams. */
  @Override
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
        logger.traceException(e);
      }
    }
  }



  /**
   * Writes the contents of the provided byte array to all of the underlying
   * output streams.
   *
   * @param  b  The byte array containing the data to be written.
   */
  @Override
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
        logger.traceException(e);
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
  @Override
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
        logger.traceException(e);
      }
    }
  }



  /**
   * Writes the specified byte to the set of target output streams.
   *
   * @param  b  The byte to be written.
   */
  @Override
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
        logger.traceException(e);
      }
    }
  }
}

