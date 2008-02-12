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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
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

