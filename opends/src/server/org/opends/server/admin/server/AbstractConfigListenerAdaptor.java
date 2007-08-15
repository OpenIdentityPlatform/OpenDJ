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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.admin.server;
import org.opends.messages.Message;



import java.util.List;

import org.opends.server.admin.DecodingException;

import org.opends.messages.MessageBuilder;


/**
 * Common features of config listener adaptors.
 */
abstract class AbstractConfigListenerAdaptor {

  /**
   * Create a new config listener adaptor.
   */
  protected AbstractConfigListenerAdaptor() {
    // No implementation required.
  }



  /**
   * Convert a decoding exception to an unacceptable reason.
   *
   * @param e
   *          The decoding exception.
   * @param unacceptableReason
   *          The builder to which messages should be appended.
   */
  protected final void generateUnacceptableReason(
      DecodingException e, MessageBuilder unacceptableReason) {
    // FIXME: generate a property OpenDS style message.
    unacceptableReason.append(e.getLocalizedMessage());
  }



  /**
   * Concatenate a list of messages into a single message.
   *
   * @param reasons
   *          The list of messages to concatenate.
   * @param unacceptableReason
   *          The single message to which messages should be appended.
   */
  protected final void generateUnacceptableReason(
      List<Message> reasons, MessageBuilder unacceptableReason) {
    boolean isFirst = true;
    for (Message reason : reasons) {
      if (isFirst) {
        isFirst = false;
      } else {
        unacceptableReason.append("  ");
      }
      unacceptableReason.append(reason);
    }
  }
}
