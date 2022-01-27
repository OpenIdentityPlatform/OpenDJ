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

    public static boolean isFips() {
    	java.security.Provider[] providers = java.security.Security.getProviders();
		for (int i = 0; i < providers.length; i++) {
			if (providers[i].getName().toLowerCase().contains("fips"))
				return true;
		}

		return false;
	}

    public static void registerBcProvider()
    {
    	if (!isFips()) {
    		return;
    	}

    	org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider bouncyCastleProvider = (org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider) java.security.Security.getProvider(org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider.PROVIDER_NAME);
  		if (bouncyCastleProvider == null) {
  			logger.info(INFO_BC_PROVIDER_REGISTER.get());

  			bouncyCastleProvider = new org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider();
  			java.security.Security.insertProviderAt(bouncyCastleProvider, 1);
  		} else {
  			logger.info(INFO_BC_PROVIDER_REGISTERED_ALREADY.get());
  		}
    }
}
