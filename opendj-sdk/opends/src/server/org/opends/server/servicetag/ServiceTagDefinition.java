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

/**
 * Definitions of ServiceTag properties, fields and values.
 */
public class ServiceTagDefinition {

    /**
     * instance_urn tag field name in the servicetag dtd.
     */
    public final static String ST_NODE_INSTANCE_URN = "instance_urn";
    /**
     * product_name tag field name in the servicetag dtd.
     */
    public final static String ST_NODE_PRODUCT_NAME = "product_name";
    /**
     * product_version tag field name in the servicetag dtd.
     */
    public final static String ST_NODE_PRODUCT_VERSION = "product_version";
    /**
     * product_urn tag field name in the servicetag dtd.
     */
    public final static String ST_NODE_PRODUCT_URN = "product_urn";
    /**
     * product_parent_urn tag field name in the servicetag dtd.
     */
    public final static String ST_NODE_PRODUCT_PARENT_URN
            = "product_parent_urn";
    /**
     * product_parent tag field name in the servicetag dtd.
     */
    public final static String ST_NODE_PRODUCT_PARENT = "product_parent";
    /**
     * product_defined_inst_id tag field name in the servicetag dtd.
     */
    public final static String ST_NODE_PRODUCT_DEFINED_INST_ID
            = "product_defined_inst_id";
    /**
     * product_vendor tag field name in the servicetag dtd.
     */
    public final static String ST_NODE_PRODUCT_VENDOR = "product_vendor";
    /**
     * platform_arch tag field name in the servicetag dtd.
     */
    public final static String ST_NODE_PLATFORM_ARCH = "platform_arch";
    /**
     * timestamp tag field name in the servicetag dtd.
     */
    public final static String ST_NODE_TIMESTAMP = "timestamp";
    /**
     * container tag field name in the servicetag dtd.
     */
    final static String ST_NODE_CONTAINER = "container";
    /**
     * tag field name in the servicetag dtd.
     */
    final static String ST_NODE_SOURCE = "source";
    /**
     * tag field name in the servicetag dtd.
     */
    final static String ST_NODE_INSTALLER_UID = "installer_uid";
    /**
     * Product container value.
     */
    final static String PRODUCT_CONTAINER = "Global";
    /**
     * Product vendor property.
     */
    final static String PRODUCT_VENDOR = "org.opends.server.servicetag.vendor";
    /**
     * Product name property.
     */
    final static String PRODUCT_NAME =
            "org.opends.server.servicetag.productname";
    /**
     * Product version property.
     */
    final static String PRODUCT_VERSION =
            "org.opends.server.servicetag.version";
    /**
     * Product uuid property.
     */
    final static String PRODUCT_UUID =
            "org.opends.server.servicetag.uuid";
    /**
     * Product parent property.
     */
    final static String PRODUCT_PARENT =
            "org.opends.server.servicetag.parent";
    /**
     * Product parent urn property.
     */
    final static String PRODUCT_PARENT_URN =
            "org.opends.server.servicetag.parenturn";
}
