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

