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
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import org.opends.server.core.DirectoryServer;

import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.debug.DebugLogger.*;

import static org.opends.server.servicetag.ServiceTagDefinition.*;
import static org.opends.messages.ServiceTagMessages.*;

/**
 * ServiceTagRegistration service is responsible to
 * manage the Common and Active product registration : Only common registration
 * is currently supported.
 * Main class to register/delete ServiceTags.
 */
public class ServiceTagRegistration {

    /**
    * The tracer object for the debug logger.
    */
    private static final DebugTracer TRACER = getTracer();


    // Registration singleton service
    private static ServiceTagRegistration registrationService = null;
    // Configuration / properties files management
    private SwordFishIdConfiguration configurationService = null;
    // ServiceTag Registry which manage the stclient calls
    private Registry registry = null;

    /**
     * Private service contructor.
     */
    private ServiceTagRegistration() {
        // Get the configuration
        this.configurationService = SwordFishIdConfiguration.getService();
        // Get the common registry
        if (Registry.isSupported()) {
            this.registry = Registry.getSystemRegistry();
        }
    }

    /**
     * Gets the ServiceTag registration service.
     * This registration service allow to register create and register
     * ServiceTag for products.
     * @return the service
     */
    public static ServiceTagRegistration getRegistrationService() {
        if (ServiceTagRegistration.registrationService == null) {
            ServiceTagRegistration.registrationService =
                    new ServiceTagRegistration();
        }

        return registrationService;
    }

    /**
     * Tests if the stclient CLI is present on the filesystem.
     * The serviceTag packages are not relocatable so the full path
     * is well known if exists.
     * @return true if stclient binary exists, false otherwise.
     */
    public boolean isCommonRegistrationSupported() {
        return Registry.isSupported();
    }

    /**
     * Create the defined serviceTags for OpenDS Based Servers if
     * the native registration is supported
     * If the system supports service tag, stclient will be used
     * to create the OpenDS service tag.
     * @param svcTag the ServiceTag to register.
     * @throws org.opends.server.servicetag.ServiceTagException
     * if the ServiceTag can not be registered
     * @throws org.opends.server.servicetag.ServiceTagAlreadyExistsException
     * if the ServiceTag already exists in the common registry based on
     * the product_urn and product_defined_instanceid
     * @throws java.lang.IllegalArgumentException if parameter is not valid.
     */
    public void registerServiceTag(ServiceTag svcTag) throws
            ServiceTagException, IllegalArgumentException,
            ServiceTagAlreadyExistsException {

        // Test if the common registration is supported on the filesystem
        // or not
        if (!isCommonRegistrationSupported()) {
            throw new ServiceTagException(
                    WARN_REGISTRY_NOT_SUPPORTED.get());
        }

        // Parameter checking
        if (svcTag == null) {
            throw new IllegalArgumentException(
                    WARN_PARAMETER_CANNOT_BE_NULL.get("svcTag").toString());
        }

        // Add the ServiceTag if it does not exist
        if (this.registry.existServiceTag(
                svcTag.getProductURN(),
                svcTag.getProductDefinedInstanceID())) {
            throw new ServiceTagAlreadyExistsException
                    (WARN_SERVICETAG_ALREADY_EXIST.get());
        }

        // Add the ServiceTag in the common registry
        try {
            this.registry.addServiceTag(svcTag);
        } catch (IOException ex) {
            throw new ServiceTagException(
                    WARN_SERVICETAG_CANNOT_BE_REGISTERED.get());
        }
    }

    /**
     * Create and register the defined serviceTags for OpenDS Based Servers if
     * the common registration is supported.
     * If the system supports service tag, stclient will be used
     * to create the OpenDS service tag.
     * @param source defining who is the creater
     * @return a set of DsServiceTag which have NOT been registered
     *         due to registration errors.
     *         An empty Set means that no error occurs.
     * @throws org.opends.server.servicetag.ServiceTagException if a pb
     *         occurs.
     * @throws java.lang.IllegalArgumentException if parameter is not valid.
     */
    public Set<ServiceTag> registerServiceTags(String source)
            throws ServiceTagException, IllegalArgumentException {

        // Parameter checking
        // Test if the common registration is supported on the filesystem
        // or not
        if (!isCommonRegistrationSupported()) {
            throw new ServiceTagException(
                    WARN_REGISTRY_NOT_SUPPORTED.get());
        }

        if ((source == null) || (source.length() == 0)) {
            throw new IllegalArgumentException(
                    WARN_PARAMETER_CANNOT_BE_NULL.get("source").toString());
        }

        // Errors Set
        Set<ServiceTag> errors = new HashSet<ServiceTag>();

        // Get the parsers
        Set<SwordFishIDParser> parsers =
                this.configurationService.getParsers();

        // Throw exception id nothing to register
        if (parsers.isEmpty()) {
            throw new ServiceTagException(
                    WARN_NO_SERVICETAG_TO_REGISTER.get());
        }

        // Register ServiceTag
        ServiceTag svcTag = null;
        for (SwordFishIDParser parser : parsers) {

            // create the serviceTag if the registration is supported
            // and the tag does not already exist
            svcTag = ServiceTag.newInstance(
                    parser,
                    source,
                    DirectoryServer.getServerRoot());
            try {
                registerServiceTag(svcTag);
            } catch (Exception ex) {
                errors.add(svcTag);
            }
        }
        return errors;
    }

