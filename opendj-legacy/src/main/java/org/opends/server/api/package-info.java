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
 * Copyright 2015 ForgeRock AS.
 */

/**
 * Contains a number of API declarations for use throughout the
 * Directory Server.  Whenever possible, these APIs should be declared
 * using abstract classes rather than interfaces so that they can be
 * altered in future versions without impacting backward
 * compatibility.
 * <BR><BR>
 * Note that the mere existence of a class or interface in this
 * package does not in itself imply that it is intended for use by
 * third party code.  Please refer to the official product
 * documentation to indicate which APIs may be safely used by anyone
 * other than the core Directory Server developers.  Failure to heed
 * this warning may result in code that could have unintended side
 * effects or that does not work properly across different Directory
 * Server versions.
 */
package org.opends.server.api;

