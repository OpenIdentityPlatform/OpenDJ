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
 * Contains code used to interact with the Directory Server configuration.  This
 * includes various kinds of configuration attributes for different data types,
 * as well as the primary means of exposing configuration and operations over
 * JMX.  Further, it includes the code for the default configuration handler
 * which stores information in an LDIF file.
 * <BR><BR>
 * Whenever a configuration entry is modified, assuming that the result of the
 * modification is in conformance with the server schema, then any configurable
 * components and configuration change listeners associated with that entry will
 * be invoked and given an opportunity to reject that update if there is a
 * problem with it, or dynamically react to it by applying the new
 * configuration.  If a new configuration entry is added, then any configuration
 * add listeners associated with that entry's parent will be invoked.  If an
 * existing entry is removed, then any configuration delete listeners associated
 * with that entry's parent will be invoked.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE)
package org.opends.server.config;

