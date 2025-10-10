package com.forgerock.opendj.util;

import org.forgerock.i18n.slf4j.LocalizedLogger;

import static com.forgerock.opendj.ldap.CoreMessages.INFO_BC_PROVIDER_REGISTER;
import static com.forgerock.opendj.ldap.CoreMessages.INFO_BC_PROVIDER_REGISTERED_ALREADY;
import static com.forgerock.opendj.ldap.CoreMessages.INFO_BC_FIPS_PROVIDER_NOT_EXISTS;
import static com.forgerock.opendj.ldap.CoreMessages.INFO_BC_FIPS_PROVIDER_REGISTER;
import static com.forgerock.opendj.ldap.CoreMessages.INFO_BC_PROVIDER_FAILED_TO_CREATE;

import java.security.Security;

public class FipsStaticUtils {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    public static final String BC_PROVIDER_NAME = "BC";
    public static final String BC_FIPS_PROVIDER_NAME = "BCFIPS";

    private static final String BC_GENERIC_PROVIDER_CLASS_NAME = "org.bouncycastle.jce.provider.BouncyCastleProvider";
    private static final String BC_FIPS_PROVIDER_CLASS_NAME = "org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider";

    public static void registerBcProvider() {
        registerBcProvider(false);
    }
    public static void registerBcProvider(boolean force) {

        if(!"true".equals(System.getProperty("org.openidentityplatform.opendj.fips.register")) && !force) {
            return;
        }

        String providerName = BC_PROVIDER_NAME;
        String className = BC_GENERIC_PROVIDER_CLASS_NAME;

        boolean bcFipsProvider = checkBcFipsProvider();
        if (bcFipsProvider) {
            logger.info(INFO_BC_FIPS_PROVIDER_REGISTER);

            providerName = BC_FIPS_PROVIDER_NAME;
            className = BC_FIPS_PROVIDER_CLASS_NAME;
        } else {
            logger.info(INFO_BC_PROVIDER_REGISTER.get());
        }

        installBCProvider(providerName, className);
    }

    private static void installBCProvider(String providerName, String providerClassName) {
        java.security.Provider bouncyCastleProvider = Security.getProvider(providerName);
        if (bouncyCastleProvider == null) {
            try {
                bouncyCastleProvider = (java.security.Provider) Class.forName(providerClassName).getConstructor().newInstance();
                java.security.Security.insertProviderAt(bouncyCastleProvider, 1);
            } catch (ReflectiveOperationException | IllegalArgumentException | SecurityException ex) {
                logger.error(INFO_BC_PROVIDER_FAILED_TO_CREATE.get());
            }
        } else {
            logger.info(INFO_BC_PROVIDER_REGISTERED_ALREADY.get());
        }
    }

    private static boolean checkBcFipsProvider() {
        try {
            // Check if there is BC FIPS provider libs
            Class.forName(BC_FIPS_PROVIDER_CLASS_NAME);
        } catch (ClassNotFoundException e) {
            logger.trace(INFO_BC_FIPS_PROVIDER_NOT_EXISTS.get(), e);
            return false;
        }

        return true;
    }
}
