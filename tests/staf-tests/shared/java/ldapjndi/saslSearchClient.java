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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.CompositeName;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.CommunicationException;
import javax.naming.directory.InvalidSearchFilterException;
import javax.security.sasl.AuthenticationException;
import javax.naming.NamingEnumeration;
import javax.naming.directory.SearchResult;
import javax.naming.directory.SearchControls;
import java.util.HashSet;
import java.util.Iterator;

/**
 *  Perform an Ldap search using SASL as authentication mechanism.
 *  Supports sasl encryption.
 *  The function returns the ldap error code
 */
public class saslSearchClient {

  /**
   * Main.
   *
   * @param args arguments
   */
  public static void main(String[] args) {

	// Ldapsearch parameters
    String hostname = null;
    String ldapPort = null;
    String basedn = null;
    String filter = null;
    int scope;
    

    // SASL options
    String mechanism = null;
    String authid = null;
    String password = null;
    String authzid = null;
    String realm = null;
    String qop = null;
    String strength = null;
    String maxbufsize = null;
    
    
    String errorCode = null;
    String errorMessage = null;
    String errorCause = null;
    

    Hashtable envLdap  = new Hashtable();
    LdapContext ctx = null;
    SearchControls searchControls = null;
    NamingEnumeration results = null;



    for (int k=0; k< args.length; k++) {
      String opt1 = args[k];
      String val1 = args[k+1];

      // Get ldapsearch parameters
      if (opt1.equals("-h")) {
        hostname = val1;
      }
      if (opt1.equals("-p")) {
        ldapPort = val1;
      }
      if (opt1.equals("-s")) {
    	if (val1.equals("base")) {
    	  scope = SearchControls.OBJECT_SCOPE;
    	} else if (opt1.equals("one")) {
    	  scope = SearchControls.ONELEVEL_SCOPE;
    	} else {
          // default scope: "sub"
    	  scope = SearchControls.SUBTREE_SCOPE;
    	}
    	searchControls = new SearchControls();
    	searchControls.setSearchScope(scope);
      }
      if (opt1.equals("-b")) {
    	basedn = val1;
      }
      if (opt1.equals("-f")) {
    	filter = val1;
      }
      
      // Get SASL options
      if (opt1.equals("--mech")) {
    	mechanism = val1;
      }
      if (opt1.equals("--authid")) {
    	authid = val1;
      }
      if (opt1.equals("-w")) {
          password = val1;
        }
      if (opt1.equals("--authzid")) {
    	authzid = val1;
      }
      if (opt1.equals("--realm")) {
    	realm = val1;
      }
      if (opt1.equals("--qop")) {
    	qop = val1;
      }
      if (opt1.equals("--strength")) {
    	strength = val1;
      }
      if (opt1.equals("--maxbufsize")) {
    	maxbufsize = val1;
      }
      k++;
    }




    String provider = "ldap://"  + hostname + ":" + ldapPort  + "/";

    envLdap.put("java.naming.factory.initial",
        "com.sun.jndi.ldap.LdapCtxFactory");
    envLdap.put(Context.PROVIDER_URL, provider);
    
    if (mechanism != null) {
      envLdap.put(Context.SECURITY_AUTHENTICATION, mechanism);
    }
    
    envLdap.put(Context.SECURITY_PRINCIPAL, authid);
    envLdap.put(Context.SECURITY_CREDENTIALS, password);
    
    if (authzid != null) {
      envLdap.put("javax.security.sasl.authorizationId", authzid);
    }
    if (realm != null) {
      envLdap.put("javax.security.sasl.realm", realm);
    }
    if (qop != null) {
      envLdap.put("javax.security.sasl.qop", qop);
    }
    if (strength != null) {
      envLdap.put("javax.security.sasl.strength", strength);
    }
    if (maxbufsize != null) {
      envLdap.put("javax.security.sasl.maxbuf", maxbufsize);
    }

    try {
      System.out.println("Search with SASL auth " + mechanism);
      System.out.println("Authentication ID " + authid);
      System.out.println("Password " + password);
      System.out.println("Authorization ID " + authzid);
      System.out.println("Realm " + realm);
      System.out.println("Quality of Protection " + qop);
      System.out.println("Cipher Strength " + strength);
      System.out.println("Maximum receive buffer size " + maxbufsize);

      // connect to server
      ctx = new InitialLdapContext(envLdap, null);

      // issue ldapsearch
      results = ctx.search(basedn, filter, searchControls);
      
      ctx.close();
    } catch (CommunicationException e1) {
      e1.printStackTrace();
      errorMessage = e1.getMessage();
      if (e1.getCause() != null)
        errorCause = e1.getCause().toString();
    } catch (InvalidSearchFilterException e2) {
      e2.printStackTrace();
      errorMessage = e2.getMessage();
      if (e2.getCause() != null)
        errorCause = e2.getCause().toString();
    } catch (NamingException e3) {
      e3.printStackTrace();
      errorMessage = e3.getMessage();
      if (e3.getCause() != null)
        errorCause = e3.getCause().toString();
    } catch (Exception e4) {
      e4.printStackTrace();
      errorMessage = e4.getMessage();
      if (e4.getCause() != null)
        errorCause = e4.getCause().toString();
    }
    
    
    String NO_COMMON_QOP_LAYER = 
    	"No common protection layer between client and server";
    

    // No error, the modify is success
    if ( errorMessage == null ) {
      errorCode = "0";
    } else {
      System.out.println();
      System.out.println(errorMessage);
      if (errorCause != null)
        System.out.println(errorCause);
      System.out.println();
      System.out.println();
      if (errorCause != null && errorCause.indexOf(NO_COMMON_QOP_LAYER) != -1) {
    	// return 89-LDAP_PARAM_ERROR, which is also returned by ldap clients
    	errorCode = "89";
      } else {
        int ind = errorMessage.indexOf("-");
        if ( ind > 0 ) {
          errorCode = errorMessage.substring(18, ind-1);
        } else errorCode = "0";
      }
    }

    try {
      if ((errorCode.equals("0")) && (results != null)) {
        while (results.hasMore()) {
          SearchResult searchResult = (SearchResult) results.next();
          System.out.println(searchResult.toString());
        }
        results.close();    	
      }
    } catch (NamingException ne) {
      ne.printStackTrace();
    }
    
    int RC = Integer.parseInt(errorCode);
    System.exit(RC);
  }

}
