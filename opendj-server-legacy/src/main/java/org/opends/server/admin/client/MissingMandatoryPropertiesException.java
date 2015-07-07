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

package org.opends.server.admin.client;



import static org.opends.messages.AdminMessages.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.server.admin.OperationsException;
import org.opends.server.admin.PropertyException;
import org.forgerock.util.Reject;



/**
 * This exception is thrown when an attempt is made to add or modify a
 * managed object when one or more of its mandatory properties are
 * undefined.
 */
public class MissingMandatoryPropertiesException extends OperationsException {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 6342522125252055588L;



  /** Create the message. */
  private static LocalizableMessage createMessage(Collection<PropertyException> causes)
  {
    Reject.ifNull(causes);
    Reject.ifFalse(!causes.isEmpty());

    if (causes.size() == 1) {
      return ERR_MISSING_MANDATORY_PROPERTIES_EXCEPTION_SINGLE.get(causes
          .iterator().next().getPropertyDefinition().getName());
    } else {
      LocalizableMessageBuilder builder = new LocalizableMessageBuilder();

      boolean isFirst = true;
      for (PropertyException cause : causes) {
        if (!isFirst) {
          builder.append(", ");
        }
        builder.append(cause.getPropertyDefinition().getName());
        isFirst = false;
      }

      return ERR_MISSING_MANDATORY_PROPERTIES_EXCEPTION_PLURAL.get(builder
          .toMessage());
    }
  }

  /** The causes of this exception. */
  private final Collection<PropertyException> causes;

  /** Indicates whether the exception occurred during managed object creation. */
  private final boolean isCreate;

  /** The user friendly name of the component that caused this exception. */
  private final LocalizableMessage ufn;



  /**
   * Creates a new missing mandatory properties exception with the
   * provided causes.
   *
   * @param ufn
   *          The user friendly name of the component that caused this
   *          exception.
   * @param causes
   *          The causes of this exception (must be non-<code>null</code>
   *          and non-empty).
   * @param isCreate
   *          Indicates whether the exception occurred during managed
   *          object creation.
   */
  public MissingMandatoryPropertiesException(LocalizableMessage ufn,
      Collection<PropertyException> causes, boolean isCreate) {
    super(createMessage(causes));

    this.causes = new ArrayList<>(causes);
    this.ufn = ufn;
    this.isCreate = isCreate;
  }



  /**
   * Gets the first exception that caused this exception.
   *
   * @return Returns the first exception that caused this exception.
   */
  @Override
  public PropertyException getCause() {
    return causes.iterator().next();
  }



  /**
   * Gets an unmodifiable collection view of the causes of this
   * exception.
   *
   * @return Returns an unmodifiable collection view of the causes of
   *         this exception.
   */
  public Collection<PropertyException> getCauses() {
    return Collections.unmodifiableCollection(causes);
  }



  /**
   * Gets the user friendly name of the component that caused this
   * exception.
   *
   * @return Returns the user friendly name of the component that
   *         caused this exception.
   */
  public LocalizableMessage getUserFriendlyName() {
    return ufn;
  }



  /**
   * Indicates whether or not this exception was thrown during managed
   * object creation or during modification.
   *
   * @return Returns <code>true</code> if this exception was thrown
   *         during managed object creation.
   */
  public boolean isCreate() {
    return isCreate;
  }

}
