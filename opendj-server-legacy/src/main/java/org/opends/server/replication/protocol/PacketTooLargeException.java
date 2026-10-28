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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.protocol;

import java.io.IOException;

/**
 * Raised by {@link Session#receive()} when this server runs out of heap for a frame: most
 * likely one whose length header announces more bytes than it can allocate, though an
 * {@link OutOfMemoryError} reading or decoding a frame of an ordinary size is raised as this too.
 * <p>
 * It is an {@link IOException}, so that every caller which takes it for a session to give up
 * still does. A handshake tells it apart from the other ones, which are the peer going away:
 * either the peer breaks the protocol, no handshake message coming near the size such a header
 * announces, or this server is short of heap, and either is an error.
 */
public class PacketTooLargeException extends IOException
{
  private static final long serialVersionUID = 4264358734106531071L;

  /**
   * Creates the exception for a frame of the provided length.
   *
   * @param length
   *          the length the header of the frame announced
   */
  public PacketTooLargeException(int length)
  {
    super("Packet too large, can't allocate " + length + " bytes.");
  }
}
