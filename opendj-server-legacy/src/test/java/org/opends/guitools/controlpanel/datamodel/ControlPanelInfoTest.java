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
package org.opends.guitools.controlpanel.datamodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.admin.ads.util.PreferredConnection;
import org.opends.guitools.controlpanel.util.ConfigFromConnection;
import org.opends.guitools.controlpanel.util.ConfigReader;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.HostPort;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ControlPanelInfoTest extends DirectoryServerTestCase
{
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  @Test
  public void readsMissingChangesAboveTheIntegerRangeWithoutAnOldestChange() throws Exception
  {
    final DN suffixDN = DN.valueOf("dc=example,dc=com");
    final BaseDNDescriptor replica = new BaseDNDescriptor(BaseDNDescriptor.Type.REPLICATED, suffixDN,
        null, -1, -1, -1);
    final BackendDescriptor backend = new BackendDescriptor("userRoot", Set.of(replica), Set.of(), Set.of(), -1,
        true, BackendDescriptor.Type.PLUGGABLE);
    replica.setReplicaID(1);
    final ConfigFromConnection reader = new ConfigFromConnection();
    final Field backends = ConfigReader.class.getDeclaredField("backends");
    backends.setAccessible(true);
    backends.set(reader, Set.of(backend));

    final SearchResultEntry monitor = Responses.newSearchResultEntry("cn=replica,cn=monitor");
    monitor.addAttribute("domain-name", suffixDN.toString());
    monitor.addAttribute("server-id", "1");
    monitor.addAttribute("missing-changes", "6442450929");
    final Method handleEntry = ConfigFromConnection.class.getDeclaredMethod(
        "handleMonitoringSearchResult", SearchResultEntry.class);
    handleEntry.setAccessible(true);
    handleEntry.invoke(reader, monitor);

    assertThat(replica.getMissingChanges()).isEqualTo(6442450929L);
    assertThat(replica.getAgeOfOldestMissingChange()).isEqualTo(-1L);
  }

  @Test
  public void reportsStatusWhenVersionAndSystemInformationMonitorsAreMissing() throws Exception
  {
    final ServerDescriptor status = regenerateWithMonitor(mock(ConnectionEntryReader.class));

    assertThat(status.isAuthenticated()).isTrue();
    assertThat(status.getStatus()).isEqualTo(ServerDescriptor.ServerStatus.STARTED);
  }

  private ServerDescriptor regenerateWithMonitor(ConnectionEntryReader monitor) throws Exception
  {
    final HostPort hostPort = new HostPort("127.0.0.1", TestCaseUtils.getServerLdapPort());
    try (ConnectionWrapper realConnection = new ConnectionWrapper(hostPort, PreferredConnection.Type.LDAP,
        DN.valueOf("cn=Directory Manager"), "password", 5000, null))
    {
      final Connection ldap = realConnection.getConnection();
      final Connection connection = mock(Connection.class, delegatesTo(ldap));
      doAnswer(invocation -> {
        final SearchRequest request = (SearchRequest) invocation.getArguments()[0];
        return request.getName().equals(DN.valueOf("cn=monitor")) ? monitor : ldap.search(request);
      }).when(connection).search(any(SearchRequest.class));
      final ConnectionWrapper wrapper = spy(realConnection);
      doReturn(connection).when(wrapper).getConnection();

      final Constructor<ControlPanelInfo> constructor = ControlPanelInfo.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      final ControlPanelInfo panel = constructor.newInstance();
      panel.setIsLocal(false);
      panel.setConnection(wrapper);
      panel.regenerateDescriptor();
      return panel.getServerDescriptor();
    }
  }
}
