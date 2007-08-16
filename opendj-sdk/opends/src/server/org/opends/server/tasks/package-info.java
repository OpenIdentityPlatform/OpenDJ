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
 * Contains implementations for various Directory Server tasks.  Tasks define
 * administrative operations that can be invoked by adding an entry into the
 * tasks backend.  Tasks may be scheduled to run immediately or at a specified
 * time in the future, and tasks may also be invoked on a recurring basis.
 * Tasks currently defined include those for exporting and importing backend
 * contents to and from LDIF, backing up and restoring backend contents,
 * stopping and restarting the Directory Server, and adding new files into the
 * server schema.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.PRIVATE)
package org.opends.server.tasks;

