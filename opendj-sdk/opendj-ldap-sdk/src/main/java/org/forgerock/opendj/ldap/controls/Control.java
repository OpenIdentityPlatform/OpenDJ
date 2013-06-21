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
 */
package org.forgerock.opendj.ldap.controls;

import org.forgerock.opendj.ldap.ByteString;

/**
 * Controls provide a mechanism whereby the semantics and arguments of existing
 * LDAP operations may be extended. One or more controls may be attached to a
 * single LDAP message. A control only affects the semantics of the message it
 * is attached to. Controls sent by clients are termed 'request controls', and
 * those sent by servers are termed 'response controls'.
 * <p>
 * To determine whether a directory server supports a given control, read the
 * list of supported controls from the root DSE to get a collection of control
 * OIDs, and then check for a match:
 *
 * <pre>
 * Connection connection = ...;
 * Collection&lt;String&gt; supported =
 *     RootDSE.readRootDSE(connection).getSupportedControls();
 *
 * Control control = ...;
 * String OID = control.getOID();
 * if (supported != null && !supported.isEmpty() && supported.contains(OID)) {
 *     // The control is supported. Use it here...
 * }
 * </pre>
 *
 * @see <a href="http://tools.ietf.org/html/rfc4511">RFC 4511 - Lightweight
 *      Directory Access Protocol (LDAP): The Protocol </a>
 */
public interface Control {

    /**
     * Returns the numeric OID associated with this control.
     *
     * @return The numeric OID associated with this control.
     */
    String getOID();

    /**
     * Returns the value, if any, associated with this control. Its format is
     * defined by the specification of this control.
     *
     * @return The value associated with this control, or {@code null} if there
     *         is no value.
     */
    ByteString getValue();

    /**
     * Returns {@code true} if this control has a value. In some circumstances
     * it may be useful to determine if a control has a value, without actually
     * calculating the value and incurring any performance costs.
     *
     * @return {@code true} if this control has a value, or {@code false} if
     *         there is no value.
     */
    boolean hasValue();

    /**
     * Returns {@code true} if it is unacceptable to perform the operation
     * without applying the semantics of this control.
     * <p>
     * The criticality field only has meaning in controls attached to request
     * messages (except UnbindRequest). For controls attached to response
     * messages and the UnbindRequest, the criticality field SHOULD be
     * {@code false}, and MUST be ignored by the receiving protocol peer. A
     * value of {@code true} indicates that it is unacceptable to perform the
     * operation without applying the semantics of the control.
     *
     * @return {@code true} if this control must be processed by the Directory
     *         Server, or {@code false} if it can be ignored.
     */
    boolean isCritical();

}
