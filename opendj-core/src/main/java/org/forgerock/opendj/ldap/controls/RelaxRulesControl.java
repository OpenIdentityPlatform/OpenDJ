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
 * Copyright 2024 3A Systems,LLC.
 */
package org.forgerock.opendj.ldap.controls;

import org.forgerock.opendj.ldap.ByteString;

public class RelaxRulesControl implements Control{

    public final static String OID="1.3.6.1.4.1.4203.666.5.12";

    @Override
    public String getOID() {
        return OID;
    }

    @Override
    public ByteString getValue() {
        return null;
    }

    @Override
    public boolean hasValue() {
        return false;
    }

    @Override
    public boolean isCritical() {
        return true;
    }
}
