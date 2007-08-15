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
package org.opends.server.replication.server;
import org.opends.messages.Message;



import org.opends.server.types.IdentifiedException;


/**
 * This class define an Exception that must be used when some error
 * condition was detected in the replicationServer database that cannot be
 * recovered automatically.
 */
public class ReplicationDBException extends IdentifiedException
{

  private static final long serialVersionUID = -8812600147768060090L;

  /**
   * Creates a new ReplicationServer db exception with the provided message.
   * This Exception must be used when the full replicationServer service is
   * compromised by the exception
   *
   * @param  message    The message to use for this exception.
   */
  public ReplicationDBException(Message message)
  {
    super(message);
  }

}
