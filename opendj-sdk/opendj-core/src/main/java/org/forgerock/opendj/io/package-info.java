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
 * Copyright 2013 ForgeRock AS.
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

