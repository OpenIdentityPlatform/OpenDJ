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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.changelog;

import java.io.IOException;
import org.opends.server.synchronization.SynchronizationMessage;

/**
 * The ProtocolSession interface should be implemented by a class that
 * implement the send/reception part of the Multimaster synchronization
 * protocol.
 *
 * This interface is designed to make easy the move from one format
 * of the SynchronizationMessage on the wire to another format.
 */
public interface ProtocolSession
{

  /**
   * This method is called when the session with the remote must be closed.
   * It must
   * This object  won't be used anymore after this method is called.
   *
   * @throws IOException If an error happen during the close process.
   */
  public abstract void close() throws IOException;

  /**
   * This method is called when a SynchronizationMessage must be sent to
   * the remote entity.
   *
   * It can be called by several threads and must implement appropriate
   * synchronization (typically, this method or a part of it should be
   * synchronized).
   *
   * @param msg The SynchronizationMessage that must be sent.
   * @throws IOException If an IO error happen during the publish process.
   */
  public abstract void publish(SynchronizationMessage msg)
                  throws IOException;

  /**
   * Attempt to receive a SynchronizationMessage.
   * This method should block the calling thread until a
   * SynchronizationMessage is available or until an error condition.
   *
   * This method can only be called by a single thread and therefore does not
   * neet to implement any synchronization.
   *
   * @return The SynchronizationMessage that was received.
   * @throws IOException When error happened durin IO process.
   * @throws ClassNotFoundException When the data received does extend the
   *         SynchronizationMessage class.
   */
  public abstract SynchronizationMessage receive()
                  throws IOException, ClassNotFoundException;
}
