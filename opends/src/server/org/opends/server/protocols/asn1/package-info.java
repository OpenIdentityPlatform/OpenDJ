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
 * Contains the Directory Server classes for performing encoding and decoding of
 * ASN.1 elements.  This is not specifically a protocol by itself, but it is
 * used in several places throughout the Directory Server in areas like client
 * communication and internal data encoding, and technically ASN.1 elements are
 * considered to be Directory Server protocol elements.
 * <BR><BR>
 * Note that this particular implementation is limited to the subset of elements
 * that are typically used by LDAP clients.  As such, it does not include all
 * ASN.1 element types, particularly elements like OIDs, bit strings, and
 * timestamp values.
 * <BR><BR>
 * Also note that the contents of this package alone are not sufficient for a
 * highly-performant and scalable LDAP implementation.  The process of reading
 * the outermost ASN.1 sequence that comprises the LDAPMessage envelope will
 * best be done within the implementation for the LDAP connection handler.
 */
package org.opends.server.protocols.asn1;

