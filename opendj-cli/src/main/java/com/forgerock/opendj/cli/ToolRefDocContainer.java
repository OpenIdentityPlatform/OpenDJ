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

import org.forgerock.i18n.LocalizableMessage;

/**
 * An interface for an object that holds reference documentation for a command-line tool.
 */
public interface ToolRefDocContainer {

    /**
     * Gets a short description for this tool, suitable in a man page summary line.
     *
     * @return  A short description for this tool,
     *          suitable in a man page summary line,
     *          or LocalizableMessage.EMPTY if there is no short description.
     */
    LocalizableMessage getShortToolDescription();

    /**
     * Sets a short description for this tool, suitable in a man page summary line.
     *
     * @param   shortDescription    The short description for this tool,
     *                              suitable in a man page summary line.
     */
    void setShortToolDescription(final LocalizableMessage shortDescription);

    /**
     * Retrieves a supplement to the description for this tool,
     * intended for use in generated reference documentation.
     *
     * @return A supplement to the description for this tool
     *         intended for use in generated reference documentation,
     *         or LocalizableMessage.EMPTY if there is no supplement.
     */
    LocalizableMessage getDocToolDescriptionSupplement();

    /**
     * Sets a supplement to the description for this tool,
     * intended for use in generated reference documentation.
     *
     * @param docToolDescriptionSupplement  The supplement to the description for this tool
     *                                      intended for use in generated reference documentation.
     */
    void setDocToolDescriptionSupplement(final LocalizableMessage docToolDescriptionSupplement);

    /**
     * Retrieves a supplement to the description for all subcommands of this tool,
     * intended for use in generated reference documentation.
     *
     * @return A supplement to the description for all subcommands of this tool
     *         intended for use in generated reference documentation,
     *         or LocalizableMessage.EMPTY if there is no supplement.
     */
    LocalizableMessage getDocSubcommandsDescriptionSupplement();

    /**
     * Sets a supplement to the description for all subcommands of this tool,
     * intended for use in generated reference documentation.
     *
     * @param docSubcommandsDescriptionSupplement
     *          The supplement to the description for all subcommands of this tool
     *          intended for use in generated reference documentation.
     */
    void setDocSubcommandsDescriptionSupplement(final LocalizableMessage docSubcommandsDescriptionSupplement);
}
