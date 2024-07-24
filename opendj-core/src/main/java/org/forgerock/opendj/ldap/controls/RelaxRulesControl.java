package org.forgerock.opendj.ldap.controls;

import org.forgerock.opendj.ldap.ByteString;

public class RelaxRulesControl implements Control{

    public final static String OID="1.3.6.1.4.1.4203.666.5.12";

    @Override
    public String getOID() {
        return OID;
    }

    @Override
    public ByteString getValue() {
        return null;
    }

    @Override
    public boolean hasValue() {
        return false;
    }

    @Override
    public boolean isCritical() {
        return true;
    }
}
