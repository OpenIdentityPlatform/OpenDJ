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
 *       Copyright 2014 ForgeRock AS.
 */

package org.forgerock.opendj.server.core;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.MissingResourceException;
import java.util.Properties;

/**
 * Product information, including version information.
 */
public final class ProductInformation {
    private static final ProductInformation DEFAULT = new ProductInformation("opendj");
    private final Properties properties;

    /**
     * Returns the singleton product information instance.
     *
     * @return The singleton product information instance.
     */
    public static ProductInformation getInstance() {
        return DEFAULT;
    }

    private ProductInformation(String productName) {
        // Load the resource file.
        String resourceName = "/META-INF/product/" + productName + ".properties";
        InputStream stream = getClass().getResourceAsStream(resourceName);

        if (stream == null) {
            throw new MissingResourceException("Can't find product information " + resourceName,
                    productName, "");
        }

        properties = new Properties();
        try {
            properties.load(new BufferedInputStream(stream));
        } catch (IOException e) {
            throw new MissingResourceException("Can't load product information " + resourceName
                    + " due to IO exception: " + e.getMessage(), productName, "");
        }
    }

    /**
     * Returns the short product name for the Directory Server.
     *
     * @return The short product name for the Directory Server.
     */
    public String productShortName() {
        return properties.getProperty("product.name.short");
    }

    /**
     * Returns the official full product name for the Directory Server.
     *
     * @return The official full product name for the Directory Server.
     */
    public String productName() {
        return properties.getProperty("product.name");
    }

}
