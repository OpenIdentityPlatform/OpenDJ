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
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.legacy;

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
public class DummyByteArrayComparator implements Comparator<byte[]> {

    /** {@inheritDoc} */
    @Override
    public int compare(byte[] o1, byte[] o2) {
        return 0;
    }

}
