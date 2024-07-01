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
package com.forgerock.opendj.cli;

/**
 * A handler for printing sub-command usage information.
 */
//@FunctionalInterface
public interface SubCommandUsageHandler {

    /**
     * Returns properties information for the sub-command.
     *
     * @param subCommand
     *          the sub command for which to print usage information
     * @return  The properties information for the sub-command.
     */
    String getProperties(SubCommand subCommand);

    /**
     * Returns additional information for the provided sub-command argument.
     *
     * @param subCommand
     *          the sub command for which to print usage information
     * @param arg
     *          the argument for which to append additional information
     * @param nameOption
     *          the string representing the name option
     * @return  The additional information for the sub-command argument.
     */
    String getArgumentAdditionalInfo(SubCommand subCommand, Argument arg, String nameOption);

}
