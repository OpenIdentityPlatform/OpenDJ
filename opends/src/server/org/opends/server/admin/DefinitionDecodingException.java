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

package org.opends.server.admin;



import static org.opends.messages.AdminMessages.*;

import org.opends.messages.Message;



/**
 * The requested managed object was found but its type could not be
 * determined.
 */
public class DefinitionDecodingException extends DecodingException {

  /**
   * An enumeration defining the reasons why the definition could not
   * be resolved.
   */
  public static enum Reason {
    /**
     * The managed object could be found but its type resolved to an
     * abstract managed object definition.
     */
    ABSTRACT_TYPE_INFORMATION(),

    /**
     * The managed object could be found but did not contain any type
     * information (eg missing object classes in LDAP).
     */
    NO_TYPE_INFORMATION(),

    /**
     * The managed object could be found but did not contain the
     * expected type information (eg incorrect object classes in
     * LDAP).
     */
    WRONG_TYPE_INFORMATION();

  }

  /**
   * Version ID required by serializable classes.
   */
  private static final long serialVersionUID = 3459033551415663416L;



  // Create the message.
  private static Message createMessage(AbstractManagedObjectDefinition<?, ?> d,
      Reason reason) {
    Message ufn = d.getUserFriendlyName();
    switch (reason) {
    case NO_TYPE_INFORMATION:
      return ERR_DECODING_EXCEPTION_NO_TYPE_INFO.get(ufn);
    case WRONG_TYPE_INFORMATION:
      return ERR_DECODING_EXCEPTION_WRONG_TYPE_INFO.get(ufn);
    default:
      return ERR_DECODING_EXCEPTION_ABSTRACT_TYPE_INFO.get(ufn);
    }
  }

  // The expected type of managed object.
  private final AbstractManagedObjectDefinition<?, ?> d;

  // The reason why the definition could not be determined.
  private final Reason reason;



  /**
   * Create a new definition decoding exception.
   *
   * @param d
   *          The expected type of managed object.
   * @param reason
   *          The reason why the definition could not be determined.
   */
  public DefinitionDecodingException(AbstractManagedObjectDefinition<?, ?> d,
      Reason reason) {
    super(createMessage(d, reason));
    this.d = d;
    this.reason = reason;
  }



  /**
   * Gets the expected managed object definition.
   *
   * @return Returns the expected managed object definition.
   */
  public AbstractManagedObjectDefinition<?, ?> getManagedObjectDefinition() {
    return d;
  }



  /**
   * Gets the reason why the definition could not be determined.
   *
   * @return Returns the reason why the definition could not be
   *         determined.
   */
  public Reason getReason() {
    return reason;
  }
}
