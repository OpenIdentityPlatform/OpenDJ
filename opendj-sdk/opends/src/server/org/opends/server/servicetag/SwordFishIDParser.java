/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.servicetag;

import java.io.IOException;
import java.net.URL;
import java.util.Properties;

import static org.opends.server.servicetag.ServiceTagDefinition.*;

/**
 * SwordFishIDParser parses a properties file where product's properties
 * are
 * <LI><B>org.opends.server.servicetag.productname</B></LI>
 * <LI><B>org.opends.server.servicetag.version</B></LI>
 * <LI><B>org.opends.server.servicetag.uuid</B></LI>
 * <LI><B>org.opends.server.servicetag.parent</B></LI>
 * <LI><B>org.opends.server.servicetag.parenturn</B></LI>
 * <LI><B>org.opends.server.servicetag.vendor</B></LI>.
 */
public class SwordFishIDParser {

    // List of properties
    private Properties properties;    // File propeties url to load
    private final URL url;    // Properties names

    /**
     * Creates a parser  for the properties file.
     * @param url to the properties file.
     * @throws java.io.IOException if the stream could not be opened.
     */
    public SwordFishIDParser(URL url) throws IOException {
        this.url = url;
        this.properties = new Properties();
        this.properties.load(url.openStream());
    }

    /**
     * Gets the UUID defined in the properties file.
     * @return the swordfish id.
     */
    public String getSwordFishID() {
        return properties.getProperty(PRODUCT_UUID);
    }

    /**
     * Gets the product name defined in the properties file.
     * @return the product name.
     */
    public String getProductName() {
        return properties.getProperty(PRODUCT_NAME);
    }

    /**
     * Gets the version defined in the properties file.
     * @return the version.
     */
    public String getProductVersion() {
        return properties.getProperty(PRODUCT_VERSION);
    }

    /**
     * Gets the vendor defined in the properties file.
     * @return the product vendor.
     */
    public String getProductVendor() {
        return properties.getProperty(PRODUCT_VENDOR);
    }

    /**
     * Gets the parent product family defined in the properties file.
     * @return the parent family name.
     */
    public String getProductParent() {
        return properties.getProperty(PRODUCT_PARENT);
    }

    /**
     * Gets the UUID of the parent family defined in the properties file.
     * @return the UUID of the product's family.
     */
    public String getProductParentUrn() {
        return properties.getProperty(PRODUCT_PARENT_URN);
    }

    /**
     * Gets the defined properties in a properties object.
     * @return the properties object.
     */
    public Properties getProperties() {
        return this.properties;
    }

}
