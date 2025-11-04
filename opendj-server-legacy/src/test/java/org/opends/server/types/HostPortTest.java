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
package org.opends.server.types;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.util.CollectionUtils.*;

import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test(groups = { "precommit", "types" }, sequential = true)
@SuppressWarnings("javadoc")
public class HostPortTest extends TypesTestCase
{

  private static final String IPV6_ADDRESS = "2001:0db8:85a3:0000:0000:8a2e:0370:7334";

  @Test
  public void valueOfHostName()
  {
    final String serverURL = "home:1";
    final HostPort hp = HostPort.valueOf(serverURL);
    assertThat(hp.getHost()).isEqualTo("home");
    assertThat(hp.getPort()).isEqualTo(1);
    assertThat(hp.toString()).isEqualTo(serverURL);
  }

  @Test
  public void valueOfHostNameDefaultPort()
  {
    final String serverURL = "home";
    final HostPort hp = HostPort.valueOf(serverURL, 1);
    assertThat(hp.getHost()).isEqualTo("home");
    assertThat(hp.getPort()).isEqualTo(1);
    assertThat(hp.toString()).isEqualTo(serverURL + ":1");
  }

  @Test
  public void valueOfHostNameOverridingDefaultPort()
  {
    final String serverURL = "home:1";
    final HostPort hp = HostPort.valueOf(serverURL, 2);
    assertThat(hp.getHost()).isEqualTo("home");
    assertThat(hp.getPort()).isEqualTo(1);
    assertThat(hp.toString()).isEqualTo(serverURL);
  }

  @Test
  public void valueOfIPv4()
  {
    final String serverURL = "192.168.1.1:1";
    final HostPort hp = HostPort.valueOf(serverURL);
    assertThat(hp.getHost()).isEqualTo("192.168.1.1");
    assertThat(hp.getPort()).isEqualTo(1);
    assertThat(hp.toString()).isEqualTo(serverURL);
  }

  @Test
  public void valueOfIPv4DefaultPort()
  {
    final String serverURL = "192.168.1.1";
    final HostPort hp = HostPort.valueOf(serverURL, 1);
    assertThat(hp.getHost()).isEqualTo("192.168.1.1");
    assertThat(hp.getPort()).isEqualTo(1);
    assertThat(hp.toString()).isEqualTo(serverURL + ":1");
  }

  @Test
  public void valueOfIPv4OverridingDefaultPort()
  {
    final String serverURL = "192.168.1.1:1";
    final HostPort hp = HostPort.valueOf(serverURL, 2);
    assertThat(hp.getHost()).isEqualTo("192.168.1.1");
    assertThat(hp.getPort()).isEqualTo(1);
    assertThat(hp.toString()).isEqualTo(serverURL);
  }

  @Test
  public void valueOfIPv6()
  {
    final String serverURL = "[" + IPV6_ADDRESS + "]:1";
    final HostPort hp = HostPort.valueOf(serverURL);
    assertThat(hp.getHost()).isEqualTo(IPV6_ADDRESS);
    assertThat(hp.getPort()).isEqualTo(1);
    assertThat(hp.toString()).isEqualTo(serverURL);
  }

  @Test
  public void valueOfIPv6DefaultPort()
  {
    final String serverURL = "[" + IPV6_ADDRESS + "]";
    final HostPort hp = HostPort.valueOf(serverURL, 1);
    assertThat(hp.getHost()).isEqualTo(IPV6_ADDRESS);
    assertThat(hp.getPort()).isEqualTo(1);
    assertThat(hp.toString()).isEqualTo(serverURL + ":1");
  }

  @Test
  public void valueOfIPv6OverridingDefaultPort()
  {
    final String serverURL = "[" + IPV6_ADDRESS + "]:1";
    final HostPort hp = HostPort.valueOf(serverURL, 2);
    assertThat(hp.getHost()).isEqualTo(IPV6_ADDRESS);
    assertThat(hp.getPort()).isEqualTo(1);
    assertThat(hp.toString()).isEqualTo(serverURL);
  }

  @Test
  public void undefinedHostPort()
  {
    final HostPort hp = new HostPort(null, 0);
    assertThat(hp.getHost()).isNull();
    assertThat(hp.getPort()).isEqualTo(0);
  }

  @Test
  public void valueOfEqualsHashCodeIPv4()
  {
    final HostPort hp1 = HostPort.valueOf("home:1");
    final HostPort hp2 = new HostPort("home", 1);
    assertThat(hp1).isEqualTo(hp2);
    assertThat(hp1.hashCode()).isEqualTo(hp2.hashCode());
    assertThat(hp1).isEqualByComparingTo(hp2);
  }

