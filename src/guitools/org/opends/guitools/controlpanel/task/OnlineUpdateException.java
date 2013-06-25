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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.task;

import org.opends.messages.Message;
import org.opends.server.types.OpenDsException;

/**
 * Exception throw when there is an error updating the configuration online
 * (in general is used as a wrapper when we get a NamingException).
 *
 */
public class OnlineUpdateException extends OpenDsException
{

  private static final long serialVersionUID = 2594845362087209988L;

  /**
   * Creates an exception with a message.
   * @param msg the message.
   */
  public OnlineUpdateException(Message msg)
  {
    super(msg);
  }

  /**
   * Creates an exception with a message and a root cause.
   * @param msg the message.
   * @param rootCause the root cause.
   */
  public OnlineUpdateException(Message msg, Throwable rootCause)
  {
    super(msg, rootCause);
  }
}