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
 * Raised by {@link Session#receive()} when the length header of a frame announces more bytes
 * than this server can allocate.
 * <p>
 * It is an {@link IOException}, so that every caller which takes it for a session to give up
 * still does. A handshake tells it apart from the other ones, which are the peer going away:
 * no handshake message comes near such a size, so this is the peer breaking the protocol, or
 * this server short of heap, and either is an error.
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
