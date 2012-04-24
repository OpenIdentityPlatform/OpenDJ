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
import org.forgerock.opendj.ldap.ByteString;

/**
 * Thrown when an unsupported LDAP message is received.
 */
@SuppressWarnings("serial")
final class UnsupportedMessageException extends IOException {
    private final int id;
    private final byte tag;
    private final ByteString content;

    public UnsupportedMessageException(final int id, final byte tag, final ByteString content) {
        super(LocalizableMessage.raw("Unsupported LDAP message: id=%d, tag=%d, content=%s", id,
                tag, content).toString());
        this.id = id;
        this.tag = tag;
        this.content = content;
    }

    public ByteString getContent() {
        return content;
    }

    public int getID() {
        return id;
    }

    public byte getTag() {
        return tag;
    }
}
