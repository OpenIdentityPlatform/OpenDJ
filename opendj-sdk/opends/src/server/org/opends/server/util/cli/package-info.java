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
 * Provides a high-level framework for implementing command-line
 * tools.
 * <p>
 * The {@link ConsoleApplication} interface can be used as a basis for
 * console based applications. It includes common utility methods for
 * interacting with the console.
 * <p>
 * The {@link MenuBuilder} and associated classes and interfaces can
 * be used to implement text based menu driven applications.
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.PRIVATE)
package org.opends.server.util.cli;



