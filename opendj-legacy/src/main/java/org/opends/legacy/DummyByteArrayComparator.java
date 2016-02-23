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
 * Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.legacy;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Dummy implementation for a byte array comparator.
 * This allows to quickly reimplement deleted matching rule classes for upgrade
 * and rebuild-index with Berkeley JE.
 *
 * @since OPENDJ-1637 Upgrade: server fails to start after upgrade (ClassNotFoundException)
 * @deprecated Do not use
 */
@Deprecated
public class DummyByteArrayComparator implements Comparator<byte[]>, Serializable {
    @Override
    public int compare(byte[] o1, byte[] o2) {
        return 0;
    }
}