    /**
     * Deletes from the common registry the defined ServiceTag.
     * @param svcTag to delete.
     * @throws java.lang.IllegalArgumentException if parameter is not valid.
     * @throws org.opends.server.servicetag.ServiceTagException if a
     *         pb occurs.
     * @throws org.opends.server.servicetag.ServiceTagDoesNotExistException
     *         if the ServiceTag to delete does not exist.
     */
    public void deleteServiceTag(ServiceTag svcTag) throws
            IllegalArgumentException, ServiceTagException,
            ServiceTagDoesNotExistException {

        // Test if the common registration is supported on the filesystem
        // or not
        if (!isCommonRegistrationSupported()) {
            throw new ServiceTagException(
                    WARN_REGISTRY_NOT_SUPPORTED.get());
        }
        // Parametr checking
        if (svcTag == null) {
            throw new IllegalArgumentException(
                    WARN_PARAMETER_CANNOT_BE_NULL.get("svcTag").toString());
        }

        if (!this.registry.existServiceTag(
                svcTag.getProductURN(),
                svcTag.getProductDefinedInstanceID())) {
            throw new ServiceTagDoesNotExistException(
                    WARN_SERVICETAG_DOESNOT_EXIST.get());
        }

        try {
            this.registry.removeServiceTag(svcTag.getInstanceURN());
        } catch (IOException ex) {
            throw new ServiceTagException(
                    WARN_NO_SERVICETAG_TO_REMOVE.get());
        }
    }

    /**
     * Delete the created tags defined in the properties files.
     * @throws org.opends.server.servicetag.ServiceTagException.
     */
    private Set<ServiceTag> deleteServiceTags() throws ServiceTagException {

        // Parameter checking
        // Test if the common registration is supported on the filesystem
        // or not
        if (!isCommonRegistrationSupported()) {
            throw new ServiceTagException(
                    WARN_REGISTRY_NOT_SUPPORTED.get());
        }

        Set<SwordFishIDParser> parsers =
                this.configurationService.getParsers();

        // Throw exception id nothing to register
        if (parsers.isEmpty()) {
            throw new ServiceTagException(
                    WARN_NO_SERVICETAG_TO_REMOVE.get());
        }

       Set<ServiceTag> errors = new HashSet<ServiceTag>();
       for (SwordFishIDParser parser : parsers) {
            Set<ServiceTag> removeTags = null;
            try {
                removeTags = this.registry.findServiceTags(
                        parser.getSwordFishID());
                if ((removeTags == null) || (removeTags.isEmpty())) {
                    throw new ServiceTagException(
                            WARN_NO_SERVICETAG_TO_REMOVE.get());
                }
            } catch (Exception ex) {
                throw new ServiceTagException(
                        WARN_NO_SERVICETAG_TO_REMOVE.get());
            }

            for (ServiceTag svcTag : removeTags) {
                try {
                    deleteServiceTag(svcTag);
                } catch (Exception ex) {
                    errors.add(svcTag);
                }
            }
        }
        return errors;
    }

