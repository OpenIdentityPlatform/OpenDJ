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
 * Contains implementations of various Directory Server schema elements.  In
 * particular, this holds the implementations of all the attribute syntaxes and
 * equality/substring/ordering/approximate matching rules that have been defined
 * in the server.  Other schema elements (e.g., attribute types, objectclasses,
 * name forms, matching rule uses, DIT content rules, and DIT structure rules)
 * do not require any specialized processing and therefore implementations of
 * those more generic objects are defined elsewhere in the Directory Server
 * codebase.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.PRIVATE)
package org.opends.server.schema;

