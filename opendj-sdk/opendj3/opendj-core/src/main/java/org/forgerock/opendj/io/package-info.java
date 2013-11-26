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
 *      Copyright 2013 ForgeRock AS.
 */

/**
 * Classes and interfaces providing I/O functionality.
 * <p>
 * It includes facilities for encoding and decoding ASN.1 data streams, as
 * well as LDAP protocol messages.
 * <p>
 * Note that this particular implementation is limited to the subset of elements
 * that are typically used by LDAP clients. As such, it does not include all
 * ASN.1 element types, particularly elements like OIDs, bit strings, and
 * timestamp values.
 */
package org.forgerock.opendj.io;