    /**
     * Creates and register a ServiceTag based on properties definitions
     * and source.
     * @param source defining the caller.
     * @param properties where the following ones are defined
     * <B>org.opends.server.servicetag.productname</B>
     * <B>org.opends.server.servicetag.version</B>
     * <B>org.opends.server.servicetag.uuid</B>
     * <B>org.opends.server.servicetag.parent</B>
     * <B>org.opends.server.servicetag.parenturn</B>
     * <B>org.opends.server.servicetag.vendor</B>
     * @return the corresponding registered ServiceTag object
     * @throws org.opends.server.servicetag.ServiceTagException if a
     *         registration problem occurs.
     * @throws org.opends.server.servicetag.ServiceTagAlreadyExistsException if
     * the Service tag already exist.
     * @throws java.lang.IllegalArgumentException if parameters are not valid.
     */
    public ServiceTag registerServiceTag(String source,
            Properties properties)
            throws ServiceTagException, IllegalArgumentException,
            ServiceTagAlreadyExistsException {

        if ((source == null) || (source.length() == 0)) {
            throw new IllegalArgumentException(
                    WARN_PARAMETER_CANNOT_BE_NULL.get("source").toString());
        }

        if (properties == null) {
            throw new IllegalArgumentException(
                    WARN_PARAMETER_CANNOT_BE_NULL.get("properties").toString());
        }

        if (!properties.containsKey(PRODUCT_NAME)) {
            throw new IllegalArgumentException(
                    WARN_BAD_PROPERTIES.get(PRODUCT_NAME).toString());
        }
        String productName = properties.getProperty(PRODUCT_NAME);

        if (!properties.containsKey(PRODUCT_VERSION)) {
            throw new IllegalArgumentException(
                    WARN_BAD_PROPERTIES.get(PRODUCT_VERSION).toString());
        }
        String productVersion = properties.getProperty(PRODUCT_VERSION);

        if (!properties.containsKey(PRODUCT_UUID)) {
            throw new IllegalArgumentException(
                    WARN_BAD_PROPERTIES.get(PRODUCT_UUID).toString());
        }
        String productURN = properties.getProperty(PRODUCT_UUID);

        if (!properties.containsKey(PRODUCT_PARENT)) {
            throw new IllegalArgumentException(
                    WARN_BAD_PROPERTIES.get(PRODUCT_PARENT).toString());
        }
        String productParent = properties.getProperty(PRODUCT_PARENT);

        if (!properties.containsKey(PRODUCT_PARENT_URN)) {
            throw new IllegalArgumentException(
                    WARN_BAD_PROPERTIES.get(PRODUCT_PARENT_URN).toString());
        }
        String productParentURN = properties.getProperty(PRODUCT_PARENT_URN);

        ServiceTag svcTag = ServiceTag.newInstance(
                productName,
                productVersion,
                productURN,
                productParent,
                productParentURN,
                DirectoryServer.getServerRoot(),
                PRODUCT_VENDOR,
                SystemEnvironment.getSystemEnvironment().getOsArchitecture(),
                PRODUCT_CONTAINER,
                source);

        registerServiceTag(svcTag);

        return svcTag;
    }

    /**
     * Test purpose.
     * @param args represents -register or -delete command.
     */
    public static void main(String[] args) {

        String source = "Manual";

        // Parse the options (arguments starting with "-" )
        boolean delete = false;
        boolean register = false;

        int count = 0;
        while (count < args.length) {
            String arg = args[count];
            if (arg.trim().length() == 0) {
                // skip empty arguments
                count++;
                continue;
            }

            if (arg.equals("-delete")) {
                delete = true;
            } else if (arg.equals("-register")) {
                register = true;
            }
            count++;
        }

        if ((register == false) && (delete == false)) {
            usage();
            return;
        }

        ServiceTagRegistration service =
                ServiceTagRegistration.getRegistrationService();

        if (delete) {
            try {
                service.deleteServiceTags();
            } catch (ServiceTagException ex) {
    // ServiceTags Registration errors do not prevent the server to
        // start. WARNING logged in debug mode
        if (debugEnabled()) {
           TRACER.debugCaught(DebugLogLevel.WARNING, ex);
      }            }
        } else {
            try {
                service.registerServiceTags(source);
            } catch (Exception ex) {
               // ServiceTags Registration errors do not prevent the server to
               // start. WARNING logged in debug mode
               if (debugEnabled()) {
                   TRACER.debugCaught(DebugLogLevel.WARNING, ex);
               }
            }
        }
        System.exit(0);
    }

    private static void usage() {
        System.out.println("Usage:");
        System.out.print("    " + ServiceTagRegistration.class.getName());
        System.out.println(" [-delete|-register|-help]");
        System.out.println("       to delete/register a OpenDS ServiceTags");
        System.out.println("");
        System.out.println("Options:");
        System.out.println("    -delete  : to delete the OpenDS ServiceTags");
        System.out.println("    -register: to register the OpenDS ServiceTags");
        System.out.println("    -help    : to print this help message");
    }
}
