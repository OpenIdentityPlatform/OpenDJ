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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.replication.protocol;

import java.io.IOException;
import java.net.SocketException;
import java.util.zip.DataFormatException;

/**
 * The ProtocolSession interface should be implemented by a class that
 * implement the send/reception part of the Multi-master replication
 * protocol.
 *
 * This interface is designed to make easy the move from one format
 * of the ReplicationMessage on the wire to another format.
 */
public interface ProtocolSession
{

  /**
   * This method is called when the session with the remote must be closed.
   * This object won't be used anymore after this method is called.
   *
   * @throws IOException If an error happen during the close process.
   */
  public abstract void close() throws IOException;

  /**
   * This method is called when a ReplicationMessage must be sent to
   * the remote entity.
   *
   * It can be called by several threads and must implement appropriate
   * replication (typically, this method or a part of it should be
   * synchronized).
   *
   * @param msg The ReplicationMessage that must be sent.
   * @throws IOException If an IO error happen during the publish process.
   */
  public abstract void publish(ReplicationMessage msg)
                  throws IOException;

  /**
   * Attempt to receive a ReplicationMessage.
   * This method should block the calling thread until a
   * ReplicationMessage is available or until an error condition.
   *
   * This method can only be called by a single thread and therefore does not
   * neet to implement any replication.
   *
   * @return The ReplicationMessage that was received.
   * @throws IOException When error happened durin IO process.
   * @throws ClassNotFoundException When the data received does extend the
   *         ReplicationMessage class.
   * @throws DataFormatException When the data received is not formatted as a
   *         ReplicationMessage.
   */
  public abstract ReplicationMessage receive()
                  throws IOException, ClassNotFoundException,
                         DataFormatException;

  /**
   * Retrieve the IP address of the remote server.
   *
   * @return The IP address of the remote server.
   */
  public abstract String getRemoteAddress();


  /**
  * Set a timeout value.
  * With this option set to a non-zero value, calls to the receive() method
  * block for only this amount of time after which a
  * java.net.SocketTimeoutException is raised.
  * The Broker is valid and useable even after such an Exception is raised.
  *
  * @param timeout the specified timeout, in milliseconds.
  * @throws SocketException if there is an error in the underlying protocol,
  *         such as a TCP error.
  */
  public abstract void setSoTimeout(int timeout) throws SocketException;



  /**
   * Gets the time the last replication message was published on this
   * session.
   * @return The timestamp in milliseconds of the last message published.
   */
  public abstract long getLastPublishTime();



  /**
   * Gets the time the last replication message was received on this
   * session.
   * @return The timestamp in milliseconds of the last message received.
   */
  public abstract long getLastReceiveTime();
}
