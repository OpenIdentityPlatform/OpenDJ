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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.CommunicationException;
import java.util.HashSet;
import java.util.Iterator;

/**
 *  Modify the DN of an entry.
 *  The operation can be a modDN (tree move) or a modRDN.
 *  The function returns the ldap error code
 */
public class modifyDn {

  /**
   * Main.
   *
   * @param args arguments
   */
  public static void main(String[] args) {

    String hostname=null;
    String ldapPort=null;
    String principal=null;
    String credential=null;
    String dnToModify=null;
    String newRDN=null;
    String deleteOldRDN=null;
    String newSuperior=null;
    String errorCode=null;
    String errorMessage=null;

    Hashtable envLdap  = new Hashtable();
    LdapContext ctx;
    LdapName nameToModify;
    LdapName targetName;
    String targetDn;


    for (int k=0; k< args.length; k++) {
      String opt1 = args[k];
      String val1 = args[k+1];

      if (opt1.equals("-h")) {
        hostname = val1;
      }
      if (opt1.equals("-p")) {
        ldapPort = val1;
      }
      if (opt1.equals("-D")) {
        principal = val1;
      }
      if (opt1.equals("-w")) {
        credential = val1;
      }
      if (opt1.equals("-d")) {
        dnToModify = val1;
      }
      if (opt1.equals("-e")) {
        newRDN = val1;
      }
      if (opt1.equals("-f")) {
        deleteOldRDN = val1;
      }
      if (opt1.equals("-g")) {
        newSuperior = val1;
      }
      k++;
    }


    String provider = "ldap://"  + hostname + ":" + ldapPort  + "/";

    envLdap.put("java.naming.factory.initial",
                "com.sun.jndi.ldap.LdapCtxFactory");
    envLdap.put(Context.SECURITY_AUTHENTICATION, "simple");
    envLdap.put(Context.SECURITY_PRINCIPAL,  principal);
    envLdap.put(Context.SECURITY_CREDENTIALS, credential);
    envLdap.put(Context.PROVIDER_URL, provider);

    // Whether the old RDN attribute values are to be retained 
    // as attributes of the entry, or deleted from the entry.
    // Default is 'true'
    if (deleteOldRDN != null) {
      envLdap.put("java.naming.ldap.deleteRDN", deleteOldRDN);
    }

    try {
      System.out.println("Modify the entry " + dnToModify);
      
      if ( newRDN == null ) {
        // newRDN = same as old rdn
        nameToModify = new LdapName(dnToModify);
        newRDN = (String) nameToModify.remove(nameToModify.size() - 1);
      }
      
      if ( newSuperior != null ) {
        // modDN operation => new parent = newSuperior	
        targetName = new LdapName(newSuperior);
      } else { 
        // modRDN operation => new parent = old parent
        targetName = new LdapName(dnToModify);
        targetName.remove(targetName.size() - 1);
      }
      targetName.add(newRDN);
      targetDn = targetName.toString();


      // connect to server
      ctx = new InitialLdapContext(envLdap, null);

      // rename the entry
      ctx.rename(dnToModify, targetDn);

      ctx.close();
    } catch (CommunicationException e1) {
      errorMessage = e1.getMessage();
    } catch (NamingException e2) {
      errorMessage = e2.getMessage();
    } catch (Exception e3) {
      errorMessage= e3.getMessage();
    }

    // No error, the modify is success
    if ( errorMessage == null ) {
      errorCode="0";
    } else {
      System.out.println(errorMessage);
      int ind=errorMessage.indexOf("-");
      if ( ind > 0 ) {
        errorCode=errorMessage.substring(18, ind-1);
      } else errorCode="0";
    }

    int RC = Integer.parseInt(errorCode);
    System.exit(RC);
  }

}
