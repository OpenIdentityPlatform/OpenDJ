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

package org.opends.server.admin.client;



import static org.opends.messages.AdminMessages.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.util.Validator;



/**
 * This exception is thrown when the client or server refuses to
 * create, delete, or modify a managed object due to one or more
 * constraints that cannot be satisfied.
 * <p>
 * Operations can be rejected either by a client-side constraint
 * violation triggered by {@link ClientConstraintHandler}, or by a
 * server-side error.
 * <p>
 * For example, the Directory Server might not be able perform an
 * operation due to some OS related problem, such as lack of disk
 * space, or missing files.
 */
public class OperationRejectedException extends AdminClientException {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 8547688890613079044L;



  // Merge the messages into a single message.
  private static Message getSingleMessage(Collection<Message> messages) {
    Validator.ensureNotNull(messages);
    Validator.ensureTrue(!messages.isEmpty());

    MessageBuilder builder = new MessageBuilder();

    boolean isFirst = true;
    for (Message m : messages) {
      if (!isFirst) {
        builder.append("; ");
      }
      builder.append(m);
      isFirst = false;
    }

    return builder.toMessage();
  }

  // The messages describing the constraint violations that occurred.
  private final Collection<Message> messages;



  /**
   * Creates a new operation rejected exception with the provided
   * messages.
   *
   * @param messages
   *          The messages describing the constraint violations that
   *          occurred (must be non-<code>null</code> and
   *          non-empty).
   */
  public OperationRejectedException(Collection<Message> messages) {
    super(getSingleMessage(messages));

    this.messages = new ArrayList<Message>(messages);
  }



  /**
   * Creates a new operation rejected exception with the provided
   * message.
   *
   * @param message
   *          The message describing the constraint violation that
   *          occurred (must be non-<code>null</code> and
   *          non-empty).
   */
  public OperationRejectedException(Message message) {
    this(Collections.singleton(message));
  }



  /**
   * Creates a new operation rejected exception with a default
   * message.
   */
  public OperationRejectedException() {
    this(ERR_OPERATION_REJECTED_DEFAULT.get());
  }



  /**
   * Gets an unmodifiable collection view of the messages describing
   * the constraint violations that occurred.
   *
   * @return Returns an unmodifiable collection view of the messages
   *         describing the constraint violations that occurred.
   */
  public Collection<Message> getMessages() {
    return Collections.unmodifiableCollection(messages);
  }

}
