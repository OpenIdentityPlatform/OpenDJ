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

import java.util.Date;

import static org.opends.server.servicetag.ServiceTagDefinition.*;

/**
 * The ServiceTag class represents a ServiceTag of the common registry.
 * The class allows to create new instance and retreive properties values.
 */
public class ServiceTag {

    private String instanceURN;
    private String productName;
    private String productVersion;
    private String productURN;
    private String productParent;
    private String productParentURN;
    private String productDefinedInstanceID;
    private String productVendor;
    private String platformArch;
    private String container;
    private String source;
    private int installerUID;
    private Date timestamp;
    // Service Tag Field Lengths (defined in sthelper.h)
    // Since the constants defined in sthelper.h includes the null-terminated
    // character, so minus 1 from the sthelper.h defined values.
    private final int MAX_URN_LEN = 256 - 1;
    private final int MAX_PRODUCT_NAME_LEN = 256 - 1;
    private final int MAX_PRODUCT_VERSION_LEN = 64 - 1;
    private final int MAX_PRODUCT_PARENT_LEN = 256 - 1;
    private final int MAX_PRODUCT_VENDOR_LEN = 64 - 1;
    private final int MAX_PLATFORM_ARCH_LEN = 64 - 1;
    private final int MAX_CONTAINER_LEN = 64 - 1;
    private final int MAX_SOURCE_LEN = 64 - 1;

    // private constructors
    private ServiceTag() {
    }

    /**
     * Build a ServiceTag object.
     * @param instanceURN the instanceUrn of the ServiceTag.
     * @param productName the product name.
     * @param productVersion the product version.
     * @param productURN the product urn.
     * @param productParent the product parent name.
     * @param productParentURN the parent urn.
     * @param productDefinedInstanceID the installed location of the product.
     * @param productVendor the product vendor.
     * @param platformArch the platform arch.
     * @param container the container.
     * @param source the source.
     * @param installerUID the installed UID
     * @param timestamp the timestamp of the ServiceTag.
     */
    public ServiceTag(String instanceURN,
            String productName,
            String productVersion,
            String productURN,
            String productParent,
            String productParentURN,
            String productDefinedInstanceID,
            String productVendor,
            String platformArch,
            String container,
            String source,
            int installerUID,
            Date timestamp) {
        setInstanceURN(instanceURN);
        setProductName(productName);
        setProductVersion(productVersion);
        setProductURN(productURN);
        setProductParentURN(productParentURN);
        setProductParent(productParent);
        setProductDefinedInstanceID(productDefinedInstanceID);
        setProductVendor(productVendor);
        setPlatformArch(platformArch);
        setContainer(container);
        setSource(source);
        setInstallerUID(installerUID);
        setTimestamp(timestamp);
    }

    /**
     * Creates a new instance of DsServiceTag.
     * @param parser            the properties provider.
     * @param source            the creator name.
     * @param installedLocation the installed location of the product.
     * @return DsServiceTag instance.
     */
    public static ServiceTag newInstance(SwordFishIDParser parser,
            String source,
            String installedLocation) {
        return new ServiceTag("", /* empty instance_urn */
                parser.getProductName(),
                parser.getProductVersion(),
                parser.getSwordFishID(),
                parser.getProductParent(),
                parser.getProductParentUrn(),
                installedLocation,
                parser.getProductVendor(),
                SystemEnvironment.getSystemEnvironment().getOsArchitecture(),
                PRODUCT_CONTAINER,
                source,
                -1,
                null);
    }

    /**
     * Creates a new service tag object with no instance_urn.
     * @param productName               the name of the product.
     * @param productVersion            the version of the product.
     * @param productURN                the uniform resource name of the product
     * @param productParent             the name of the product's parent.
     * @param productParentURN          the uniform resource name of the
     *                                  product's parent.
     * @param productDefinedInstanceID  the instance identifier.
     * @param productVendor             the vendor of the product.
     * @param platformArch              the operating system architecture.
     * @param container                 the container of the product.
     * @param source                    the source of the product.
     *
     * @return DsServiceTag instance.
     */
    public static ServiceTag newInstance(String productName,
            String productVersion,
            String productURN,
            String productParent,
            String productParentURN,
            String productDefinedInstanceID,
            String productVendor,
            String platformArch,
            String container,
            String source) {
        return new ServiceTag("", /* empty instance_urn */
                productName,
                productVersion,
                productURN,
                productParent,
                productParentURN,
                productDefinedInstanceID,
                productVendor,
                platformArch,
                container,
                source,
                -1,
                null);
    }

