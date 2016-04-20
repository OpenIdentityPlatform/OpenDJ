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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
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
  private volatile ByteChannel redirect;

  private RedirectingByteChannel(final ByteChannel child)
  {
    this.child = child;
  }

  @Override
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

  /** Disable redirection. */
  public final void disable()
  {
    redirect = null;
  }

  @Override
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

  @Override
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

  @Override
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
