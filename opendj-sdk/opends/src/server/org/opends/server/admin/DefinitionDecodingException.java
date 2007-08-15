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

package org.opends.server.admin;
import org.opends.messages.Message;


/**
 * The requested managed object was found but its type could not be
 * determined.
 */
public class DefinitionDecodingException extends DecodingException {

  /**
   * Version ID required by serializable classes.
   */
  private static final long serialVersionUID = 3459033551415663416L;



  /**
   * An enumeration defining the reasons why the definition could not be
   * resolved.
   */
  public static enum Reason {
    /**
     * The managed object could be found but did not contain any type
     * information (eg missing object classes in LDAP).
     */
    NO_TYPE_INFORMATION(Message.raw( // TODO: i18n?
        "The managed object could be found but did not contain any"
            + " type information (e.g. missing object classes in LDAP).")),

    /**
     * The managed object could be found but did not contain the expected type
     * information (eg incorrect object classes in LDAP).
     */
    WRONG_TYPE_INFORMATION(Message.raw( // TODO: i18n?
        "The managed object could be found but did not contain the"
            + " expected type information (e.g. incorrect object"
            + " classes in LDAP).")),

    /**
     * The managed object could be found but its type resolved to an abstract
     * managed object definition.
     */
    ABSTRACT_TYPE_INFORMATION(Message.raw( // TODO: i18n?
        "The managed object could be found but its type resolved to an"
            + " abstract managed object definition."));

    // Simple description of this reason for debugging.
    private Message msg;



    // Private constructor.
    private Reason(Message msg) {
      this.msg = msg;
    }

  }



  // The reason why the definition could not be determined.
  private final Reason reason;



  /**
   * Create a new definition decoding exception.
   *
   * @param reason
   *          The reason why the definition could not be determined.
   */
  public DefinitionDecodingException(Reason reason) {
    this.reason = reason;
  }



  /**
   * Get the reason why the definition could not be determined.
   *
   * @return Returns the reason why the definition could not be determined.
   */
  public Reason getReason() {
    return reason;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Message getMessageObject() {
    return reason.msg;
  }

}
