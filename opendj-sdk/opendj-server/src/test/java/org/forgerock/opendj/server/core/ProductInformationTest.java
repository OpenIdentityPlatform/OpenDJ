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
 *      Copyright 2014 ForgeRock AS.
 */

package org.forgerock.opendj.server.core;

import static org.fest.assertions.Assertions.assertThat;

import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.Test;

/**
 * This test class verifies that the product information has been generated
 * and can be loaded at runtime. It does not attempt to exhaustively check
 * all methods.
 */
@SuppressWarnings("javadoc")
@Test
public class ProductInformationTest extends ForgeRockTestCase {

    @Test
    public void testProductName() {
        assertThat(ProductInformation.getInstance().productName()).isNotNull();
    }

    @Test
    public void testProductShortName() {
        assertThat(ProductInformation.getInstance().productShortName()).isEqualTo("OpenDJ");
    }

    @Test
    public void testVersionMajor() {
        assertThat(ProductInformation.getInstance().versionMajorNumber()).isGreaterThanOrEqualTo(3);
    }
}