    /**
     * Creates a service tag object with a specified <tt>instance_urn</tt>.
     *
     * @param instanceURN               the uniform resource name of this
     *                                  instance.
     * @param productName               the name of the product.
     * @param productVersion            the version of the product.
     * @param productURN                the uniform resource name of the product
     * @param productParent             the name of the product's parent.
     * @param productParentURN          the uniform resource name of the
     *                                  product's parent.
     * @param productDefinedInstanceID  the instance identifier.
     * @param productVendor             the vendor of the product.
     * @param platformArch              the operating system architecture.
     * @param container                 the container of the product.
     * @param source                    the source of the product.
     * @return the ServiceTag object
     */
    public static ServiceTag newInstance(String instanceURN,
            String productName,
            String productVersion,
            String productURN,
            String productParent,
            String productParentURN,
            String productDefinedInstanceID,
            String productVendor,
            String platformArch,
            String container,
            String source) {
        return new ServiceTag(instanceURN,
                productName,
                productVersion,
                productURN,
                productParent,
                productParentURN,
                productDefinedInstanceID,
                productVendor,
                platformArch,
                container,
                source,
                -1,
                null);
    }

    /**
     * Returns a uniform resource name (urn).
     * @return a URN as a String.
     */
    public static String generateInstanceURN() {
        return Util.generateURN();
    }

    /**
     * Returns the uniform resource name of this service tag instance.
     * @return  the instance_urn of this service tag.
     */
    public String getInstanceURN() {
        return instanceURN;
    }

    /**
     * Returns the name of the product.
     * @return the product name.
     */
    public String getProductName() {
        return productName;
    }

    /**
     * Returns the version of the product.
     * @return the product version.
     */
    public String getProductVersion() {
        return productVersion;
    }

    /**
     * Returns the uniform resource name of the product.
     * @return the product URN.
     */
    public String getProductURN() {
        return productURN;
    }

    /**
     * Returns the uniform resource name of the product's parent.
     * @return the product's parent URN.
     */
    public String getProductParentURN() {
        return productParentURN;
    }

    /**
     * Returns the name of the product's parent.
     * @return the product's parent name.
     */
    public String getProductParent() {
        return productParent;
    }

    /**
     * Returns the identifier defined for this product instance.
     * @return  the identifier defined for this product instance.
     */
    public String getProductDefinedInstanceID() {
        return productDefinedInstanceID;
    }

    /**
     * Returns the vendor of the product.
     * @return the product vendor.
     */
    public String getProductVendor() {
        return productVendor;
    }

    /**
     * Returns the platform architecture on which the product
     * is running on.
     * @return the platform architecture on which the product is running on.
     */
    public String getPlatformArch() {
        return platformArch;
    }

    /**
     * Returns the timestamp.  This timestamp is set when this service tag
     * is added to or updated in the system service tag registry.
     * @return timestamp when this service tag
     * is added to or updated in a the system service tag registry.
     */
    public Date getTimestamp() {
        if (timestamp != null) {
            return (Date) timestamp.clone();
        } else {
            return null;
        }
    }

    /**
     * Returns the container of the product.
     * @return the container of the product.
     */
    public String getContainer() {
        return container;
    }

    /**
     * Returns the source of this service tag.
     * @return  source of this service tag.
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns the UID. The UID is set when this service tag
     * is added to or updated in the system service tag registry.
     * @return the UID of whom this service tag
     * is added to or updated in the system service tag registry.
     */
    public int getInstallerUID() {
        return installerUID;
    }

    // The following setter methods are used to validate the
    // input field when constructing a ServiceTag instance
    private void setInstanceURN(String instanceURN) {
        if (instanceURN == null) {
            throw new NullPointerException(
                    "Parameter instanceURN cannot be null");
        }
        if (instanceURN.length() > MAX_URN_LEN) {
            throw new IllegalArgumentException("instanceURN \"" + instanceURN +
                    "\" exceeds maximum length " + MAX_URN_LEN);
        }
        this.instanceURN = instanceURN;
    }

    private void setProductName(String productName) {
        if (productName == null) {
            throw new NullPointerException(
                    "Parameter productName cannot be null");
        }
        if (productName.length() == 0) {
            throw new IllegalArgumentException("product name cannot be empty");
        }
        if (productName.length() > MAX_PRODUCT_NAME_LEN) {
            throw new IllegalArgumentException("productName \"" + productName +
                    "\" exceeds maximum length " + MAX_PRODUCT_NAME_LEN);
        }
        this.productName = productName;
    }

    private void setProductVersion(String productVersion) {
        if (productVersion == null) {
            throw new NullPointerException(
                    "Parameter productVersion cannot be null");
        }

        if (productVersion.length() == 0) {
            throw new IllegalArgumentException(
                    "product version cannot be empty");
        }
        if (productVersion.length() > MAX_PRODUCT_VERSION_LEN) {
            throw new IllegalArgumentException("productVersion \"" +
                    productVersion + "\" exceeds maximum length " +
                    MAX_PRODUCT_VERSION_LEN);
        }
        this.productVersion = productVersion;
    }

