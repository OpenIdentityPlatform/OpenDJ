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
 * Contains the code for the Directory Server backend that provides an interface
 * for executing administrative tasks.  A task is a set of user-defined code
 * that may be executed either immediately or at a given time in the future by
 * adding an entry within the "cn=tasks" branch.  The attributes of that entry
 * may serve as arguments that can customize the operation of that task.
 * <BR><BR>
 * Recurring tasks may be used to perform an operation at regular intervals.
 * Task groups may define a set of tasks that should be executed in sequential
 * order, and may be executed as a recurring task.  The order of individual
 * scheduled tasks may be controlled by defining dependencies between those
 * tasks, but a task group must be used to achieve the same result for recurring
 * tasks.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.PRIVATE)
package org.opends.server.backends.task;

