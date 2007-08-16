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

