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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS
 */
package org.opends.server.types;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * This class defines a custom output stream that simply discards any
 * data written to it.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class NullOutputStream
       extends OutputStream
{
  /**
   * The singleton instance for this class.
   */
  private static final NullOutputStream instance =
       new NullOutputStream();



  /**
   * The singleton print stream tied to the null output stream.
   */
  private static final PrintStream printStream =
       new PrintStream(instance);



  /**
   * Retrieves an instance of this null output stream.
   *
   * @return  An instance of this null output stream.
   */
  public static NullOutputStream instance()
  {
    return instance;
  }



  /**
   * Retrieves a print stream using this null output stream.
   *
   * @return  A print stream using this null output stream.
   */
  public static PrintStream printStream()
  {
    return printStream;
  }


  /**
   * Returns s wrapped into a {@link PrintStream} if is not null,
   * {@link NullOutputStream#printStream()} otherwise.
   *
   * @param s
   *          the OutputStream to wrap into a {@link PrintStream}. Can be null.
   * @return a PrintStream wrapping s if not null,
   *         {@link NullOutputStream#printStream()} otherwise.
   */
  public static PrintStream wrapOrNullStream(OutputStream s)
  {
    if (s != null)
    {
      return new PrintStream(s);
    }
    return NullOutputStream.printStream();
  }


  /**
   * Creates a new instance of this null output stream.
   */
  private NullOutputStream()
  {
    // No implementation is required.
  }



  /**
   * Closes the output stream.  This has no effect.
   */
  @Override
  public void close()
  {
    // No implementation is required.
  }



  /**
   * Flushes the output stream.  This has no effect.
   */
  @Override
  public void flush()
  {
    // No implementation is required.
  }



  /**
   * Writes the provided data to this output stream.  This has no
   * effect.
   *
   * @param  b  The byte array containing the data to be written.
   */
  @Override
  public void write(byte[] b)
  {
    // No implementation is required.
  }



  /**
   * Writes the provided data to this output stream.  This has no
   * effect.
   *
   * @param  b    The byte array containing the data to be written.
   * @param  off  The offset at which the real data begins.
   * @param  len  The number of bytes to be written.
   */
  @Override
  public void write(byte[] b, int off, int len)
  {
    // No implementation is required.
  }



  /**
   * Writes the provided byte to this output stream.  This has no
   * effect.
   *
   * @param  b  The byte to be written.
   */
  @Override
  public void write(int b)
  {
    // No implementation is required.
  }
}

