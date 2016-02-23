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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 */



/**
 * Defines all the messages that may be used in the Directory Server and tools.
 * Localizable message format strings are stored in properties files and are
 * used to generate <code>MessageDescriptor</code>s at build time.  Descriptors
 * sepcify a category, severity, and ordinal value that is unique to other
 * messages of the same category.  For the server in particular, these
 * three values form an ID that is unique among all messages in OpenDS system.
 * Descriptors also specify a key that is used to access the locale-sensitive
 * format string from the property file that may also contain argument
 * specifiers that are used to parameterize messages according to the rules of
 * <code>java.util.Formatter</code>.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE)
package org.opends.messages;

