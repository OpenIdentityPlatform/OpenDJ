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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.protocols.http;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;

/**
 * Class holding the configuration for HTTP authentication. This is extracted
 * from the JSON config file or the config held in LDAP.
 */
class HTTPAuthenticationConfig
{

  private boolean basicAuthenticationSupported;
  private boolean customHeadersAuthenticationSupported;
  private String customHeaderUsername;
  private String customHeaderPassword;
  private DN searchBaseDN;
  private SearchScope searchScope;
  private String searchFilterTemplate;

  /**
   * Returns whether HTTP basic authentication is supported.
   *
   * @return true if supported, false otherwise
   */
  public boolean isBasicAuthenticationSupported()
  {
    return basicAuthenticationSupported;
  }

  /**
   * Sets whether HTTP basic authentication is supported.
   *
   * @param supported
   *          the supported value
   */
  public void setBasicAuthenticationSupported(boolean supported)
  {
    this.basicAuthenticationSupported = supported;
  }

  /**
   * Returns whether HTTP authentication via custom headers is supported.
   *
   * @return true if supported, false otherwise
   */
  public boolean isCustomHeadersAuthenticationSupported()
  {
    return customHeadersAuthenticationSupported;
  }

  /**
   * Sets whether HTTP authentication via custom headers is supported.
   *
   * @param supported
   *          the supported value
   */
  public void setCustomHeadersAuthenticationSupported(boolean supported)
  {
    this.customHeadersAuthenticationSupported = supported;
  }

  /**
   * Returns the expected HTTP header for the username. This setting is only
   * used when HTTP authentication via custom headers is supported.
   *
   * @return the HTTP header for the username
   */
  public String getCustomHeaderUsername()
  {
    return customHeaderUsername;
  }

  /**
   * Sets the expected HTTP header for the username. This setting only takes
   * effect when HTTP authentication via custom headers is supported.
   *
   * @param customHeaderUsername
   *          the HTTP header for the username
   */
  public void setCustomHeaderUsername(String customHeaderUsername)
  {
    this.customHeaderUsername = customHeaderUsername;
  }

  /**
   * Returns the expected HTTP header for the password. This setting is only
   * used when HTTP authentication via custom headers is supported.
   *
   * @return the HTTP header for the password
   */
  public String getCustomHeaderPassword()
  {
    return customHeaderPassword;
  }

  /**
   * Sets the expected HTTP header for the password. This setting only takes
   * effect when HTTP authentication via custom headers is supported.
   *
   * @param customHeaderPassword
   *          the HTTP header for the password
   */
  public void setCustomHeaderPassword(String customHeaderPassword)
  {
    this.customHeaderPassword = customHeaderPassword;
  }

  /**
   * Returns the base DN to use when searching the entry corresponding to the
   * authenticating user.
   *
   * @return the base DN to use when searching the authenticating user
   */
  public DN getSearchBaseDN()
  {
    return searchBaseDN;
  }

  /**
   * Sets the base DN to use when searching the entry corresponding to the
   * authenticating user.
   *
   * @param searchBaseDN
   *          the base DN to use when searching the authenticating user
   */
  public void setSearchBaseDN(DN searchBaseDN)
  {
    this.searchBaseDN = searchBaseDN;
  }

  /**
   * Returns the search scope to use when searching the entry corresponding to
   * the authenticating user.
   *
   * @return the search scope to use when searching the authenticating user
   */
  public SearchScope getSearchScope()
  {
    return searchScope;
  }

  /**
   * Sets the search scope to use when searching the entry corresponding to the
   * authenticating user.
   *
   * @param searchScope
   *          the search scope to use when searching the authenticating user
   */
  public void setSearchScope(SearchScope searchScope)
  {
    this.searchScope = searchScope;
  }

  /**
   * Returns the search filter template to use when searching the entry
   * corresponding to the authenticating user.
   *
   * @return the search filter template to use when searching the authenticating
   *         user
   */
  public String getSearchFilterTemplate()
  {
    return searchFilterTemplate;
  }

  /**
   * Sets the search filter template to use when searching the entry
   * corresponding to the authenticating user.
   *
   * @param searchFilterTemplate
   *          the search filter template to use when searching the
   *          authenticating user
   */
  public void setSearchFilterTemplate(String searchFilterTemplate)
  {
    this.searchFilterTemplate = searchFilterTemplate;
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("basicAuth: ");
    if (!basicAuthenticationSupported)
    {
      sb.append("not ");
    }
    sb.append("supported, ");
    sb.append("customHeadersAuth: ");
    if (customHeadersAuthenticationSupported)
    {
      sb.append("usernameHeader=\"").append(customHeaderUsername).append("\",");
      sb.append("passwordHeader=\"").append(customHeaderPassword).append("\"");
    }
    else
    {
      sb.append("not supported, ");
    }
    sb.append("searchBaseDN: \"").append(searchBaseDN).append("\"");
    sb.append("searchScope: \"").append(searchScope).append("\"");
    sb.append("searchFilterTemplate: \"").append(searchFilterTemplate).append(
        "\"");
    return sb.toString();
  }
}
