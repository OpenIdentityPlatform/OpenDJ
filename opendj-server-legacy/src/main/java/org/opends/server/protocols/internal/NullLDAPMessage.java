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
 */
package org.opends.server.protocols.internal;



import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.UnbindRequestProtocolOp;



/**
 * This class provides a special implementation of an LDAP message
 * that will serve as a marker in the internal LDAP input stream
 * message queue that the end of the stream has been reached.  It
 * should not be used for any other purpose.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.PRIVATE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=false)
final class NullLDAPMessage
       extends LDAPMessage
{
  /**
   * Creates a new null LDAP message.
   */
  NullLDAPMessage()
  {
    super(1, new UnbindRequestProtocolOp());
  }
}

