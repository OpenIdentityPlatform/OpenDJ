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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.server.protocols.ldap;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Collection;

import static org.opends.server.config.ConfigConstants.*;

import org.opends.server.admin.std.server.LDAPConnectionHandlerCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.*;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;
import static org.testng.Assert.*;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class TestLDAPConnectionHandler extends LdapTestCase {

	private static String reasonMsg="Don't need a reason.";

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

	@Test()
	/**
	 *  Creates two handlers, one which is SSL type. Then change some values via the setter
	 *  methods.
	 *  
	 * @throws Exception if the handler cannot be instantiated.
	 */
	public void testLDAPConnectionHandler() throws Exception {
		Entry LDAPHandlerEntry=null;

		LDAPHandlerEntry=TestCaseUtils.makeEntry(
				"dn: cn=LDAP Connection Handler,cn=Connection Handlers,cn=config",
				"objectClass: top",
				"objectClass: ds-cfg-connection-handler",
				"objectClass: ds-cfg-ldap-connection-handler",
				"cn: LDAP Connection Handler",
				"ds-cfg-connection-handler-class: org.opends.server.protocols.ldap.LDAPConnectionHandler",
				"ds-cfg-connection-handler-enabled: true",
				"ds-cfg-listen-address: 0.0.0.0",
				"ds-cfg-accept-backlog: 128",
				"ds-cfg-allow-ldapv2: false",
				"ds-cfg-keep-stats: false",
				"ds-cfg-use-tcp-keepalive: true",
				"ds-cfg-use-tcp-nodelay: true",
				"ds-cfg-allow-tcp-reuse-address: true",
				"ds-cfg-send-rejection-notice: true",
				"ds-cfg-max-request-size: 5 megabytes",
				"ds-cfg-num-request-handlers: 2",
				"ds-cfg-allow-start-tls: false",
				"ds-cfg-use-ssl: false",
				"ds-cfg-ssl-client-auth-policy: optional",
		"ds-cfg-ssl-cert-nickname: server-cert");
		LDAPConnectionHandler LDAPConnHandler=getLDAPHandlerInstance(LDAPHandlerEntry);
		LDAPConnHandler.allowLDAPv2();
		LDAPConnHandler.allowStartTLS();
		LDAPConnHandler.keepStats();
		LDAPConnHandler.toString(new StringBuilder());
		LDAPConnHandler.toString();
		LDAPStatistics tracker=LDAPConnHandler.getStatTracker();
		LinkedHashMap<String,String> alerts = LDAPConnHandler.getAlerts();
		String c=LDAPConnHandler.getClassName();
		DN dn = LDAPConnHandler.getComponentEntryDN();
		String[] cips = LDAPConnHandler.getEnabledSSLCipherSuites();
		String[] protos = LDAPConnHandler.getEnabledSSLProtocols();
		int maxReqSize = LDAPConnHandler.getMaxRequestSize();
		String shutListName=LDAPConnHandler.getShutdownListenerName();
		SSLClientAuthPolicy policy = LDAPConnHandler.getSSLClientAuthPolicy();
		Collection<ClientConnection> cons=LDAPConnHandler.getClientConnections();
		LDAPConnHandler.processServerShutdown(reasonMsg);
		//Reset some things for the SSL handler	
		Attribute useSSL=new Attribute(ATTR_USE_SSL, String.valueOf(false));
		Attribute startTls=new Attribute(ATTR_ALLOW_STARTTLS, String.valueOf(false));
		AttributeType attrType=DirectoryServer.getAttributeType(ATTR_LISTEN_PORT, true);
		Attribute a=new Attribute(attrType);
		LDAPHandlerEntry.removeAttribute(a, null);
		LDAPHandlerEntry.removeAttribute(useSSL, null);
		LDAPHandlerEntry.removeAttribute(startTls, null);
		Attribute useSSL1=new Attribute(ATTR_USE_SSL, String.valueOf(true));
		Attribute startTls1=new Attribute(ATTR_ALLOW_STARTTLS, String.valueOf(true));
		LDAPHandlerEntry.addAttribute(useSSL1,null);
		LDAPHandlerEntry.addAttribute(startTls1,null);
		LDAPConnectionHandler LDAPSConnHandler = getLDAPHandlerInstance(LDAPHandlerEntry);
		LDAPSConnHandler.finalizeConnectionHandler(reasonMsg, true);
		LDAPConnHandler.processServerShutdown(reasonMsg);
	}

	@Test(expectedExceptions=ConfigException.class)
	/**
	 *  Start a handler an then give its hasAcceptableConfiguration a ConfigEntry with
	 *  numerous invalid cases and single-valued attrs with duplicate values.
	 *  
	 * @throws Exception if handler cannot be instantiated or the configuration is 
	 *                   accepted.
	 */
	public void testBadLDAPConnectionHandlerConfiguration() throws Exception
	{
		Entry BadHandlerEntry=TestCaseUtils.makeEntry(
				"dn: cn=LDAP Connection Handler,cn=Connection Handlers,cn=config",
				"objectClass: top",
				"objectClass: ds-cfg-connection-handler",
				"objectClass: ds-cfg-ldap-connection-handler",
				"cn: LDAP Connection Handler",
				"ds-cfg-connection-handler-class: org.opends.server.protocols.ldap.LDAPConnectionHandler",
				"ds-cfg-connection-handler-enabled: true",
				"ds-cfg-listen-address: 0.0.0.0",
				"ds-cfg-accept-backlog: 128",
				"ds-cfg-allow-ldapv2: false",
				"ds-cfg-keep-stats: false",
				"ds-cfg-use-tcp-keepalive: true",
				"ds-cfg-use-tcp-nodelay: true",
				"ds-cfg-allow-tcp-reuse-address: true",
				"ds-cfg-send-rejection-notice: true",
				"ds-cfg-max-request-size: 5 megabytes",
				"ds-cfg-num-request-handlers: 2",
				"ds-cfg-allow-start-tls: false",
				"ds-cfg-use-ssl: false",
				"ds-cfg-ssl-client-auth-policy: optional",
		"ds-cfg-ssl-cert-nickname: server-cert");
		
    // Add some invalid attrs and some duplicate attrs 
		Attribute a2=new Attribute(ATTR_LISTEN_PORT, String.valueOf(389));
		Attribute a2a=new Attribute(ATTR_LISTEN_PORT, String.valueOf(70000));
		Attribute a3=new Attribute(ATTR_LISTEN_ADDRESS, "localhost");
		Attribute a3a=new Attribute(ATTR_LISTEN_ADDRESS, "FAFASFSDFSADFASDFSDFSDAFAS");
		Attribute a4=new Attribute(ATTR_ACCEPT_BACKLOG, String.valueOf(Long.MAX_VALUE));
		Attribute a5=new Attribute(ATTR_ALLOWED_CLIENT, "129.800.990.45");
		Attribute a6=new Attribute(ATTR_DENIED_CLIENT, "129.");
		Attribute a7=new Attribute(ATTR_ALLOW_LDAPV2, "45");
		Attribute a8=new Attribute(ATTR_KEEP_LDAP_STATS, "45");
		Attribute a9=new Attribute(ATTR_SEND_REJECTION_NOTICE, "45");
		Attribute a10=new Attribute(ATTR_USE_TCP_KEEPALIVE, "45");
		Attribute a11=new Attribute(ATTR_USE_TCP_NODELAY, "45");
		Attribute a12=new Attribute(ATTR_ALLOW_REUSE_ADDRESS, "45");
		Attribute a13=new Attribute(ATTR_MAX_REQUEST_SIZE, "45 FLUBBERBYTES");
		Attribute a14=new Attribute(ATTR_USE_SSL, "45");
		Attribute a15=new Attribute(ATTR_ALLOW_STARTTLS, "45");
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
	@Test()
	public void testGoodLDAPConnectionHandlerConfiguration() throws Exception
	{
		Entry GoodHandlerEntry=TestCaseUtils.makeEntry(
				"dn: cn=LDAP Connection Handler,cn=Connection Handlers,cn=config",
				"objectClass: top",
				"objectClass: ds-cfg-connection-handler",
				"objectClass: ds-cfg-ldap-connection-handler",
				"cn: LDAP Connection Handler",
				"ds-cfg-connection-handler-class: org.opends.server.protocols.ldap.LDAPConnectionHandler",
				"ds-cfg-connection-handler-enabled: true",
				"ds-cfg-listen-address: 0.0.0.0",
				"ds-cfg-accept-backlog: 128",
				"ds-cfg-allow-ldapv2: false",
				"ds-cfg-keep-stats: false",
				"ds-cfg-use-tcp-keepalive: true",
				"ds-cfg-use-tcp-nodelay: true",
				"ds-cfg-allow-tcp-reuse-address: true",
				"ds-cfg-send-rejection-notice: true",
				"ds-cfg-max-request-size: 5 megabytes",
				"ds-cfg-num-request-handlers: 2",
				"ds-cfg-allow-start-tls: false",
				"ds-cfg-use-ssl: true",
				"ds-cfg-ssl-client-auth-policy: optional",
		"ds-cfg-ssl-cert-nickname: server-cert");
		LDAPConnectionHandler LDAPConnHandler=getLDAPHandlerInstance(GoodHandlerEntry);
		//Make attrTypes to remove
		AttributeType at0=DirectoryServer.getAttributeType(ATTR_LISTEN_PORT, true);
//		AttributeType at1=DirectoryServer.getAttributeType(ATTR_LISTEN_ADDRESS, true);
//		Attribute rAttr1=new Attribute(at1);
//		GoodHandlerEntry.removeAttribute(rAttr1, null);
		AttributeType at2=DirectoryServer.getAttributeType(ATTR_ALLOW_LDAPV2, true);
		AttributeType at3=DirectoryServer.getAttributeType(ATTR_ALLOW_LDAPV2, true);
		AttributeType at4=DirectoryServer.getAttributeType(ATTR_KEEP_LDAP_STATS, true);
		AttributeType at5=DirectoryServer.getAttributeType(ATTR_SEND_REJECTION_NOTICE,true);
		AttributeType at6=DirectoryServer.getAttributeType(ATTR_USE_TCP_KEEPALIVE,true);
		AttributeType at7=DirectoryServer.getAttributeType(ATTR_USE_TCP_NODELAY,true);
		AttributeType at8=DirectoryServer.getAttributeType(ATTR_ALLOW_REUSE_ADDRESS,true);
		AttributeType at9=DirectoryServer.getAttributeType(ATTR_USE_SSL,true);
		AttributeType at10=DirectoryServer.getAttributeType(ATTR_ALLOW_STARTTLS,true);
		AttributeType at11=DirectoryServer.getAttributeType(ATTR_MAX_REQUEST_SIZE,true);
		AttributeType at12=DirectoryServer.getAttributeType(ATTR_ACCEPT_BACKLOG,true);
		//Remove them
		Attribute rAttr0=new Attribute(at0);
		GoodHandlerEntry.removeAttribute(rAttr0, null);

		Attribute rAttr2=new Attribute(at2);
		GoodHandlerEntry.removeAttribute(rAttr2, null);
		Attribute rAttr3=new Attribute(at3);
		GoodHandlerEntry.removeAttribute(rAttr3, null);
		Attribute rAttr4=new Attribute(at4);
		GoodHandlerEntry.removeAttribute(rAttr4, null);
		Attribute rAttr5=new Attribute(at5);
		GoodHandlerEntry.removeAttribute(rAttr5, null);
		Attribute rAttr6=new Attribute(at6);
		GoodHandlerEntry.removeAttribute(rAttr6, null);
		Attribute rAttr7=new Attribute(at7);		
		GoodHandlerEntry.removeAttribute(rAttr7, null);
		Attribute rAttr8=new Attribute(at8);
		Attribute rAttr9=new Attribute(at9);
		Attribute rAttr10=new Attribute(at10);
		Attribute rAttr11=new Attribute(at11);
		Attribute rAttr12=new Attribute(at12);
		GoodHandlerEntry.removeAttribute(rAttr8, null);
		GoodHandlerEntry.removeAttribute(rAttr9, null);
		GoodHandlerEntry.removeAttribute(rAttr10, null);
		GoodHandlerEntry.removeAttribute(rAttr11, null);
		GoodHandlerEntry.removeAttribute(rAttr12, null);
		//Make new AttrTypes with different values
		long newPort=getFreePort();
		Attribute a2=new Attribute(ATTR_LISTEN_PORT, String.valueOf(newPort));
		//uncomment if want to test listen address
//		Attribute a3=new Attribute(ATTR_LISTEN_ADDRESS, "localhost");	
		Attribute a4=new Attribute(ATTR_ACCEPT_BACKLOG, String.valueOf(25));
		Attribute a5=new Attribute(ATTR_ALLOWED_CLIENT, "129.56.56.45");
		Attribute a6=new Attribute(ATTR_DENIED_CLIENT, "129.*.*.90");
		Attribute a7=new Attribute(ATTR_ALLOW_LDAPV2, "true");
		Attribute a8=new Attribute(ATTR_KEEP_LDAP_STATS, "true");
		Attribute a9=new Attribute(ATTR_SEND_REJECTION_NOTICE, "false");
		Attribute a10=new Attribute(ATTR_USE_TCP_KEEPALIVE, "false");
		Attribute a11=new Attribute(ATTR_USE_TCP_NODELAY, "false");
		Attribute a12=new Attribute(ATTR_ALLOW_REUSE_ADDRESS, "false");
		Attribute a13=new Attribute(ATTR_MAX_REQUEST_SIZE, "45 kb");
		Attribute a14=new Attribute(ATTR_USE_SSL, "false");
		Attribute a15=new Attribute(ATTR_ALLOW_STARTTLS, "true");
		//Add them
		GoodHandlerEntry.addAttribute(a2, null);
//		GoodHandlerEntry.addAttribute(a3, null);
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
		LinkedList<String> reasons = new LinkedList<String>();
    LDAPConnectionHandlerCfg config = LdapTestCase.getConfiguration(GoodHandlerEntry);
		//see if we're ok
		boolean ret=LDAPConnHandler.isConfigurationChangeAcceptable(config, reasons);
		assertTrue(ret);	
		//apply it
		LDAPConnHandler.applyConfigurationChange(config);
		LDAPConnHandler.finalizeConnectionHandler(reasonMsg, true);

	}
}
