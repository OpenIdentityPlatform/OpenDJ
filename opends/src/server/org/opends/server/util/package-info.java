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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */



/**
 * Contains implementations for various Directory Server utility classes and
 * methods which may be used throughout the server but do not necessarily fit in
 * elsewhere.  Notable utility classes include:
 * <BR>
 * <UL>
 *   <LI>
 *     StaticUtils.java -- a set of static methods that may be called to perform
 *     all kinds of useful operations on data (e.g., get a stack trace from an
 *     exception, or convert the contents of a byte array to a readable form).
 *   </LI>
 *   <LI>
 *     Base64.java -- provides a mechanism for performing base64 encoding and
 *     decoding.
 *   </LI>
 *   <LI>
 *     DynamicConstants.java -- a set of constants that are actually generated
 *     automatically at build time and reflect information about the time and
 *     conditions under which the server was built.
 *   </LI>
 *   <LI>
 *     TimeThread.java -- provides a thread that will periodically retrieve
 *     the current time and format it in several common methods in an attempt to
 *     minimize the need to have multiple calls to
 *     <CODE>System.currentTimeMillis()</CODE> or <CODE>new Date()</CODE>
 *     whenever the time is needed.  This thread updates internal variables
 *     several times each second, which should be sufficient for cases in which
 *     high-resolution timing is not required.
 *   </LI>
 * </UL>
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE)
package org.opends.server.util;

