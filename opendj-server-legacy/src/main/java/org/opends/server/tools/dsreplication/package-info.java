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
 * Copyright 2008 Sun Microsystems, Inc.
 */



/**
 * Defines the  classes that are you used by the replication
 * command lines.  This includes the command line parsers
 * (ReplicationCliParser), the classes that actually execute the configuration
 * operations (ReplicationCliMain), the enumeration that defines the return
 * codes of the command-line (ReplicationCliReturnCode), a particular exception
 * used only for the package (ReplicationCliException) and the different data
 * models that represent the data provided by the user directly as command-line
 * parameters and also interactively.
 * */
package org.opends.server.tools.dsreplication;
