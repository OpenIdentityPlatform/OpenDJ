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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import org.forgerock.opendj.ldap.responses.Result;

/**
 * Thrown when the result code returned in a Result indicates that the update
 * Request failed because it would have left the Directory in an inconsistent
 * state. More specifically, this exception is used for the following error
 * result codes:
 * <ul>
 * <li>{@link ResultCode#ATTRIBUTE_OR_VALUE_EXISTS ATTRIBUTE_OR_VALUE_EXISTS} -
 * the Request failed because it would have resulted in a conflict with an
 * existing attribute or attribute value in the target entry.
 * <li>{@link ResultCode#NO_SUCH_ATTRIBUTE NO_SUCH_ATTRIBUTE} - the Request
 * failed because it targeted an attribute or attribute value that did not exist
 * in the specified entry.
 * <li>{@link ResultCode#CONSTRAINT_VIOLATION CONSTRAINT_VIOLATION} - the
 * Request failed because it would have violated some constraint defined in the
 * server.
 * <li>{@link ResultCode#ENTRY_ALREADY_EXISTS ENTRY_ALREADY_EXISTS} - the
 * Request failed because it would have resulted in an entry that conflicts with
 * an entry that already exists.
 * <li>{@link ResultCode#INVALID_ATTRIBUTE_SYNTAX INVALID_ATTRIBUTE_SYNTAX} -
 * the Request failed because it violated the syntax for a specified attribute.
 * <li>{@link ResultCode#INVALID_DN_SYNTAX INVALID_DN_SYNTAX} - the Request
 * failed because it would have resulted in an entry with an invalid or
 * malformed DN.
 * <li>{@link ResultCode#NAMING_VIOLATION NAMING_VIOLATION} - the Request failed
 * becauseit would have violated the server's naming configuration.
 * <li>{@link ResultCode#NOT_ALLOWED_ON_NONLEAF NOT_ALLOWED_ON_NONLEAF} - the
 * Request failed because it is not allowed for non-leaf entries.
 * <li>{@link ResultCode#NOT_ALLOWED_ON_RDN NOT_ALLOWED_ON_RDN} - the Request
 * failed because it is not allowed on an RDN attribute.
 * <li>{@link ResultCode#OBJECTCLASS_MODS_PROHIBITED
 * OBJECTCLASS_MODS_PROHIBITED} - the Request failed because it would have
 * modified the objectclasses associated with an entry in an illegal manner.
 * <li>{@link ResultCode#OBJECTCLASS_VIOLATION OBJECTCLASS_VIOLATION} - the
 * Request failed because it would have resulted in an entry that violated the
 * server schema.
 * <li>{@link ResultCode#UNDEFINED_ATTRIBUTE_TYPE UNDEFINED_ATTRIBUTE_TYPE} -
 * the Request failed because it referenced an attribute that is not defined in
 * the server schema.
 * </ul>
 */
@SuppressWarnings("serial")
public class ConstraintViolationException extends LdapException {
    ConstraintViolationException(final Result result) {
        super(result);
    }
}
