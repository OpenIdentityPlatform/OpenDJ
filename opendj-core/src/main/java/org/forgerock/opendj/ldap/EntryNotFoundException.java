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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */

package org.forgerock.opendj.ldap;

import org.forgerock.opendj.ldap.responses.Result;

/**
 * Thrown when the result code returned in a Result indicates that the Request
 * failed because the target entry was not found by the Directory Server. More
 * specifically, this exception is used for the following error result codes:
 * <ul>
 * <li>{@link ResultCode#NO_SUCH_OBJECT NO_SUCH_OBJECT} - the requested
 * operation failed because it referenced an entry that does not exist.
 * <li>{@link ResultCode#REFERRAL REFERRAL} - the requested operation failed
 * because it referenced an entry that is located on another server.
 * <li>{@link ResultCode#CLIENT_SIDE_NO_RESULTS_RETURNED
 * CLIENT_SIDE_NO_RESULTS_RETURNED} - the requested single entry search
 * operation or read operation failed because the Directory Server did not
 * return any matching entries.
 * </ul>
 * <b>NOTE:</b> referrals are handled by the {@link ReferralException}
 * sub-class.
 */
@SuppressWarnings("serial")
public class EntryNotFoundException extends LdapException {
    EntryNotFoundException(final Result result) {
        super(result);
    }
}
