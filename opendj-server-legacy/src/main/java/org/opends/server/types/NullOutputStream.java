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
 * Portions Copyright 2013-2016 ForgeRock AS.
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
  public static PrintStream nullPrintStream()
  {
    return printStream;
  }


  /**
   * Returns s wrapped into a {@link PrintStream} if is not null,
   * {@link NullOutputStream#nullPrintStream()} otherwise.
   *
   * @param s
   *          the OutputStream to wrap into a {@link PrintStream}. Can be null.
   * @return a PrintStream wrapping s if not null,
   *         {@link NullOutputStream#nullPrintStream()} otherwise.
   */
  public static PrintStream wrapOrNullStream(OutputStream s)
  {
    if (s != null)
    {
      return new PrintStream(s);
    }
    return NullOutputStream.nullPrintStream();
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

