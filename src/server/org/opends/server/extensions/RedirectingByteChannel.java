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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.opends.server.extensions;



import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;



/**
 * This class redirects read and write requests either to a child byte channel,
 * or a byte channel to be redirected to.
 */
public class RedirectingByteChannel implements ByteChannel
{
  /**
   * Create an instance of a redirecting byte channel using the specified byte
   * channel as the child.
   *
   * @param bc
   *          A byte channel to use as the child.
   * @return A redirecting byte channel.
   */
  public static RedirectingByteChannel getRedirectingByteChannel(
      final ByteChannel bc)
  {
    return new RedirectingByteChannel(bc);
  }



  private final ByteChannel child;
  private volatile ByteChannel redirect = null;



  private RedirectingByteChannel(final ByteChannel child)
  {
    this.child = child;
  }



  /**
   * {@inheritDoc}
   */
  public void close() throws IOException
  {
    final ByteChannel tmp = redirect;
    if (tmp != null)
    {
      tmp.close();
    }
    else
    {
      child.close();
    }
  }



  /**
   * Disable redirection.
   */
  public final void disable()
  {
    redirect = null;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isOpen()
  {
    final ByteChannel tmp = redirect;
    if (tmp != null)
    {
      return tmp.isOpen();
    }
    else
    {
      return child.isOpen();
    }
  }



  /**
   * {@inheritDoc}
   */
  public int read(final ByteBuffer buffer) throws IOException
  {
    final ByteChannel tmp = redirect;
    if (tmp != null)
    {
      return tmp.read(buffer);
    }
    else
    {
      return child.read(buffer);
    }
  }



  /**
   * Redirects a byte channel to a byte channel associated with the specified
   * provider.
   *
   * @param provider
   *          The provider to redirect to.
   */
  public final void redirect(final ConnectionSecurityProvider provider)
  {
    redirect = provider.getChannel();
  }



  /**
   * {@inheritDoc}
   */
  public int write(final ByteBuffer buffer) throws IOException
  {
    final ByteChannel tmp = redirect;
    if (tmp != null)
    {
      return tmp.write(buffer);
    }
    else
    {
      return child.write(buffer);
    }
  }
}
