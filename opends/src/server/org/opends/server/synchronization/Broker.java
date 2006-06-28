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
package org.opends.server.synchronization;

import java.util.ArrayList;

/**
 * define interface for synchronization Broker.
 */
public abstract class Broker
{
  /**
   * Start the Changelog Broker.
   *
   * @param identifier identifier for the broker
   * @param servers servers that are part of teh broker
   * @throws Exception in case of errors
   */
  public abstract void start(Short identifier,
                             ArrayList<String> servers)
                             throws Exception;
  /**
   * publish a message to the broker.
   *
   * @param msg the message to be published
   */
  public abstract void publish(UpdateMessage msg);

  /**
   * receive a message form the broker.
   *
   * @return the message that was received
   */
  public abstract UpdateMessage receive();

  /**
   * Stop the broker.
   */
  public abstract void stop();
  /**
   * restart reception after suspension.
   * @throws Exception in case of errors
   */
  public abstract void restartReceive() throws Exception;
  /**
   * temporarily stop receiving messages.
   * @throws Exception in case of errors
   */
  public abstract void suspendReceive() throws Exception;
}
