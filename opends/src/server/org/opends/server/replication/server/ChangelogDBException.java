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



import org.opends.server.types.IdentifiedException;



/**
 * This class define an Exception that must be used when some error
 * condition was detected in the changelog database that cannot be recovered
 * automatically.
 */
public class ChangelogDBException extends IdentifiedException
{
  private int messageID;

  private static final long serialVersionUID = -8812600147768060090L;

  /**
   * Creates a new Changelog db exception with the provided message.
   * This Exception must be used when the full changelog service is
   * compromised by the exception
   *
   * @param  messageID  The unique message ID for the provided message.
   * @param  message    The message to use for this exception.
   */
  public ChangelogDBException(int messageID, String message)
  {
    super(message);

    this.messageID = messageID;
  }

  /**
   * Returns the message Id associated to this Exception.
   * @return the message ID associated to this Exception.
   */
  public int getMessageID()
  {
    return messageID;
  }
}
