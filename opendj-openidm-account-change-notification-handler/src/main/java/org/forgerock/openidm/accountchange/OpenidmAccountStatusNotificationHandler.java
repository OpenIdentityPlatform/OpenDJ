/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.openidm.accountchange;

import static org.opends.server.types.AccountStatusNotificationType.PASSWORD_RESET;
import static org.opends.server.types.AccountStatusNotificationType.PASSWORD_CHANGED;
import static org.forgerock.openidm.accountchange.OpenidmAccountStatusNotificationHandlerMessages.*;
import static org.opends.server.types.AccountStatusNotificationProperty.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import org.forgerock.http.Client;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Headers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.spi.Loader;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.crypto.JsonCrypto;
import org.forgerock.json.crypto.JsonCryptoException;
import org.forgerock.json.crypto.JsonEncryptor;
import org.forgerock.json.crypto.simple.SimpleEncryptor;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.KeyManagers;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.server.AccountStatusNotificationHandlerCfg;
import org.forgerock.openidm.accountchange.meta.OpenidmAccountStatusNotificationHandlerCfgDefn.OpenidmCompatMode;
import org.forgerock.openidm.accountchange.server.OpenidmAccountStatusNotificationHandlerCfg;
import org.forgerock.util.Function;
import org.forgerock.util.Options;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;
import org.opends.server.api.AccountStatusNotificationHandler;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.api.TrustManagerProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AccountStatusNotification;
import org.opends.server.types.AccountStatusNotificationType;
import org.opends.server.types.Attribute;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * An account status notification handler that listens to password reset and password change events
 * in order to propagate them to OpenIDM.
 * <p>
 * The following information is retained for a change
 * <ul>
 *  <li>the entry DN</li>
 *  <li>the encrypted password</li>
 *  <li>the kind of change (PASSWORD_CHANGE, PASSWORD_RESET)</li>
 *  <li>optionally, the values of some attributes (for any attribute listed in the "attribute-type" parameter
 *  in the config)</li>
 * </ul>
 * <p>
 * There are two ways the changes can be handled, depending on the 'interval' parameter in the configuration:
 * <ul>
 *  <li>If interval is set to zero, then the change is sent immediately to OpenIDM using a HTTP POST request</li>
 *  <li>If interval is strictly superior to zero, then the change is stored locally (currently in a JE database).
 *    At each interval period of time, the changes which are stored locally are read and sent to OpenIDM using
 *    a HTTP POST request></li>
 * </ul>
 * <p>
 * The communication to OpenIDM can be done in one of three ways:
 * <ul>
 *  <li>Using HTTP : authentication to OpenIDM is done using BASIC Auth, using the openidm-username and
 *   opendidm-password parameter values from the configuration</li>
 *  <li>Using HTTPS without SSL client authentication : authentication to OpenIDM is done using BASIC Auth, using the
 *   openidm-username and opendidm-password parameter values from the configuration</li>
 *  <li>Using HTTPS with SSL client authentication : ssl-cert-nickname parameter value from the configuration
 *   is used to retrieve the appropriate client certificate from the provided key manager</li>
 * </ul>
 */
