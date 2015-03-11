/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2015 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

import org.forgerock.i18n.LocalizableMessage;

/**
 * Documentation that supplements generated descriptions.
 */
public interface DocDescriptionSupplement {

    /**
     * Retrieves a supplement to the description intended for use in generated reference documentation.
     *
     * @return The supplement to the description for use in generated reference documentation,
     *         or LocalizableMessage.EMPTY if there is no supplement.
     */
    public LocalizableMessage getDocDescriptionSupplement();

    /**
     * Sets a supplement to the description intended for use in generated reference documentation.
     *
     * @param docDescriptionSupplement  The supplement to the description
     *                                  for use in generated reference documentation.
     */
    public void setDocDescriptionSupplement(final LocalizableMessage docDescriptionSupplement);
}
