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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.server.admin.DecodingException;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.PropertyException;
import org.forgerock.util.Reject;



/**
 * The requested server managed object was found but one or more of
 * its properties could not be decoded successfully.
 */
public class ServerManagedObjectDecodingException extends DecodingException {

  /**
   * Version ID required by serializable classes.
   */
  private static final long serialVersionUID = 1598401431084729853L;



  /** Create the message. */
  private static LocalizableMessage createMessage(
      ServerManagedObject<?> partialManagedObject,
      Collection<PropertyException> causes) {
    Reject.ifNull(causes);
    Reject.ifFalse(!causes.isEmpty());

    ManagedObjectDefinition<?, ?> d = partialManagedObject
        .getManagedObjectDefinition();
    if (causes.size() == 1) {
      return ERR_MANAGED_OBJECT_DECODING_EXCEPTION_SINGLE.get(d
          .getUserFriendlyName(), causes.iterator().next().getMessageObject());
    } else {
      LocalizableMessageBuilder builder = new LocalizableMessageBuilder();

      boolean isFirst = true;
      for (PropertyException cause : causes) {
        if (!isFirst) {
          builder.append("; ");
        }
        builder.append(cause.getMessageObject());
        isFirst = false;
      }

      return ERR_MANAGED_OBJECT_DECODING_EXCEPTION_PLURAL.get(d
          .getUserFriendlyName(), builder.toMessage());
    }
  }

  /** The exception(s) that caused this decoding exception. */
  private final Collection<PropertyException> causes;

  /** The partially created server managed object. */
  private final ServerManagedObject<?> partialManagedObject;



  /**
   * Create a new property decoding exception.
   *
   * @param partialManagedObject
   *          The partially created server managed object containing
   *          properties which were successfully decoded and empty
   *          properties for those which were not (this may include
   *          empty mandatory properties).
   * @param causes
   *          The exception(s) that caused this decoding exception.
   */
  public ServerManagedObjectDecodingException(
      ServerManagedObject<?> partialManagedObject,
      Collection<PropertyException> causes) {
    super(createMessage(partialManagedObject, causes));

    this.partialManagedObject = partialManagedObject;
    this.causes = Collections
        .unmodifiableList(new LinkedList<PropertyException>(causes));
  }



  /**
   * Get an unmodifiable collection view of the causes of this
   * exception.
   *
   * @return Returns an unmodifiable collection view of the causes of
   *         this exception.
   */
  public Collection<PropertyException> getCauses() {
    return causes;
  }



  /**
   * Get the partially created server managed object containing
   * properties which were successfully decoded and empty properties
   * for those which were not (this may include empty mandatory
   * properties).
   *
   * @return Returns the partially created server managed object
   *         containing properties which were successfully decoded and
   *         empty properties for those which were not (this may
   *         include empty mandatory properties).
   */
  public ServerManagedObject<?> getPartialManagedObject() {
    return partialManagedObject;
  }

}