public class OpenidmAccountStatusNotificationHandler
    extends AccountStatusNotificationHandler<OpenidmAccountStatusNotificationHandlerCfg>
    implements ConfigurationChangeListener<OpenidmAccountStatusNotificationHandlerCfg>, ServerShutdownListener {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    private static final String THREADNAME = "OpenIDM AccountStatus Notification Handler Thread";
    /** Cipher used for the JSON encryptor. */
    private static final String ASYMMETRIC_CIPHER = "RSA/ECB/OAEPWithSHA1AndMGF1Padding";

    private static final byte PWD_CHANGED = 1;
    private static final byte PWD_RESET = 2;
    /**
     * The name of the logfile that the update thread uses to process change records. Defaults to "logs/pwsync", but can
     * be changed in the configuration.
     */
    private String logFileName;
    private File logFile;
    /** The hostname of the server. */
    private String hostname;
    private OpenidmAccountStatusNotificationHandlerCfg currentConfig;
    /**
     * The update interval the background thread uses. If it is 0, then there is no background thread and
     * the changes are processed in foreground.
     */
    private long interval;
    /** The flag used by the background thread to check if it should exit. */
    private boolean stopRequested;
    private Thread backgroundThread;
    /** Queue used to store changes when update interval is not equal to zero. */
    private PersistedQueue queue;
    /** Used to encrypt JSON values. */
    private JsonEncryptor encryptor;
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpClientHandler httpClientHandler;
    private Client client;
    private URI openidmURI;

    /** OpenIDM compatibility mode. */
    private OpenidmCompatMode compatMode;

    /** A service loader using the class loader that loaded the plugin. */
    private static Loader serviceLoader = new Loader() {
        @Override
        public <S> S load(final Class<S> service, final Options options) {
            final ServiceLoader<S> loader = ServiceLoader.load(service,
                    OpenidmAccountStatusNotificationHandler.class.getClassLoader());
            final Iterator<S> i = loader.iterator();
            return i.hasNext() ? i.next() : null;
        }
    };

    @Override
    public void initializeStatusNotificationHandler(OpenidmAccountStatusNotificationHandlerCfg configuration)
            throws ConfigException, InitializationException {
        if (logger.isTraceEnabled()) {
            logger.trace("Start initialization of OpenIDM status notification handler");
        }
        currentConfig = configuration;
        currentConfig.addOpenidmChangeListener(this);

        // Fetch the local host name for the client host identification.
        try {
            hostname = java.net.InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException ex) {
            hostname = "UnknownHost";
        }

        // Read configuration, check and initialize things here.
        logFileName = configuration.getLogFile();
        initializeLogFile(logFileName);
        try {
            initializeOpenIDMClient(configuration);
        } catch (DirectoryException ex) {
            throw new InitializationException(ex.getMessageObject(), ex);
        }

        // Update interval is applied only when server is restarted.
        interval = configuration.getUpdateInterval();

        // There are two possible ways to process the password changes
        // 1. if interval is zero: send changes immediately to OpenIDM
        // 2. if interval is strictly positive: persist changes locally and send them asynchronously to OpenIDM at
        //    given interval
        if (interval > 0) {
            queue = new PersistedQueue(getFileForPath(currentConfig.getLogFile()), "OpenIDMSyncQueue", 10);
            initializeBackGroundProcessing();
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Successfully finished initialization of OpenIDM status notification handler, "
                    + "using update interval: %s ms", getInterval());
        }
    }

    @Override
    public boolean isConfigurationAcceptable(AccountStatusNotificationHandlerCfg configuration,
            List<LocalizableMessage> unacceptableReasons) {
        OpenidmAccountStatusNotificationHandlerCfg config = (OpenidmAccountStatusNotificationHandlerCfg) configuration;
        return isConfigurationChangeAcceptable(config, unacceptableReasons);
    }

    @Override
    public boolean isConfigurationChangeAcceptable(OpenidmAccountStatusNotificationHandlerCfg configuration,
            List<LocalizableMessage> unacceptableReasons) {
        try {
            // ensure URI is valid
            getOpenIDMURI(configuration);

            // There are two possible ways to authenticate to OpenIDM
            // 1. Basic authentication : use openidm-user and openidm-password
            // 2. SSL client authentication : use ssl-cert-nickname (to retrieve the correct certificate in key manager)
            if (configuration.getSSLCertNickname() == null
                    && (configuration.getOpenidmUsername() == null || configuration.getOpenidmPassword() == null)) {
                unacceptableReasons.add(ERR_OPENIDM_PWSYNC_INVALID_AUTHENTICATION_CONFIG.get());
                return false;
            }
            if (configuration.getSSLCertNickname() != null) {
                String keyManagerProv = configuration.getKeyManagerProvider();
                if (keyManagerProv == null || keyManagerProv.isEmpty()) {
                    unacceptableReasons.add(ERR_OPENIDM_PWSYNC_NO_KEYMANAGER_PROVIDER.get());
                    return false;
                }
            }
        } catch (ConfigException ex) {
            unacceptableReasons.add(ex.getMessageObject());
            return false;
        }
        return true;
    }

    @Override
    public ConfigChangeResult applyConfigurationChange(OpenidmAccountStatusNotificationHandlerCfg configuration) {
        // User is not allowed to change the logfile name, append a message that the
        // server needs restarting for change to take effect.
        ConfigChangeResult configChangeResult = new ConfigChangeResult();
        String newLogFileName = configuration.getLogFile();
        if (!logFileName.equals(newLogFileName)) {
            configChangeResult.setAdminActionRequired(true);
            configChangeResult.addMessage(
                    INFO_OPENIDM_PWSYNC_LOGFILE_CHANGE_REQUIRES_RESTART.get(logFileName, newLogFileName));
        }

        if ((currentConfig.getUpdateInterval() == 0) != (configuration.getUpdateInterval() == 0)) {
            configChangeResult.setAdminActionRequired(true);
            configChangeResult.addMessage(INFO_OPENIDM_PWSYNC_UPDATE_INTERVAL_CHANGE_REQUIRES_RESTART.get(
                    currentConfig.getUpdateInterval(), configuration.getUpdateInterval()));
        } else {
            interval = configuration.getUpdateInterval();
        }
        currentConfig = configuration;

        try {
            close(httpClientHandler);
            initializeOpenIDMClient(configuration);
            configChangeResult.setResultCode(ResultCode.SUCCESS);
        } catch (DirectoryException | ConfigException | InitializationException ex) {
            configChangeResult.setResultCode(ResultCode.UNDEFINED);
            configChangeResult.setAdminActionRequired(true);
            configChangeResult.addMessage(ex.getMessageObject());
        }

        return configChangeResult;
    }

    private void initializeOpenIDMClient(OpenidmAccountStatusNotificationHandlerCfg configuration)
            throws DirectoryException, ConfigException, InitializationException {

        String certNickname = configuration.getSSLCertNickname();
        X509KeyManager x509KeyManager = (certNickname != null) ? getKeyManager(configuration) : null;
        TrustManager[] trustMgrs = getTrustManagers(configuration);
        X509Certificate serverCert = getServerCertificate(trustMgrs, configuration);

        encryptor =
                new SimpleEncryptor(ASYMMETRIC_CIPHER, serverCert.getPublicKey(), configuration.getPrivateKeyAlias());
        initializeHttpClient(configuration, x509KeyManager, trustMgrs);
    }

    private void initializeHttpClient(OpenidmAccountStatusNotificationHandlerCfg configuration,
            X509KeyManager keyManager, TrustManager[] trustManagers) throws ConfigException, InitializationException {
        compatMode = configuration.getOpenidmCompatMode();
        openidmURI = getOpenIDMURI(configuration);
        Options options = Options.defaultOptions();
        options.set(HttpClientHandler.OPTION_LOADER, serviceLoader);
        options.set(HttpClientHandler.OPTION_MAX_CONNECTIONS, 16);
        boolean isHTTPS = "https".equalsIgnoreCase(openidmURI.getScheme());
        if (isHTTPS) {
            options.set(HttpClientHandler.OPTION_KEY_MANAGERS, new KeyManager[] { keyManager });
            options.set(HttpClientHandler.OPTION_TRUST_MANAGERS, trustManagers);
        }
        try {
            httpClientHandler = new HttpClientHandler(options);
        } catch (HttpApplicationException e) {
            logger.traceException(e, "Error when creating HTTP client handler");
            throw new InitializationException(ERR_OPENIDM_PWSYNC_INITIALIZATIONEXCEPTION.get(e.getMessage()));
        }
        client = new Client(httpClientHandler);
    }

    private void initializeLogFile(String logFileName) throws ConfigException {
        this.logFileName = logFileName;
        this.logFile = getFileForPath(logFileName);

        if (!logFile.exists()) {
            if (!logFile.mkdirs()) {
                throw new ConfigException(ERR_OPENIDM_PWSYNC_LOGFILE_UNABLE_TO_CREATE_DIRECTORY.get(logFileName));
            }
        } else if (!logFile.isDirectory()) {
            throw new ConfigException(ERR_OPENIDM_PWSYNC_LOGFILE_ALREADY_EXISTS.get(logFileName));
        }
    }

    private X509Certificate getServerCertificate(TrustManager[] trustMgrs,
            OpenidmAccountStatusNotificationHandlerCfg configuration) throws ConfigException {

        X509TrustManager trustMgr = (X509TrustManager) trustMgrs[0];
        String serverCertSubject = configuration.getCertificateSubjectDN().toString();
        for (X509Certificate cert : trustMgr.getAcceptedIssuers()) {
            String subjectX500Principal = cert.getSubjectX500Principal().getName(X500Principal.CANONICAL);
            if (serverCertSubject.equalsIgnoreCase(subjectX500Principal)) {
                return cert;
            }
        }
        throw new ConfigException(ERR_OPENIDM_PWSYNC_INVALID_SERVERKEYALIAS.get(serverCertSubject));
    }

    private TrustManager[] getTrustManagers(OpenidmAccountStatusNotificationHandlerCfg configuration)
            throws DirectoryException {
        DN trustMgrDN = configuration.getTrustManagerProviderDN();
        TrustManagerProvider<?> trustManagerProvider = DirectoryServer.getTrustManagerProvider(trustMgrDN);
        if (logger.isTraceEnabled()) {
            logger.trace("Trust Manager: %s, Server certificate subject: %s",
                    trustMgrDN, configuration.getCertificateSubjectDN());
        }
        return trustManagerProvider.getTrustManagers();
    }

    private X509KeyManager getKeyManager(OpenidmAccountStatusNotificationHandlerCfg configuration)
            throws DirectoryException, ConfigException {
        DN keyMgrDN = configuration.getKeyManagerProviderDN();
        if (keyMgrDN == null) {
            throw new ConfigException(ERR_OPENIDM_PWSYNC_NO_KEYMANAGER_PROVIDER.get());
        }
        KeyManagerProvider<?> keyManagerProvider = DirectoryServer.getKeyManagerProvider(keyMgrDN);
        KeyManager[] keyManagers = keyManagerProvider.getKeyManagers();
        X509KeyManager x509KeyManager = (X509KeyManager) keyManagers[0];

        // Client certificate nickname must be present in the keystore to ensure client certificate
        // will be retrieved.
        String certNickname = configuration.getSSLCertNickname();
        if (x509KeyManager.getPrivateKey(certNickname) == null) {
            throw new ConfigException(ERR_OPENIDM_PWSYNC_INVALID_CLIENT_CERT_NICKNAME.get(certNickname));
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Key Manager: %s, Client certificate nickname: %s", keyMgrDN, certNickname);
        }
        return KeyManagers.useSingleCertificate(certNickname, x509KeyManager);
    }

    /**
     * Retrieves the URI of OpenIDM service.
     * <p/>
     * Example: https://localhost:8181/openidm/managed/user
     *
     * @param configuration
     *            Plugin configuration.
     * @return URI corresponding to the OpenIDM service address.
     * @throws ConfigException
     *             if the configuration value has invalid URL syntax.
     */
    private URI getOpenIDMURI(OpenidmAccountStatusNotificationHandlerCfg configuration) throws ConfigException {
        try {
            return new URI(configuration.getOpenidmUrl());
        } catch (URISyntaxException ex) {
            logger.traceException(ex);
            throw new ConfigException(ERR_OPENIDM_PWSYNC_MALFORMEDURLEXCEPTION.get(configuration.getOpenidmUrl(),
                    ex.getMessage()), ex);
        }
    }

    @Override
    public void handleStatusNotification(AccountStatusNotification notification) {
        if (logger.isTraceEnabled()) {
            logger.trace("Received notification for user: " + notification.getUserDN());
        }
        OpenidmAccountStatusNotificationHandlerCfg config = currentConfig;
        HashMap<String, List<String>> returnedData = new HashMap<>();

        String userDN = String.valueOf(notification.getUserDN());
        Entry userEntry = notification.getUserEntry();

        Set<AttributeType> notificationAttrs = config.getAttributeType();
        for (AttributeType t : notificationAttrs) {
            for (Attribute a : userEntry.getAttribute(t)) {
                List<String> attrVals = new ArrayList<>();
                String attrName = a.getAttributeDescription().getAttributeType().getNameOrOID();
                for (ByteString v : a) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Adding end user attribute value " + v + " from attr " + attrName
                                + "to notification");
                    }
                    attrVals.add(v.toString());
                }
                returnedData.put(attrName, attrVals);
            }
        }

        AccountStatusNotificationType notifType = notification.getNotificationType();
        if (PASSWORD_CHANGED != notifType && PASSWORD_RESET != notifType) {
            return;
        }
        List<String> newPasswords = notification.getNotificationProperties().get(NEW_PASSWORD);
        byte passwordEvent = notifType == PASSWORD_CHANGED ? PWD_CHANGED : PWD_RESET;
        processOpenIDMNotification(passwordEvent, userDN, newPasswords, returnedData);

        if (logger.isTraceEnabled()) {
            logger.trace("Finished to process the notification to IDM for user: " + notification.getUserDN());
        }
    }

    /**
     * Returns the patch value for provided passwords as a map of fields.
     *
     * @param newPasswords
     * @return the patch as a map of fields
     * @throws JsonCryptoException
     *             if encryption fails
     */
    private Map<String, Object> buildPatchForPasswords(final List<String> newPasswords) throws JsonCryptoException {
        final Map<String, Object> patchFields = new HashMap<>();
        JsonValue crypto = new JsonCrypto(encryptor.getType(), encryptor.encrypt(new JsonValue(newPasswords.get(0))))
                .toJsonValue();

        switch (compatMode) {
        case V2:
            // { "replace": "/password", "value": {"$crypto" :{}} }
            patchFields.put("replace", new JsonPointer(currentConfig.getPasswordAttribute()).toString());
            patchFields.put("value", crypto.asMap());
            break;
        case V3:
            // { "operation": "replace", "field": "/password", "value": {"$crypto" :{}} }
            patchFields.put("operation", "replace");
            patchFields.put("field", new JsonPointer(currentConfig.getPasswordAttribute()).toString());
            patchFields.put("value", crypto.asMap());
            break;
        default:
            throw new IllegalArgumentException("Unknown compatibility mode: " + compatMode);
        }
        return patchFields;
    }

    /**
     * Processes a password change notification and sends it to OpenIDM.
     *
     * @param passwordEvent
     *            A byte indicating if it's a change or reset.
     * @param userDN
     *            The user distinguished name as a string.
     * @param newPasswords
     *            the list of new passwords (there may be more than 1).
     * @param returnedData
     *            the additional attributes and values of the user entry.
     */
    private void processOpenIDMNotification(byte passwordEvent, String userDN, List<String> newPasswords,
            Map<String, List<String>> returnedData) {
        if (logger.isTraceEnabled()) {
            logger.trace("Process notification: user %s 's password %s. Additional data: %s", userDN,
                    (passwordEvent == PWD_CHANGED ? "changed" : "reset"), returnedData);
        }

        try {
            String paramPrefix = compatMode == OpenidmCompatMode.V2 ? "_" : "";
            Map<String, String> queryParameters =
                    buildQueryParameters(paramPrefix, userDN, passwordEvent, returnedData);
            Map<String, Object> passwordsPatch = buildPatchForPasswords(newPasswords);

            if (interval > 0) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Pushing modification to local storage for user: %s", userDN);
                }
                Map<String, Object> request = new HashMap<>(2);
                request.put("queryParameter", queryParameters);
                request.put("patch", passwordsPatch);
                try {
                    StringWriter writer = new StringWriter();
                    mapper.writeValue(writer, request);
                    queue.push(userDN, writer.toString());
                } catch (Exception ex) {
                    logger.traceException(ex, "Error when pushing modification to queue");
                }
            } else {
                if (logger.isTraceEnabled()) {
                    logger.trace("Posting REST request to IDM for user: %s", userDN);
                }
                postRequestToIDM(queryParameters, passwordsPatch);
            }
        } catch (Exception ex) {
            logger.traceException(ex, "Error when processing modification for user: %s", userDN);
        }

    }

    private Map<String, String> buildQueryParameters(String paramPrefix, String userDN, byte passwordEvent,
            Map<String, List<String>> returnedData) {
        Map<String, String> queryParameter = new HashMap<>(returnedData.size());
        queryParameter.put(paramPrefix + "passwordEvent", Byte.toString(passwordEvent));
        queryParameter.put(paramPrefix + "resourceHostname", hostname);
        queryParameter.put(paramPrefix + "messageTimestamp", Long.toString(System.currentTimeMillis()));
        queryParameter.put(paramPrefix + "resourceAccountDN", userDN);
        for (Map.Entry<String, List<String>> e : returnedData.entrySet()) {
            if (e.getValue().size() == 1) {
                queryParameter.put(e.getKey(), e.getValue().get(0));
            } else if (e.getValue().size() > 1) {
                StringBuilder listString = new StringBuilder();
                for (String s : e.getValue()) {
                    listString.append(s).append("\t");
                }
                queryParameter.put(e.getKey(), listString.toString());
            }
        }
        return queryParameter;
    }

    /** Returns {@code true} if request is successful, {@code false} otherwise. */
    @SuppressWarnings("resource")
    private Promise<Boolean, NeverThrowsException> postRequestToIDM(Map<String, String> queryParameter,
            Map<String, Object> passwordsPatch) {

        final Request request = buildHttpRequest(queryParameter, passwordsPatch);

        if (logger.isTraceEnabled()) {
            try {
                logger.trace("Posting to IDM url=[%s], query params=[%s], json patch=[%s]", request.getUri(),
                        request.getForm(), request.getEntity().getString());
            } catch (IOException e) {
                // ignore
            }
        }

        return client.send(request).then(new Function<Response, Boolean, NeverThrowsException>() {
            @Override
            public Boolean apply(Response response) {
                try {
                    Status status = response.getStatus();
                    if (status.isSuccessful()) {
                        if (logger.isTraceEnabled()) {
                            logger.trace("Success when posting to IDM. Message received: %s", response.getEntity());
                        }
                        return true;
                    }
                    if (logger.isTraceEnabled()) {
                        final String message;
                        if (status.equals(Status.UNAUTHORIZED)) {
                            message = "Access non authorized by the server, check your credentials";
                        } else if (status.equals(Status.NOT_FOUND)) {
                            message = "HTTP response: object not found";
                        } else if (status.equals(Status.CONFLICT)) {
                            message = "HTTP response: conflict (matches multiple objects or patch failure)";
                        } else {
                            message = "Unexpected status";
                        }
                        Exception cause = response.getCause();
                        logger.trace("Failure when posting to IDM. Status: %s. Message: %s. Cause: %s", status,
                                message, cause != null ? cause : "N/A");
                    }
                    return false;
                } finally {
                    request.close();
                    response.close();
                }
            }
        });
    }

    private Request buildHttpRequest(Map<String, String> queryParameter, Map<String, Object> passwordsPatch) {
        Request request = new Request();
        request.setMethod("POST");
        request.setUri(openidmURI);
        Headers headers = request.getHeaders();
        headers.add("X-Requested-With", "OpenDJPlugin");
        if (currentConfig.getOpenidmUsername() != null && currentConfig.getOpenidmPassword() != null) {
            headers.add("X-OpenIDM-Username", currentConfig.getOpenidmUsername());
            headers.add("X-OpenIDM-Password", currentConfig.getOpenidmPassword());
        }
        final Form form = new Form();
        form.add("_action", "patch");
        form.add("_queryId", currentConfig.getQueryId());
        for (Map.Entry<String, String> e : queryParameter.entrySet()) {
            form.add(e.getKey(), e.getValue());
        }
        form.appendRequestQuery(request);

        List<Object> finalPatch = new ArrayList<>(1);
        finalPatch.add(passwordsPatch);
        request.getEntity().setJson(finalPatch);
        return request;
    }

    /**
     * Returns the listener name.
     *
     * @return The name of the listener.
     */
    @Override
    public String getShutdownListenerName() {
        return THREADNAME;
    }

    /**
     * Processes a server shutdown. If the background thread is running it needs to be interrupted so it can read the
     * stop request variable and exit.
     *
     * @param reason
     *            The reason message for the shutdown.
     */
    @Override
    public void processServerShutdown(LocalizableMessage reason) {
        stopRequested = true;

        // Wait for back ground thread to terminate
        while (backgroundThread != null && backgroundThread.isAlive()) {
            try {
                // Interrupt if its sleeping
                backgroundThread.interrupt();
                backgroundThread.join();
            } catch (InterruptedException ex) {
                // Expected.
            }
        }
        DirectoryServer.deregisterShutdownListener(this);
        queue.close();
        close(httpClientHandler);
        backgroundThread = null;
    }

    /**
     * Returns the interval time converted to milliseconds.
     *
     * @return The interval time for the background thread.
     */
    private long getInterval() {
        return interval * 1000;
    }

    /** Starts a background thread to process locally stored changes asynchronously. */
    private void initializeBackGroundProcessing() {
        if (backgroundThread == null) {
            DirectoryServer.registerShutdownListener(this);
            stopRequested = false;
            backgroundThread = new BackGroundThread();
            backgroundThread.start();
        }
    }

    /**
     * Used by the background thread to determine if it should exit.
     *
     * @return Returns <code>true</code> if the background thread should exit.
     */
    private boolean isShuttingDown() {
        return stopRequested;
    }

    /**
     * The background processing thread.
     * <p>
     * Wakes up after sleeping for a configurable interval and sends all changes stored locally to OpenIDM.
     */
    private class BackGroundThread extends DirectoryThread {
        BackGroundThread() {
            super(THREADNAME);
        }

        /** Run method for the background thread. */
        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            while (!isShuttingDown()) {
                try {
                    long sleepInterval = getInterval();
                    if (logger.isTraceEnabled()) {
                        logger.trace("Background thread - sleeping for " + sleepInterval + " milliseconds");
                    }
                    sleep(sleepInterval);
                } catch (InterruptedException e) {
                    continue;
                } catch (Exception e) {
                    logger.traceException(e, "Error when sleeping");
                }

                if (logger.isTraceEnabled()) {
                    logger.trace("Queue size: " + queue.size() + " items");
                }
                if (queue.size() > 0) {
                    String[] request = null;
                    try {
                        while ((request = queue.poll()) != null) {
                            Map<String, Object> item = mapper.readValue(request[1], Map.class);
                            Map<String, String> queryParameter = (Map<String, String>) item.get("queryParameter");
                            Map<String, Object> passwordsPatch = (Map<String, Object>) item.get("patch");
                            final String[] currentRequest = request;
                            postRequestToIDM(queryParameter, passwordsPatch)
                                .thenOnResult(new ResultHandler<Boolean>() {
                                    @Override
                                    public void handleResult(Boolean isSuccess) {
                                        if (!isSuccess) {
                                            try {
                                                queue.push(currentRequest[0], currentRequest[1]);
                                            } catch (IOException ex) {
                                                logger.traceException(ex, "Error when pushing back to queue a request");
                                            }
                                        }
                                    }
                                });
                        }
                    } catch (Exception e) {
                        logger.traceException(e, "Error when processing a queue element");
                    }
                }
            }
        }
    }
}
