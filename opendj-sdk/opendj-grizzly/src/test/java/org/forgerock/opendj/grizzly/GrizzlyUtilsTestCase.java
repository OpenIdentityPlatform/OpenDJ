/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.grizzly;

import static org.fest.assertions.Assertions.assertThat;

import org.forgerock.opendj.ldap.SdkTestCase;
import org.glassfish.grizzly.StandaloneProcessor;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class GrizzlyUtilsTestCase extends SdkTestCase {

    private static final class DummyLDAPFilter extends BaseFilter { // only need type
    }

    private static final class FilterOne extends BaseFilter { // only need type
    }

    private static final class DummySSLFilter extends SSLFilter { // only need type
    }

    /**
     * Default filter chain contains a transport filter and a ldap filter.
     */
    private FilterChain getDefaultFilterChain() {
        return FilterChainBuilder.stateless().
                add(new TransportFilter()).add(new DummyLDAPFilter()).build();
    }

    @Test
    public void addFilterToChain() throws Exception {
        final FilterChain chain = GrizzlyUtils.addFilterToChain(new FilterOne(), getDefaultFilterChain());

        assertThat(chain.indexOfType(TransportFilter.class)).isEqualTo(0);
        assertThat(chain.indexOfType(FilterOne.class)).isEqualTo(1);
        assertThat(chain.indexOfType(DummyLDAPFilter.class)).isEqualTo(2);
        assertThat(chain.size()).isEqualTo(3);
    }

    @Test
    public void addSSLFilterToChain() throws Exception {
        final FilterChain chain = GrizzlyUtils.addFilterToChain(new DummySSLFilter(), getDefaultFilterChain());

        assertThat(chain.indexOfType(TransportFilter.class)).isEqualTo(0);
        assertThat(chain.indexOfType(DummySSLFilter.class)).isEqualTo(1);
        assertThat(chain.indexOfType(DummyLDAPFilter.class)).isEqualTo(2);
        assertThat(chain.size()).isEqualTo(3);
    }

    @Test
    public void addConnectionSecurityLayerAndSSLFilterToChain() throws Exception {
        final FilterChain chain = GrizzlyUtils.addFilterToChain(new ConnectionSecurityLayerFilter(null, null),
                getDefaultFilterChain());
        final FilterChain sslChain = GrizzlyUtils.addFilterToChain(new DummySSLFilter(), chain);

        // SSLFilter must be beneath ConnectionSecurityLayerFilter
        assertThat(sslChain.indexOfType(TransportFilter.class)).isEqualTo(0);
        assertThat(sslChain.indexOfType(DummySSLFilter.class)).isEqualTo(1);
        assertThat(sslChain.indexOfType(ConnectionSecurityLayerFilter.class)).isEqualTo(2);
        assertThat(sslChain.indexOfType(DummyLDAPFilter.class)).isEqualTo(3);
        assertThat(sslChain.size()).isEqualTo(4);
    }

    @Test
    public void buildFilterChainFromFilterChainProcessor() throws Exception {
        final FilterChain chain = GrizzlyUtils.buildFilterChain(
                FilterChainBuilder.stateless().add(new TransportFilter()).build(), new DummyLDAPFilter());

        assertThat(chain.indexOfType(TransportFilter.class)).isEqualTo(0);
        assertThat(chain.indexOfType(DummyLDAPFilter.class)).isEqualTo(1);
        assertThat(chain.size()).isEqualTo(2);
    }

    @Test
    public void buildFilterChainFromNonFilterChainProcessor() throws Exception {
        final FilterChain chain = GrizzlyUtils.buildFilterChain(new StandaloneProcessor(), new DummyLDAPFilter());

        assertThat(chain.indexOfType(TransportFilter.class)).isEqualTo(0);
        assertThat(chain.indexOfType(DummyLDAPFilter.class)).isEqualTo(1);
        assertThat(chain.size()).isEqualTo(2);
    }


}
