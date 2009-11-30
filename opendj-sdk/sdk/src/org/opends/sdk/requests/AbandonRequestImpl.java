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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.requests;

/**
 * Abandon request implementation.
 */
final class AbandonRequestImpl extends
    AbstractRequestImpl<AbandonRequest> implements AbandonRequest
{

  private int messageID;



  /**
   * Creates a new abandon request using the provided message ID.
   * 
   * @param messageID
   *          The message ID of the request to be abandoned.
   */
  AbandonRequestImpl(int messageID)
  {
    this.messageID = messageID;
  }



  public int getMessageID()
  {
    return messageID;
  }



  /**
   * {@inheritDoc}
   */
  public AbandonRequest setMessageID(int id)
      throws UnsupportedOperationException
  {
    this.messageID = id;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("AbandonRequest(messageID=");
    builder.append(getMessageID());
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }



  AbandonRequest getThis()
  {
    return this;
  }

}
