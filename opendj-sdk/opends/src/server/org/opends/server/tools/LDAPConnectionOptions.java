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
package org.opends.server.tools;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;




/**
 * This class defines options used while creating an LDAP connection
 * to the server.
 */
public class LDAPConnectionOptions
{

  private boolean reportAuthzID = false;
  private boolean useSSL =  false;
  private boolean startTLS = false;
  private boolean saslExternal = false;
  private boolean usePasswordPolicyControl = false;
  private SSLConnectionFactory sslConnectionFactory = null;
  private String saslMechanism = null;
  private int versionNumber = 3;
  private Map<String, List<String>> saslProperties =
                                         new HashMap<String, List<String>> ();
  private boolean verbose = false;

  /**
   * Creates a the connection options instance.
   *
   */
  public LDAPConnectionOptions()
  {
  }

  /**
   * Set whether to use SSL for the connection or not.
   *
   * @param useSSL    True if SSL should be used, false otherwise.
   *
   */

  public void setUseSSL(boolean useSSL)
  {
    this.useSSL = useSSL;
  }

  /**
   * Return the useSSL flag value.
   *
   * @return  <CODE>true</CODE> if SSL should be used, or <CODE>false</CODE> if
   *          not.
   */
  public boolean useSSL()
  {
    return useSSL;
  }

  /**
   * Set whether to use startTLS for the connection or not.
   *
   * @param startTLS    True if startTLS should be used, false otherwise.
   *
   */

  public void setStartTLS(boolean startTLS)
  {
    this.startTLS = startTLS;
  }

  /**
   * Return the startTLS flag value.
   *
   * @return  <CODE>true</CODE> if StartTLS should be used, or
   *          <CODE>false</CODE> if not.
   */
  public boolean useStartTLS()
  {
    return startTLS;
  }

  /**
   * Set whether to use SASL EXTERNAL for the connection or not.
   *
   * @param saslExternal    True if SASL EXTERNAL should be used,
   *                        false otherwise.
   *
   */

  public void setSASLExternal(boolean saslExternal)
  {
    this.saslExternal = saslExternal;
  }

  /**
   * Return the saslExternal flag value.
   *
   * @return  <CODE>true</CODE> if SASL EXTERNAL should be used, or
   *          <CODE>false</CODE> if not.
   */
  public boolean useSASLExternal()
  {
    return saslExternal;
  }

  /**
   * Set the SSL connection factory to use to create SSL connections.
   *
   * @param sslConnectionFactory    The SSL connection factory.
   *
   */

  public void setSSLConnectionFactory(SSLConnectionFactory sslConnectionFactory)
  {
    this.sslConnectionFactory = sslConnectionFactory;
  }

  /**
   * Return the SSLConnectionFactory instance.
   *
   * @return  The SSL connection factory to use when establishing secure
   *          connections.
   */
  public SSLConnectionFactory getSSLConnectionFactory()
  {
    return sslConnectionFactory;
  }

  /**
   * Set the SASL mechanism used for authentication.
   *
   * @param  mechanism  The SASL mechanism string, in "name=value" form.
   *
   * @return  <CODE>true</CODE> if the SASL mechanism was set, or
   *          <CODE>false</CODE> if not.
   */
  public boolean setSASLMechanism(String mechanism)
  {
    int idx = mechanism.indexOf("=");
    if(idx == -1)
    {
      System.err.println("Invalid SASL mechanism property:" + mechanism);
      return false;
    }
    this.saslMechanism = mechanism.substring(idx+1, mechanism.length());
    if(saslMechanism.equalsIgnoreCase("EXTERNAL"))
    {
      setSASLExternal(true);
    }
    return true;
  }

  /**
   * Get the SASL mechanism used for authentication.
   *
   * @return  The SASL mechanism used for authentication.
   */
  public String getSASLMechanism()
  {
    return saslMechanism;
  }

  /**
   * Get the SASL options used for authentication.
   *
   * @return  The SASL options used for authentication.
   */
  public Map<String, List<String>> getSASLProperties()
  {
    return saslProperties;
  }

  /**
   * Add a property to the list of SASL properties.
   *
   * @param  property  The property (in name=value form) to add to the set of
   *                   SASL properties.
   *
   * @return  <CODE>true</CODE> if the property was set properly, or
   *          <CODE>false</CODE> if not.
   */

  public boolean addSASLProperty(String property)
  {
    int idx = property.indexOf("=");
    if(idx == -1)
    {
      System.err.println("Invalid SASL property format:" + property);
      return false;
    }
    String key = property.substring(0, idx);
    String value = property.substring(idx+1, property.length());
    List<String> valList = saslProperties.get(key);
    if(valList == null)
    {
      valList = new ArrayList<String> ();
    }
    valList.add(value);

    saslProperties.put(key, valList);
    return true;
  }

  /**
   * Set the LDAP version number.
   *
   * @param  version  The LDAP version number.
   */
  public void setVersionNumber(int version)
  {
    this.versionNumber = version;
  }

  /**
   * Get the LDAP version number.
   *
   * @return  The LDAP version number.
   */
  public int getVersionNumber()
  {
    return this.versionNumber;
  }



  /**
   * Indicates whether to request that the server return the authorization ID in
   * the bind response.
   *
   * @return  <CODE>true</CODE> if the server should include the authorization
   *          ID in the bind response, or <CODE>false</CODE> if not.
   */
  public boolean getReportAuthzID()
  {
    return reportAuthzID;
  }



  /**
   * Specifies whether to request that the server return the authorization ID in
   * the bind response.
   *
   * @param  reportAuthzID  Specifies whether to request that the server return
   *                        the authorization ID in the bind response.
   */
  public void setReportAuthzID(boolean reportAuthzID)
  {
    this.reportAuthzID = reportAuthzID;
  }



  /**
   * Indicates whether to use the password policy control in the bind request.
   *
   * @return  <CODE>true</CODE> if the password policy control should be
   *          included in the bind request, or <CODE>false</CODE> if not.
   */
  public boolean usePasswordPolicyControl()
  {
    return usePasswordPolicyControl;
  }



  /**
   * Specifies whether to use the password policy control in the bind request.
   *
   * @param  usePasswordPolicyControl  Specifies whether to use the password
   *                                   policy control in the bind request.
   */
  public void setUsePasswordPolicyControl(boolean usePasswordPolicyControl)
  {
    this.usePasswordPolicyControl = usePasswordPolicyControl;
  }

  /**
   * Indicates whether verbose tracing is enabled.
   *
   * @return <CODE>true</CODE> if verbose tracing is enabled.
   */
  public boolean isVerbose()
  {
    return verbose;
  }

  /**
   * Specifies whether verbose tracing should be enabled.
   * @param verbose Specifies whether verbose tracing should be enabled.
   */
  public void setVerbose(boolean verbose)
  {
    this.verbose = verbose;
  }
}

