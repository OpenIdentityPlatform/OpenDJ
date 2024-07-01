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
 * Contains a number of interfaces that are implemented by the various
 * types of Directory Server operations.  These interfaces are used to
 * define the methods that should be available for use at various
 * times during the operation processing (e.g., what methods are
 * available for pre-parse versus pre-operation versus post-operation
 * versus post-response plugins).
 * <BR><BR>
 * Note that none of the interfaces defined in this package are
 * intended to be implemented by any custom code.  They should be
 * implemented only by the core Directory Server operation types.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED)
package org.opends.server.types.operation;

