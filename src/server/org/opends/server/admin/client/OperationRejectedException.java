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
   * The type of operation that caused this exception.
   */
  public enum OperationType {
    /**
     * A managed object could not be created.
     */
    CREATE,

    /**
     * A managed object could not be deleted.
     */
    DELETE,

    /**
     * A managed object could not be modified.
     */
    MODIFY;
  }

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 8547688890613079044L;



  // Gets the default message.
  private static Message getDefaultMessage(Collection<Message> messages) {
    Validator.ensureNotNull(messages);
    Validator.ensureTrue(!messages.isEmpty());

    if (messages.size() == 1) {
      return ERR_OPERATION_REJECTED_EXCEPTION_SINGLE.get(messages.iterator()
          .next());
    } else {
      return ERR_OPERATION_REJECTED_EXCEPTION_PLURAL
          .get(getSingleMessage(messages));
    }
  }



  // Merge the messages into a single message.
  private static Message getSingleMessage(Collection<Message> messages) {
    if (messages.size() == 1) {
      return messages.iterator().next();
    } else {
      MessageBuilder builder = new MessageBuilder();

      boolean isFirst = true;
      for (Message m : messages) {
        if (!isFirst) {
          builder.append(";  ");
        }
        builder.append(m);
        isFirst = false;
      }

      return builder.toMessage();
    }
  }

  // The messages describing the constraint violations that occurred.
  private final Collection<Message> messages;

  // The type of operation that caused this exception.
  private final OperationType type;

  // The user friendly name of the component that caused this
  // exception.
  private final Message ufn;



  /**
   * Creates a new operation rejected exception with a default
   * message.
   *
   * @param type
   *          The type of operation that caused this exception.
   * @param ufn
   *          The user friendly name of the component that caused this
   *          exception.
   */
  public OperationRejectedException(OperationType type, Message ufn) {
    this(type, ufn, ERR_OPERATION_REJECTED_DEFAULT.get());
  }



  /**
   * Creates a new operation rejected exception with the provided
   * messages.
   *
   * @param type
   *          The type of operation that caused this exception.
   * @param ufn
   *          The user friendly name of the component that caused this
   *          exception.
   * @param messages
   *          The messages describing the constraint violations that
   *          occurred (must be non-<code>null</code> and
   *          non-empty).
   */
  public OperationRejectedException(OperationType type, Message ufn,
      Collection<Message> messages) {
    super(getDefaultMessage(messages));

    this.messages = new ArrayList<Message>(messages);
    this.type = type;
    this.ufn = ufn;
  }



  /**
   * Creates a new operation rejected exception with the provided
   * message.
   *
   * @param type
   *          The type of operation that caused this exception.
   * @param ufn
   *          The user friendly name of the component that caused this
   *          exception.
   * @param message
   *          The message describing the constraint violation that
   *          occurred.
   */
  public OperationRejectedException(OperationType type, Message ufn,
      Message message) {
    this(type, ufn, Collections.singleton(message));
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



  /**
   * Creates a single message listing all the messages combined into a
   * single list separated by semi-colons.
   *
   * @return Returns a single message listing all the messages
   *         combined into a single list separated by semi-colons.
   */
  public Message getMessagesAsSingleMessage() {
    return getSingleMessage(messages);
  }



  /**
   * Gets the type of operation that caused this exception.
   *
   * @return Returns the type of operation that caused this exception.
   */
  public OperationType getOperationType() {
    return type;
  }



  /**
   * Gets the user friendly name of the component that caused this
   * exception.
   *
   * @return Returns the user friendly name of the component that
   *         caused this exception.
   */
  public Message getUserFriendlyName() {
    return ufn;
  }

}
