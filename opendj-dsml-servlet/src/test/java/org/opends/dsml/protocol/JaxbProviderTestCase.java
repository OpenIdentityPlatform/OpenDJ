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
package org.opends.dsml.protocol;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.Test;

import jakarta.xml.bind.JAXBContext;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Pins the gateway to a single, known Jakarta XML Binding provider.
 * <p>
 * {@code JAXBContext.newInstance(String, ClassLoader)} resolves its provider
 * through the {@code ServiceLoader}, so with two providers on the classpath the
 * one serving the DSMLv2 bindings is whichever the container's classloader
 * happens to reach first (GHSA-jr68-5c58-j463). That decides observable
 * behaviour: {@code mapAnyUriToUri}, which the gateway sets when anyURI
 * dereferencing is enabled, is honoured by the JAXB reference implementation
 * and ignored by EclipseLink MOXy.
 * <p>
 * The web application therefore has to ship exactly one provider. There is no
 * way to state that in the pom, and pinning it per package through a
 * {@code jaxb.properties} file is not an option either - that lookup was
 * removed from the API in 4.0 and is silently ignored there - so it is asserted
 * here: a dependency change that reintroduces a second provider fails this
 * test rather than changing behaviour in one container only.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "dsml" })
public class JaxbProviderTestCase extends ForgeRockTestCase
{
  private static final String PROVIDER_SERVICE = "META-INF/services/jakarta.xml.bind.JAXBContextFactory";
  private static final String JAXB_RI_PACKAGE = "org.glassfish.jaxb.runtime.";

  @Test
  public void testExactlyOneProviderIsOnTheClasspath() throws Exception
  {
    List<URL> providers =
        Collections.list(getClass().getClassLoader().getResources(PROVIDER_SERVICE));

    assertEquals(providers.size(), 1, "expected a single Jakarta XML Binding provider, found "
        + describe(providers));
  }

  @Test
  public void testDsmlBindingsAreServedByTheJaxbReferenceImplementation() throws Exception
  {
    JAXBContext context =
        JAXBContext.newInstance("org.opends.dsml.protocol", getClass().getClassLoader());

    assertTrue(context.getClass().getName().startsWith(JAXB_RI_PACKAGE),
        "the DSMLv2 bindings are served by " + context.getClass().getName()
            + ", which does not honour the mapAnyUriToUri property");
  }

  private String describe(List<URL> providers)
  {
    List<String> descriptions = new ArrayList<>(providers.size());
    for (URL provider : providers)
    {
      descriptions.add(provider.toString());
    }
    return descriptions.toString();
  }
}
