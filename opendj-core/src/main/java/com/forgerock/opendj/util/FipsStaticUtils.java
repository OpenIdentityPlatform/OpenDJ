package com.forgerock.opendj.util;

import org.forgerock.i18n.slf4j.LocalizedLogger;

import static com.forgerock.opendj.ldap.CoreMessages.INFO_BC_PROVIDER_REGISTER;
import static com.forgerock.opendj.ldap.CoreMessages.INFO_BC_PROVIDER_REGISTERED_ALREADY;

public class FipsStaticUtils {

	private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    /**
     * A zero-length byte array.
     */
    public static final byte[] EMPTY_BYTES = new byte[0];

    public static void registerBcProvider()
    {
          org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider bouncyCastleProvider = (org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider) java.security.Security.getProvider(org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider.PROVIDER_NAME);
        if (bouncyCastleProvider == null) {
            FipsStaticUtils.logger.info(INFO_BC_PROVIDER_REGISTER.get());

            bouncyCastleProvider = new org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider();
            java.security.Security.insertProviderAt(bouncyCastleProvider, 1);
        } else {
            FipsStaticUtils.logger.info(INFO_BC_PROVIDER_REGISTERED_ALREADY.get());
        }
    }

}
