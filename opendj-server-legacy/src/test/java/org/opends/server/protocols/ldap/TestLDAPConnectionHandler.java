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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 * Portions Copyright 2025 3A Systems, LLC.
 */
package org.opends.server.protocols.ldap;

import static org.opends.server.config.ConfigConstants.*;
import static org.testng.Assert.*;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.server.config.server.LDAPConnectionHandlerCfg;
import org.opends.server.api.ClientConnection;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.reactive.LDAPConnectionHandler2;
import org.opends.server.types.Attributes;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.SSLClientAuthPolicy;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class TestLDAPConnectionHandler extends LdapTestCase {

  private static LocalizableMessage reasonMsg= LocalizableMessage.raw("Don't need a reason.");

  /**
   * Once-only initialization.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @BeforeClass
  public void setUp() throws Exception {
    // This test suite depends on having the schema available, so we'll
    // start the server.
    TestCaseUtils.startServer();
  }

  /**
   *  Creates two handlers, one which is SSL type. Then change some values via the setter
   *  methods.
   *
   * @throws Exception if the handler cannot be instantiated.
   */
  @Test
  public void testLDAPConnectionHandler() throws Exception {
    Entry LDAPHandlerEntry=null;

    LDAPHandlerEntry=TestCaseUtils.makeEntry(
        "dn: cn=LDAP Connection Handler,cn=Connection Handlers,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-connection-handler",
        "objectClass: ds-cfg-ldap-connection-handler",
        "cn: LDAP Connection Handler",
        "ds-cfg-java-class: org.forgerock.opendj.reactive.LDAPConnectionHandler2",
        "ds-cfg-enabled: true",
        "ds-cfg-listen-address: 0.0.0.0",
        "ds-cfg-accept-backlog: 128",
        "ds-cfg-allow-ldap-v2: false",
        "ds-cfg-keep-stats: true",
        "ds-cfg-use-tcp-keep-alive: true",
        "ds-cfg-use-tcp-no-delay: true",
        "ds-cfg-allow-tcp-reuse-address: true",
        "ds-cfg-send-rejection-notice: true",
        "ds-cfg-max-request-size: 10 megabytes",
        "ds-cfg-num-request-handlers: 2",
        "ds-cfg-allow-start-tls: false",
        "ds-cfg-use-ssl: false",
        "ds-cfg-ssl-client-auth-policy: optional",
        "ds-cfg-ssl-cert-nickname: server-cert",
        "ds-cfg-key-manager-provider: cn=JKS,cn=Key Manager Providers,cn=config",
        "ds-cfg-trust-manager-provider: cn=JKS,cn=Trust Manager Providers,cn=config");
    LDAPConnectionHandler2 LDAPConnHandler=getLDAPHandlerInstance(LDAPHandlerEntry);
    LDAPConnHandler.allowLDAPv2();
    LDAPConnHandler.allowStartTLS();
    LDAPConnHandler.toString(new StringBuilder());
    LDAPConnHandler.toString();
    LDAPStatistics tracker=LDAPConnHandler.getStatTracker();
    Map<String,String> alerts = LDAPConnHandler.getAlerts();
    String c=LDAPConnHandler.getClassName();
    DN dn = LDAPConnHandler.getComponentEntryDN();
    Collection<String> cips = LDAPConnHandler.getEnabledSSLCipherSuites();
    Collection<String> protos = LDAPConnHandler.getEnabledSSLProtocols();
    int maxReqSize = LDAPConnHandler.getMaxRequestSize();
    assertEquals(maxReqSize,10*1000*1000);
    String shutListName=LDAPConnHandler.getShutdownListenerName();
    SSLClientAuthPolicy policy = LDAPConnHandler.getSSLClientAuthPolicy();
    Collection<ClientConnection> cons=LDAPConnHandler.getClientConnections();
    LDAPConnHandler.processServerShutdown(reasonMsg);
    //Reset some things for the SSL handler
    Attribute useSSL=Attributes.create(ATTR_USE_SSL, String.valueOf(false));
    Attribute startTls=Attributes.create(ATTR_ALLOW_STARTTLS, String.valueOf(false));
    LDAPHandlerEntry.removeAttribute(Attributes.empty(ATTR_LISTEN_PORT), null);
    LDAPHandlerEntry.removeAttribute(useSSL, null);
    LDAPHandlerEntry.removeAttribute(startTls, null);
    Attribute useSSL1=Attributes.create(ATTR_USE_SSL, String.valueOf(true));
    Attribute startTls1=Attributes.create(ATTR_ALLOW_STARTTLS, String.valueOf(false));
    LDAPHandlerEntry.addAttribute(useSSL1,null);
    LDAPHandlerEntry.addAttribute(startTls1,null);
    LDAPConnectionHandler2 LDAPSConnHandler = getLDAPHandlerInstance(LDAPHandlerEntry);
    LDAPSConnHandler.finalizeConnectionHandler(reasonMsg);
    LDAPConnHandler.processServerShutdown(reasonMsg);
  }

  /**
   *  Start a handler an then give its hasAcceptableConfiguration a Entry with
   *  numerous invalid cases and single-valued attrs with duplicate values.
   *
   * @throws Exception if handler cannot be instantiated or the configuration is
   *                   accepted.
   */
  @Test(expectedExceptions = ConfigException.class)
  public void testBadLDAPConnectionHandlerConfiguration() throws Exception
  {
    Entry BadHandlerEntry=TestCaseUtils.makeEntry(
        "dn: cn=LDAP Connection Handler,cn=Connection Handlers,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-connection-handler",
        "objectClass: ds-cfg-ldap-connection-handler",
        "cn: LDAP Connection Handler",
        "ds-cfg-java-class: org.opends.server.protocols.ldap.LDAPConnectionHandler",
        "ds-cfg-enabled: true",
        "ds-cfg-listen-address: 0.0.0.0",
        "ds-cfg-accept-backlog: 128",
        "ds-cfg-allow-ldap-v2: false",
        "ds-cfg-keep-stats: false",
        "ds-cfg-use-tcp-keep-alive: true",
        "ds-cfg-use-tcp-no-delay: true",
        "ds-cfg-allow-tcp-reuse-address: true",
        "ds-cfg-send-rejection-notice: true",
        "ds-cfg-max-request-size: 5 megabytes",
        "ds-cfg-num-request-handlers: 2",
        "ds-cfg-allow-start-tls: false",
        "ds-cfg-use-ssl: false",
        "ds-cfg-ssl-client-auth-policy: optional",
        "ds-cfg-ssl-cert-nickname: server-cert",
        "ds-cfg-key-manager-provider: cn=JKS,cn=Key Manager Providers,cn=config",
        "ds-cfg-trust-manager-provider: cn=JKS,cn=Trust Manager Providers,cn=config");

    // Add some invalid attrs and some duplicate attrs
    Attribute a2=Attributes.create(ATTR_LISTEN_PORT, String.valueOf(389));
    Attribute a2a=Attributes.create(ATTR_LISTEN_PORT, String.valueOf(70000));
    Attribute a3=Attributes.create(ATTR_LISTEN_ADDRESS, "localhost");
    Attribute a3a=Attributes.create(ATTR_LISTEN_ADDRESS, "FAFASFSDFSADFASDFSDFSDAFAS");
    Attribute a4=Attributes.create(ATTR_ACCEPT_BACKLOG, String.valueOf(Long.MAX_VALUE));
    Attribute a5=Attributes.create(ATTR_ALLOWED_CLIENT, "129.800.990.45");
    Attribute a6=Attributes.create(ATTR_DENIED_CLIENT, "129.");
    Attribute a7=Attributes.create(ATTR_ALLOW_LDAPV2, "45");
    Attribute a8=Attributes.create(ATTR_KEEP_LDAP_STATS, "45");
    Attribute a9=Attributes.create(ATTR_SEND_REJECTION_NOTICE, "45");
    Attribute a10=Attributes.create(ATTR_USE_TCP_KEEPALIVE, "45");
    Attribute a11=Attributes.create(ATTR_USE_TCP_NODELAY, "45");
    Attribute a12=Attributes.create(ATTR_ALLOW_REUSE_ADDRESS, "45");
    Attribute a13=Attributes.create(ATTR_MAX_REQUEST_SIZE, "45 FLUBBERBYTES");
    Attribute a14=Attributes.create(ATTR_USE_SSL, "45");
    Attribute a15=Attributes.create(ATTR_ALLOW_STARTTLS, "45");
    BadHandlerEntry.addAttribute(a2, null);
    BadHandlerEntry.addAttribute(a3, null);
    BadHandlerEntry.addAttribute(a2a, null);
    BadHandlerEntry.addAttribute(a3a, null);
    BadHandlerEntry.addAttribute(a4, null);
    BadHandlerEntry.addAttribute(a5, null);
    BadHandlerEntry.addAttribute(a6, null);
    BadHandlerEntry.addAttribute(a7, null);
    BadHandlerEntry.addAttribute(a8, null);
    BadHandlerEntry.addAttribute(a9, null);
    BadHandlerEntry.addAttribute(a10, null);
    BadHandlerEntry.addAttribute(a11, null);
    BadHandlerEntry.addAttribute(a12, null);
    BadHandlerEntry.addAttribute(a13, null);
    BadHandlerEntry.addAttribute(a14, null);
    BadHandlerEntry.addAttribute(a15, null);

    LdapTestCase.getConfiguration(BadHandlerEntry);
  }

  /**
   * Create handler and then change most of its values and see if
   * it is acceptable and applied.
   * @throws Exception if handler cannot be instantiated.
   */
  @Test
  public void testGoodLDAPConnectionHandlerConfiguration() throws Exception
  {
    Entry GoodHandlerEntry=TestCaseUtils.makeEntry(
        "dn: cn=LDAP Connection Handler,cn=Connection Handlers,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-connection-handler",
        "objectClass: ds-cfg-ldap-connection-handler",
        "cn: LDAP Connection Handler",
        "ds-cfg-java-class: org.forgerock.opendj.reactive.LDAPConnectionHandler2",
        "ds-cfg-enabled: true",
        "ds-cfg-listen-address: 0.0.0.0",
        "ds-cfg-accept-backlog: 128",
        "ds-cfg-allow-ldap-v2: false",
        "ds-cfg-keep-stats: false",
        "ds-cfg-use-tcp-keep-alive: true",
        "ds-cfg-use-tcp-no-delay: true",
        "ds-cfg-allow-tcp-reuse-address: true",
        "ds-cfg-send-rejection-notice: true",
        "ds-cfg-max-request-size: 5 megabytes",
        "ds-cfg-num-request-handlers: 2",
        "ds-cfg-allow-start-tls: false",
        "ds-cfg-use-ssl: true",
        "ds-cfg-ssl-client-auth-policy: optional",
        "ds-cfg-ssl-cert-nickname: server-cert",
        "ds-cfg-key-manager-provider: cn=JKS,cn=Key Manager Providers,cn=config",
        "ds-cfg-trust-manager-provider: cn=JKS,cn=Trust Manager Providers,cn=config");
    LDAPConnectionHandler2 LDAPConnHandler=getLDAPHandlerInstance(GoodHandlerEntry);
    //Make attrTypes to remove
    Schema schema = DirectoryServer.getInstance().getServerContext().getSchema();
    AttributeType at0=schema.getAttributeType(ATTR_LISTEN_PORT);
//    AttributeType at1=DirectoryServer.getAttributeType(ATTR_LISTEN_ADDRESS, true);
//    Attribute rAttr1=new Attribute(at1);
//    GoodHandlerEntry.removeAttribute(rAttr1, null);
    AttributeType at2=schema.getAttributeType(ATTR_ALLOW_LDAPV2);
    AttributeType at3=schema.getAttributeType(ATTR_ALLOW_LDAPV2);
    AttributeType at4=schema.getAttributeType(ATTR_KEEP_LDAP_STATS);
    AttributeType at5=schema.getAttributeType(ATTR_SEND_REJECTION_NOTICE);
    AttributeType at6=schema.getAttributeType(ATTR_USE_TCP_KEEPALIVE);
    AttributeType at7=schema.getAttributeType(ATTR_USE_TCP_NODELAY);
    AttributeType at8=schema.getAttributeType(ATTR_ALLOW_REUSE_ADDRESS);
    AttributeType at9=schema.getAttributeType(ATTR_USE_SSL);
    AttributeType at10=schema.getAttributeType(ATTR_ALLOW_STARTTLS);
    AttributeType at11=schema.getAttributeType(ATTR_MAX_REQUEST_SIZE);
    AttributeType at12=schema.getAttributeType(ATTR_ACCEPT_BACKLOG);
    //Remove them
    Attribute rAttr0=Attributes.empty(at0);
    GoodHandlerEntry.removeAttribute(rAttr0, null);

    Attribute rAttr2=Attributes.empty(at2);
    GoodHandlerEntry.removeAttribute(rAttr2, null);
    Attribute rAttr3=Attributes.empty(at3);
    GoodHandlerEntry.removeAttribute(rAttr3, null);
    Attribute rAttr4=Attributes.empty(at4);
    GoodHandlerEntry.removeAttribute(rAttr4, null);
    Attribute rAttr5=Attributes.empty(at5);
    GoodHandlerEntry.removeAttribute(rAttr5, null);
    Attribute rAttr6=Attributes.empty(at6);
    GoodHandlerEntry.removeAttribute(rAttr6, null);
    Attribute rAttr7=Attributes.empty(at7);
    GoodHandlerEntry.removeAttribute(rAttr7, null);
    Attribute rAttr8=Attributes.empty(at8);
    Attribute rAttr9=Attributes.empty(at9);
    Attribute rAttr10=Attributes.empty(at10);
    Attribute rAttr11=Attributes.empty(at11);
    Attribute rAttr12=Attributes.empty(at12);
    GoodHandlerEntry.removeAttribute(rAttr8, null);
    GoodHandlerEntry.removeAttribute(rAttr9, null);
    GoodHandlerEntry.removeAttribute(rAttr10, null);
    GoodHandlerEntry.removeAttribute(rAttr11, null);
    GoodHandlerEntry.removeAttribute(rAttr12, null);
    //Make new AttrTypes with different values
    long newPort = TestCaseUtils.findFreePort();
    Attribute a2=Attributes.create(ATTR_LISTEN_PORT, String.valueOf(newPort));
    //uncomment if want to test listen address
//    Attribute a3=new Attribute(ATTR_LISTEN_ADDRESS, "localhost");
    Attribute a4=Attributes.create(ATTR_ACCEPT_BACKLOG, String.valueOf(25));
    Attribute a5=Attributes.create(ATTR_ALLOWED_CLIENT, "129.56.56.45");
    Attribute a6=Attributes.create(ATTR_DENIED_CLIENT, "129.*.*.90");
    Attribute a7=Attributes.create(ATTR_ALLOW_LDAPV2, "true");
    Attribute a8=Attributes.create(ATTR_KEEP_LDAP_STATS, "true");
    Attribute a9=Attributes.create(ATTR_SEND_REJECTION_NOTICE, "false");
    Attribute a10=Attributes.create(ATTR_USE_TCP_KEEPALIVE, "false");
    Attribute a11=Attributes.create(ATTR_USE_TCP_NODELAY, "false");
    Attribute a12=Attributes.create(ATTR_ALLOW_REUSE_ADDRESS, "false");
    Attribute a13=Attributes.create(ATTR_MAX_REQUEST_SIZE, "45 kb");
    Attribute a14=Attributes.create(ATTR_USE_SSL, "false");
    Attribute a15=Attributes.create(ATTR_ALLOW_STARTTLS, "true");
    //Add them
    GoodHandlerEntry.addAttribute(a2, null);
//    GoodHandlerEntry.addAttribute(a3, null);
    GoodHandlerEntry.addAttribute(a4, null);
    GoodHandlerEntry.addAttribute(a5, null);
    GoodHandlerEntry.addAttribute(a6, null);
    GoodHandlerEntry.addAttribute(a7, null);
    GoodHandlerEntry.addAttribute(a8, null);
    GoodHandlerEntry.addAttribute(a9, null);
    GoodHandlerEntry.addAttribute(a10, null);
    GoodHandlerEntry.addAttribute(a11, null);
    GoodHandlerEntry.addAttribute(a12, null);
    GoodHandlerEntry.addAttribute(a13, null);
    GoodHandlerEntry.addAttribute(a14, null);
    GoodHandlerEntry.addAttribute(a15, null);
    List<LocalizableMessage> reasons = new LinkedList<>();
    LDAPConnectionHandlerCfg config = LdapTestCase.getConfiguration(GoodHandlerEntry);
    //see if we're ok
    boolean ret=LDAPConnHandler.isConfigurationChangeAcceptable(config, reasons);
    assertTrue(ret);
    //apply it
    LDAPConnHandler.applyConfigurationChange(config);
    LDAPConnHandler.finalizeConnectionHandler(reasonMsg);
  }
}
