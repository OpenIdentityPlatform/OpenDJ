/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014 ForgeRock AS.
 */
package org.opends.server.authorization.dseecompat;
import org.forgerock.i18n.LocalizableMessage;

import org.opends.server.types.IdentifiedException;


/**
 * The AciException class defines an exception that may be thrown
 * either during ACI syntax verification of an "aci" attribute type value
 * or during evaluation of an LDAP operation using a set of applicable
 * ACIs.
 */
public class AciException extends IdentifiedException {
  /**
   * The serial version identifier required to satisfy the compiler because this
   * class extends <CODE>java.lang.Exception</CODE>, which implements the
   * <CODE>java.io.Serializable</CODE> interface.  This value was generated
   * using the <CODE>serialver</CODE> command-line utility included with the
   * Java SDK.
   */
  private static final long serialVersionUID = -2763328522960628853L;

    /**
     * Constructs a new exception with <code>null</code> as its detail message.
     * The cause is not initialized. Used to break out of a recursive bind rule
     * decode and not print duplicate messages.
     */
    public AciException() {
      super();
    }

    /**
     * Creates a new ACI exception with the provided message.
     *
     * @param  message    The message to use for this ACI exception.
     */
    public AciException(LocalizableMessage message) {
      super(message);
    }

    /**
     * Creates a new ACI exception with the provided message and root
     * cause.
     *
     * @param  message    The message that explains the problem that occurred.
     * @param  cause      The exception that was caught to trigger this
     *                    exception.
     */
    public AciException(LocalizableMessage message, Throwable cause) {
      super(message, cause);
    }

}
