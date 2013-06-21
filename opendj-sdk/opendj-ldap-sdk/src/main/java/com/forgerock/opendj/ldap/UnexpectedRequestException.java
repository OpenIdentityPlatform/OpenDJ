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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package com.forgerock.opendj.ldap;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.requests.Request;

/**
 * Thrown when an expected LDAP request is received.
 */
@SuppressWarnings("serial")
final class UnexpectedRequestException extends IOException {
    private final int messageID;
    private final Request request;

    public UnexpectedRequestException(final int messageID, final Request request) {
        super(LocalizableMessage.raw("Unexpected LDAP request: id=%d, message=%s", messageID,
                request).toString());
        this.messageID = messageID;
        this.request = request;
    }

    public int getMessageID() {
        return messageID;
    }

    public Request getRequest() {
        return request;
    }
}
