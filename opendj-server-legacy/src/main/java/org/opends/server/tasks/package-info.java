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

