/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */

package org.opends.server.admin.server;



import static org.opends.messages.AdminMessages.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.server.admin.DecodingException;
import org.forgerock.util.Reject;



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

  /** The server managed object. */
  private final ServerManagedObject<?> managedObject;



  /** Gets the default message. */
  private static LocalizableMessage getDefaultMessage(Collection<LocalizableMessage> messages) {
    Reject.ifNull(messages);
    Reject.ifFalse(!messages.isEmpty());

    if (messages.size() == 1) {
      return ERR_CONSTRAINT_VIOLATION_EXCEPTION_SINGLE.get(messages.iterator()
          .next());
    } else {
      return ERR_CONSTRAINT_VIOLATION_EXCEPTION_PLURAL
          .get(getSingleMessage(messages));
    }
  }



  /** Merge the messages into a single message. */
  private static LocalizableMessage getSingleMessage(Collection<LocalizableMessage> messages) {
    if (messages.size() == 1) {
      return messages.iterator().next();
    } else {
      LocalizableMessageBuilder builder = new LocalizableMessageBuilder();

      boolean isFirst = true;
      for (LocalizableMessage m : messages) {
        if (!isFirst) {
          builder.append(";  ");
        }
        builder.append(m);
        isFirst = false;
      }

      return builder.toMessage();
    }
  }

  /** The messages describing the constraint violations that occurred. */
  private final Collection<LocalizableMessage> messages;



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
      Collection<LocalizableMessage> messages) {
    super(getDefaultMessage(messages));

    this.managedObject = managedObject;
    this.messages = new ArrayList<>(messages);
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
      LocalizableMessage message) {
    this(managedObject, Collections.singleton(message));
  }



  /**
   * Gets an unmodifiable collection view of the messages describing
   * the constraint violations that occurred.
   *
   * @return Returns an unmodifiable collection view of the messages
   *         describing the constraint violations that occurred.
   */
  public Collection<LocalizableMessage> getMessages() {
    return Collections.unmodifiableCollection(messages);
  }



  /**
   * Creates a single message listing all the messages combined into a
   * single list separated by semi-colons.
   *
   * @return Returns a single message listing all the messages
   *         combined into a single list separated by semi-colons.
   */
  public LocalizableMessage getMessagesAsSingleMessage() {
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