    private void setProductURN(String productURN) {
        if (productURN == null) {
            throw new NullPointerException(
                    "Parameter productURN cannot be null");
        }
        if (productURN.length() == 0) {
            throw new IllegalArgumentException(
                    "product URN cannot be empty");
        }
        if (productURN.length() > MAX_URN_LEN) {
            throw new IllegalArgumentException("productURN \"" + productURN +
                    "\" exceeds maximum length " + MAX_URN_LEN);
        }
        this.productURN = productURN;
    }

    private void setProductParentURN(String productParentURN) {
        if (productParentURN == null) {
            throw new NullPointerException(
                    "Parameter productParentURN cannot be null");
        }
        // optional field - can be empty
        if (productParentURN.length() > MAX_URN_LEN) {
            throw new IllegalArgumentException("productParentURN \"" +
                    productParentURN + "\" exceeds maximum length " +
                    MAX_URN_LEN);
        }
        this.productParentURN = productParentURN;
    }

    private void setProductParent(String productParent) {
        if (productParent == null) {
            throw new NullPointerException(
                    "Parameter productParent cannot be null");
        }
        if (productParent.length() == 0) {
            throw new IllegalArgumentException(
                    "product parent cannot be empty");
        }
        if (productParent.length() > MAX_PRODUCT_PARENT_LEN) {
            throw new IllegalArgumentException("productParent \"" +
                    productParent + "\" exceeds maximum length " +
                    MAX_PRODUCT_PARENT_LEN);
        }
        this.productParent = productParent;
    }

    private void setProductDefinedInstanceID(String productDefinedInstanceID) {
        if (productDefinedInstanceID == null) {
            throw new NullPointerException(
                    "Parameter productDefinedInstanceID cannot be null");
        }
        if (productDefinedInstanceID.length() > MAX_URN_LEN) {
            throw new IllegalArgumentException("productDefinedInstanceID \"" +
                    productDefinedInstanceID + "\" exceeds maximum length " +
                    MAX_URN_LEN);
        }
        // optional field - can be empty
        this.productDefinedInstanceID = productDefinedInstanceID;
    }

    private void setProductVendor(String productVendor) {
        if (productVendor == null) {
            throw new NullPointerException(
                    "Parameter productVendor cannot be null");
        }
        if (productVendor.length() == 0) {
            throw new IllegalArgumentException(
                    "product vendor cannot be empty");
        }
        if (productVendor.length() > MAX_PRODUCT_VENDOR_LEN) {
            throw new IllegalArgumentException("productVendor \"" +
                    productVendor + "\" exceeds maximum length " +
                    MAX_PRODUCT_VENDOR_LEN);
        }
        this.productVendor = productVendor;
    }

    private void setPlatformArch(String platformArch) {
        if (platformArch == null) {
            throw new NullPointerException(
                    "Parameter platformArch cannot be null");
        }
        if (platformArch.length() == 0) {
            throw new IllegalArgumentException(
                    "platform architecture cannot be empty");
        }
        if (platformArch.length() > MAX_PLATFORM_ARCH_LEN) {
            throw new IllegalArgumentException("platformArch \"" +
                    platformArch + "\" exceeds maximum length " +
                    MAX_PLATFORM_ARCH_LEN);
        }
        this.platformArch = platformArch;
    }

    private void setTimestamp(Date timestamp) {
        // can be null
        this.timestamp = timestamp;
    }

    private void setContainer(String container) {
        if (container == null) {
            throw new NullPointerException(
                    "Parameter container cannot be null");
        }
        if (container.length() == 0) {
            throw new IllegalArgumentException("container cannot be empty");
        }
        if (container.length() > MAX_CONTAINER_LEN) {
            throw new IllegalArgumentException("container \"" +
                    container + "\" exceeds maximum length " +
                    MAX_CONTAINER_LEN);
        }
        this.container = container;
    }

    private void setSource(String source) {
        if (source == null) {
            throw new NullPointerException("Parameter source cannot be null");
        }
        if (source.length() == 0) {
            throw new IllegalArgumentException("source cannot be empty");
        }
        if (source.length() > MAX_SOURCE_LEN) {
            throw new IllegalArgumentException("source \"" + source +
                    "\" exceeds maximum length " + MAX_SOURCE_LEN);
        }
        this.source = source;
    }

    private void setInstallerUID(int installerUID) {
        this.installerUID = installerUID;
    }

    /**
     * Compares this service tag to the specified object.
     * @param obj Object to test.
     * @return true if this service tag is the same as
     * the specified object.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof ServiceTag)) {
            return false;
        }
        ServiceTag st = (ServiceTag) obj;
        if (st == this) {
            return true;
        }
        return st.getInstanceURN().equals(getInstanceURN());
    }

    /**
     * Returns the hash code value for this service tag.
     * @return the hash code value for this service tag.
     */
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 19 * hash + (this.instanceURN != null ?
            this.instanceURN.hashCode()
                : 0);
        return hash;
    }
}