  @Test
  public void valueOfIPv6Brackets()
  {
    final String hostName = IPV6_ADDRESS;
    final String serverURL = "[" + hostName + "]:389";
    final HostPort hp = HostPort.valueOf(serverURL);
    assertThat(hp.getHost()).isEqualTo(hostName);
    assertThat(hp.getPort()).isEqualTo(389);
    assertThat(hp.toString()).isEqualTo(serverURL);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void valueOfIPv6NoBrackets()
  {
    final String hostName = IPV6_ADDRESS;
    final HostPort hp = HostPort.valueOf(hostName + ":389");
    assertThat(hp.getHost()).isEqualTo(hostName);
    assertThat(hp.getPort()).isEqualTo(389);
    assertThat(hp.toString()).isEqualTo("[" + hostName + "]:389");
  }

  @Test
  public void valueOfEqualsHashCodeIPv6()
  {
    final String hostName = IPV6_ADDRESS;
    final HostPort hp1 = HostPort.valueOf("[" + hostName + "]:389");
    final HostPort hp2 = new HostPort(hostName, 389);
    assertThat(hp1).isEqualTo(hp2);
    assertThat(hp1.hashCode()).isEqualTo(hp2.hashCode());
    assertThat(hp1).isEqualByComparingTo(hp2);
  }

  @Test
  public void valueOfEqualsHashCodeDifferentHostNames()
  {
    final HostPort hp1 = HostPort.valueOf("127.0.0.1:389");
    final HostPort hp2 = HostPort.valueOf("localhost:389");
    assertThat(hp1).isEqualTo(hp2);
    assertThat(hp1.hashCode()).isEqualTo(hp2.hashCode());
    assertThat(hp1).isNotEqualByComparingTo(hp2);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void valueOfNoPort()
  {
    HostPort.valueOf("host");
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void valueOfNoHost()
  {
    HostPort.valueOf(":389");
  }

  @Test(expectedExceptions = NumberFormatException.class)
  public void valueOfPortNotANumber()
  {
    HostPort.valueOf("host:port");
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void valueOfPortNumberTooSmall()
  {
    HostPort.valueOf("host:-1");
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void valueOfPortNumberTooBig()
  {
    HostPort.valueOf("host:99999999");
  }

  @Test
  public void valueOfIPv6NoPort()
  {
    try
    {
      final String hostName = "[2001:0db8:85a3:0000:0000:8a2e:0370:7334]";
      HostPort hp = HostPort.valueOf(hostName);
      assertThat(hp.getHost()).isEqualTo(hostName);
    }
    catch (IllegalArgumentException e)
    {
      Assert.assertEquals(e.getClass(), IllegalArgumentException.class);
    }
  }

  @Test
  public void allAddressesNullHost() {
    HostPort hp = HostPort.allAddresses(1);
    assertThat(hp.getHost()).isEqualTo("0.0.0.0");
    assertThat(hp.getPort()).isEqualTo(1);
  }

  @Test
  public void allAddressesToString() {
    HostPort hp = HostPort.allAddresses(1636);
    assertThat(hp.toString()).isEqualTo(HostPort.WILDCARD_ADDRESS + ":1636");
  }

  @Test
  public void allAddressesEquals() {
    HostPort hp1 = HostPort.allAddresses(1389);
    HostPort hp2 = HostPort.allAddresses(1389);
    assertThat(hp1).isEqualTo(hp2);
  }

  @Test
  public void isEquivalentTo()
  {
    HostPort hp1 = HostPort.valueOf("localhost:389");
    assertThat(hp1.isEquivalentTo(hp1)).isTrue();
    HostPort hp2 = HostPort.valueOf("localhost:389");
    assertThat(hp1.isEquivalentTo(hp2)).isTrue();

    HostPort hpNull = new HostPort(null, 0);
    assertThat(hp1.isEquivalentTo(hpNull)).isFalse();

    HostPort nonLocalHp = HostPort.valueOf("www.openidentityplatform.org:389");
    assertThat(nonLocalHp.isEquivalentTo(nonLocalHp)).isTrue();
    assertThat(hp1.isEquivalentTo(nonLocalHp)).isFalse();
    assertThat(nonLocalHp.isEquivalentTo(hp1)).isFalse();
  }

  @Test
  public void isLocal()
  {
    HostPort hp1 = HostPort.valueOf("localhost:389");
    assertThat(hp1.isLocalAddress()).isTrue();

    HostPort nonLocalHp = HostPort.valueOf("example.org:389");
    assertThat(nonLocalHp.isLocalAddress()).isFalse();
  }

  @Test
  public void toLowerCaseStrings()
  {
    HostPort hp1 = HostPort.valueOf("localHost:389");
    HostPort nonLocalHp = HostPort.valueOf("Example.org:389");

    Set<String> sets = HostPort.toLowerCaseStrings(newHashSet(hp1, nonLocalHp));
    assertThat(sets).containsOnly("localhost:389", "example.org:389");
  }
}
