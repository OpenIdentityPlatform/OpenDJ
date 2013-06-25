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

package org.opends.server.admin.server;



import static org.opends.messages.AdminMessages.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.admin.DecodingException;
import org.opends.server.util.Validator;



/**
 * This exception is thrown when the server refuses to use or delete a
 * managed object due to one or more constraints that cannot be
 * satisfied.
 */
public class ConstraintViolationException extends DecodingException {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -4902443848460011875L;

  // The server managed object.
  private final ServerManagedObject<?> managedObject;



  // Gets the default message.
  private static Message getDefaultMessage(Collection<Message> messages) {
    Validator.ensureNotNull(messages);
    Validator.ensureTrue(!messages.isEmpty());

    if (messages.size() == 1) {
      return ERR_CONSTRAINT_VIOLATION_EXCEPTION_SINGLE.get(messages.iterator()
          .next());
    } else {
      return ERR_CONSTRAINT_VIOLATION_EXCEPTION_PLURAL
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



  /**
   * Creates a new constraint violation exception with the provided
   * messages.
   *
   * @param managedObject
   *          The server managed object which caused the constraint
   *          violations.
   * @param messages
   *          The messages describing the constraint violations that
   *          occurred (must be non-<code>null</code> and
   *          non-empty).
   */
  public ConstraintViolationException(ServerManagedObject<?> managedObject,
      Collection<Message> messages) {
    super(getDefaultMessage(messages));

    this.managedObject = managedObject;
    this.messages = new ArrayList<Message>(messages);
  }



  /**
   * Creates a new constraint violation exception with the provided
   * message.
   *
   * @param managedObject
   *          The server managed object which caused the constraint
   *          violations.
   * @param message
   *          The message describing the constraint violation that
   *          occurred.
   */
  public ConstraintViolationException(ServerManagedObject<?> managedObject,
      Message message) {
    this(managedObject, Collections.singleton(message));
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
   * Gets the server managed object which caused the constraint
   * violations.
   *
   * @return Returns the server managed object which caused the
   *         constraint violations.
   */
  public ServerManagedObject<?> getManagedObject() {
    return managedObject;
  }
}
