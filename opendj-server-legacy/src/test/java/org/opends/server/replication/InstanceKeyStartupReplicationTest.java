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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.opends.server.config.ConfigConstants.ADS_CERTIFICATE_ALIAS;
import static org.opends.server.config.ConfigConstants.ATTR_CRYPTO_KEY_ID;
import static org.opends.server.config.ConfigConstants.ATTR_CRYPTO_PUBLIC_KEY_CERTIFICATE;
import static org.opends.server.config.ConfigConstants.DN_TRUST_STORE_ROOT;
import static org.opends.server.protocols.internal.InternalClientConnection.getRootConnection;
import static org.testng.Assert.assertEquals;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.admin.ads.ADSContext;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.crypto.CryptoManagerImpl;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.Entry;
import org.testng.annotations.Test;

/** Tests replication of the local instance certificate's ADS entry on restart. */
public class InstanceKeyStartupReplicationTest extends ReplicationTestCase
{
  @Test
  public void publishesMissingInstanceKeyWhenReplicationConnectsAfterStartup() throws Exception
  {
    final DN adminData = ADSContext.getAdministrationSuffixDN();
    final Entry certificate = DirectoryServer.getEntry(
        DN.valueOf(ATTR_CRYPTO_KEY_ID + "=" + ADS_CERTIFICATE_ALIAS + "," + DN_TRUST_STORE_ROOT));
    final ByteString certificateValue = certificate.parseAttribute(ATTR_CRYPTO_PUBLIC_KEY_CERTIFICATE).asByteString();
    final String keyId = CryptoManagerImpl.getInstanceKeyID(certificateValue.toByteArray());
    final DN instanceKey = DN.valueOf(ATTR_CRYPTO_KEY_ID + "=" + keyId + ",cn=instance keys," + adminData);

    // Keep the certificate used for TLS. Only the ADS entry is missing on restart.
    assertThat(connection.processDelete(instanceKey).getResultCode()).isEqualTo(ResultCode.SUCCESS);

    final int port = TestCaseUtils.findFreePort();
    final Entry replicationServer = TestCaseUtils.entryFromLdifString(
        "dn: cn=Replication Server, " + SYNCHRO_PLUGIN_DN + "\n"
            + "objectClass: ds-cfg-replication-server\n"
            + "cn: Replication Server\n"
            + "ds-cfg-replication-port: " + port + "\n"
            + "ds-cfg-replication-db-directory: InstanceKeyStartupReplicationTest\n"
            + "ds-cfg-replication-server-id: 2781\n");
    addSynchroServerEntry(
        "dn: cn=admin data, cn=domains, " + SYNCHRO_PLUGIN_DN + "\n"
            + "objectClass: ds-cfg-replication-domain\n"
            + "cn: admin data\n"
            + "ds-cfg-base-dn: " + adminData + "\n"
            + "ds-cfg-replication-server: localhost:" + port + "\n"
            + "ds-cfg-server-id: 2782\n");

    TestCaseUtils.getServer().stop(getClass().getName(), LocalizableMessage.raw("Restart without a replication server"));
    TestCaseUtils.getServer().start();
    connection = getRootConnection();
    assertThat(DirectoryServer.getEntry(instanceKey)).isNull();

    assertThat(connection.processAdd(replicationServer).getResultCode()).isEqualTo(ResultCode.SUCCESS);
    configEntriesToCleanup.add(replicationServer.getName());
    replServerEntry = replicationServer;

    final ReplicationBroker peer = openReplicationSession(adminData, 2783, 10, port, 5000);
    try
    {
      final AddMsg publishedKey = waitForSpecificMsg(peer, AddMsg.class);
      assertEquals(publishedKey.getDN(), instanceKey);
      assertThat(publishedKey.getAttributes()).anyMatch(attribute ->
          attribute.getAttributeDescription().getAttributeType().hasNameOrOID(ATTR_CRYPTO_PUBLIC_KEY_CERTIFICATE)
              && attribute.contains(certificateValue));
    }
    finally
    {
      stop(peer);
    }
  }
}
