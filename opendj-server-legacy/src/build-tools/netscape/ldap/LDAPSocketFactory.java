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
 *      Portions Copyright 2015 ForgeRock AS
 */
package netscape.ldap;



import java.net.Socket;



/**
 * This class provides an empty definition of the
 * {{netscape.ldap.LDAPSocketFactory}} interface, which is part of the Mozilla
 * LDAP SDK for Java.  It is provided to allow the
 * {{org.opends.server.protocols.internal.InternalMozillaLDAPSocketFactory}}
 * class to compile without creating a dependency on the full Mozilla LDAP SDK
 * for Java.
 * <BR><BR>
 * Note that we do not intend to distribute the Mozilla LDAP SDK for Java with
 * OpenDS, or do we depend on it in any way.  Any third-party applications which
 * intend to use the
 * {{org.opends.server.protocols.internal.InternalMozillaLDAPSocketFactory}}
 * class will be required to provide the Mozilla LDAP SDK for Java library.
 */
public interface LDAPSocketFactory
{
  Socket makeSocket(String host, int port);
}

