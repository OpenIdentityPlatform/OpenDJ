package opendj;

import java.util.List;
import java.util.Set;

import opendj.server.MsadPluginCfg;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Schema;
import org.opends.messages.CoreMessages;
import org.opends.messages.PluginMessages;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.api.LocalBackend;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult.PreOperation;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordPolicy;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeParser;
import org.opends.server.types.Attributes;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;
import org.opends.server.types.operation.PreOperationAddOperation;
import org.opends.server.types.operation.PreOperationBindOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;

public class MsadPlugin extends DirectoryServerPlugin<MsadPluginCfg>
        implements ConfigurationChangeListener<MsadPluginCfg> {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    private static final String USER_ACCOUNT_CONTROL_OID = "1.2.840.113556.1.4.8";
    private static final String MS_DS_USER_ACCOUNT_DISABLED_OID = "1.2.840.113556.1.4.1853";
    private static final String PWD_LAST_SET_OID = "1.2.840.113556.1.4.96";
    private static final String USER_OID = "1.2.840.113556.1.5.9";
    private static final String MS_DS_BINDABLE_OBJECT_OID = "1.2.840.113556.1.5.244";
    private AttributeType userAccountControlAT;
    private AttributeType msDSUserAccountDisabledAT;
    private AttributeType pwdLastSetAT;
    private ObjectClass userOC;
    private ObjectClass msDSBindableObjectOC;

    private MsadPluginCfg config;

    public MsadPlugin() {
        super();
        logger.info(LocalizableMessage.raw("created MSAD plugin"));
    }

    @Override
    public void initializePlugin(Set<PluginType> pluginTypes, MsadPluginCfg config)
            throws ConfigException, InitializationException {
        this.config = config;
        config.addMsadChangeListener(this);

        Schema schema = getServerContext().getSchema();
        userAccountControlAT = schema.getAttributeType(USER_ACCOUNT_CONTROL_OID);
        msDSUserAccountDisabledAT = schema.getAttributeType(MS_DS_USER_ACCOUNT_DISABLED_OID);
        pwdLastSetAT = schema.getAttributeType(PWD_LAST_SET_OID);
        userOC = schema.getObjectClass(USER_OID);
        msDSBindableObjectOC = schema.getObjectClass(MS_DS_BINDABLE_OBJECT_OID);

        for (PluginType t : pluginTypes) {
            switch (t) {
                case PRE_OPERATION_BIND:
                    break;
                case PRE_OPERATION_ADD:
                    break;
                case PRE_OPERATION_MODIFY:
                    break;
                default:
                    throw new InitializationException(
                            PluginMessages.ERR_PLUGIN_TYPE_NOT_SUPPORTED.get(this.getPluginEntryDN(), t));
            }
        }

        logger.info(LocalizableMessage.raw("initialized MSAD plugin"));
    }

    @Override
    public PreOperation doPreOperation(PreOperationBindOperation bindOperation) {
        DN bindDN = bindOperation.getBindDN();
        Entry userEntry;

        try {
            if (bindOperation.getAuthenticationType().equals(AuthenticationType.SIMPLE)) {
                DN dn = DirectoryServer.getActualRootBindDN(bindDN);
                if (dn != null) {
                    bindDN = dn;
                }
            }

            userEntry = getUserEntry(bindDN);
        } catch (DirectoryException e) {
            logger.traceException(e);
            return PreOperation.stopProcessing(e.getResultCode(), e.getMessageObject());
        }

        if (userEntry == null) {
            return PreOperation.stopProcessing(ResultCode.INVALID_CREDENTIALS, CoreMessages.ERR_BIND_OPERATION_UNKNOWN_USER.get());
        }

        if (parseAttribute(userEntry, msDSUserAccountDisabledAT).asBoolean(false)
                || (parseAttribute(userEntry, userAccountControlAT).asLong(0L) & 2L) != 0L) {
            return PreOperation.stopProcessing(ResultCode.INVALID_CREDENTIALS, CoreMessages.ERR_BIND_OPERATION_ACCOUNT_DISABLED.get());
        }

        return PreOperation.continueOperationProcessing();
    }

    @Override
    public PreOperation doPreOperation(PreOperationAddOperation addOperation) throws CanceledOperationException {
        if (addOperation.isSynchronizationOperation()) {
            return PreOperation.continueOperationProcessing();
        }

        Entry entry = addOperation.getEntryToAdd();
        if (!isActiveDirectoryUser(entry)) {
            return PreOperation.continueOperationProcessing();
        }

        if (parseAttribute(entry, pwdLastSetAT).asLong(-1L) != 0L) {
            entry.replaceAttribute(Attributes.create(pwdLastSetAT, currentTimeInActiveDirectory()));
        }

        return PreOperation.continueOperationProcessing();
    }

    @Override
    public PreOperation doPreOperation(PreOperationModifyOperation modifyOperation) throws CanceledOperationException {
        if (modifyOperation.isSynchronizationOperation()) {
            return PreOperation.continueOperationProcessing();
        }

        Entry entry = modifyOperation.getModifiedEntry();
        if (!isActiveDirectoryUser(entry)) {
            return PreOperation.continueOperationProcessing();
        }

        try {
            AttributeType userPasswordAT = CoreSchema.getUserPasswordAttributeType();
            AuthenticationPolicy policy = AuthenticationPolicy.forUser(entry, true);
            if (policy.isPasswordPolicy()) {
                userPasswordAT = ((PasswordPolicy) policy).getPasswordAttribute();
            }

            boolean needUpdatePwdLastSet = false;
            boolean nonZeroPwdLastSet = true;
            List<Modification> mods = modifyOperation.getModifications();
            for (Modification mod : mods) {
                Attribute attr = mod.getAttribute();
                AttributeType type = attr.getAttributeDescription().getAttributeType();
                if (nonZeroPwdLastSet && userPasswordAT.equals(type)) {
                    needUpdatePwdLastSet = true;
                } else if (pwdLastSetAT.equals(type)) {
                    nonZeroPwdLastSet = AttributeParser.parseAttribute(attr).asLong(-1L) != 0L;
                    needUpdatePwdLastSet = nonZeroPwdLastSet;
                }
            }

            if (needUpdatePwdLastSet) {
                Modification mod = new Modification(ModificationType.REPLACE,
                        Attributes.create(pwdLastSetAT, currentTimeInActiveDirectory()));
                modifyOperation.addModification(mod);
            }
        } catch (DirectoryException e) {
            logger.traceException(e);
            return PreOperation.stopProcessing(e.getResultCode(), e.getMessageObject());
        }

        return PreOperation.continueOperationProcessing();
    }

    @Override
    public ConfigChangeResult applyConfigurationChange(MsadPluginCfg config) {
        logger.info(LocalizableMessage.raw("changed MSAD plugin configuration"));

        this.config = config;
        return new ConfigChangeResult();
    }

    @Override
    public boolean isConfigurationChangeAcceptable(MsadPluginCfg config, List<LocalizableMessage> messages) {
        return true;
    }

    private Entry getUserEntry(DN dn) throws DirectoryException {
        LocalBackend<?> backend = getServerContext().getBackendConfigManager().findLocalBackendForEntry(dn);
        return backend != null ? backend.getEntry(dn) : null;
    }

    private AttributeParser parseAttribute(Entry entry, AttributeType type) {
        List<Attribute> attributes = entry.getAllAttributes(type);
        return AttributeParser.parseAttribute(attributes == null || attributes.isEmpty()
                ? null : attributes.get(0));
    }

    private boolean isActiveDirectoryUser(Entry entry) {
        return (userOC != null && entry.hasObjectClass(userOC))
                || (msDSBindableObjectOC != null && entry.hasObjectClass(msDSBindableObjectOC));
    }

    // https://github.com/apache/directory-studio/blob/2.0.0.v20200411-M15/plugins/valueeditors/src/main/java/org/apache/directory/studio/valueeditors/adtime/ActiveDirectoryTimeUtils.java#L54
    private static String currentTimeInActiveDirectory() {
        return Long.toUnsignedString(System.currentTimeMillis() * 10000L + 116444736000000000L);
    }
}
