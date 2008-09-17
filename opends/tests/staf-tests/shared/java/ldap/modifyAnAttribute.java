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
import java.util.HashSet;
import java.util.Iterator;

/**
 *  Modify an entry with an attribute.
 *  The operation can be a replace, delete or a add new attribute
 *  The function returns the ldap error code
 */
public class modifyAnAttribute {

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
    String attributeToModify=null;
    String dnToModify=null;
    String newAttributeValue=null;
    String changetype=null;
    String errorCode=null;
    String errorMessage=null;
    String listAttributesToModify=null;

    int ind1;
    String attributeName;
    String attributeValue;
    Hashtable envLdap  = new Hashtable();
    LdapContext ctx;

    Attributes attributes = new BasicAttributes();
    HashSet attributeSet = new HashSet();


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
      if (opt1.equals("-v")) {
        newAttributeValue = val1;
      }
      if (opt1.equals("-a")) {
        attributeToModify = val1;
      }
      if (opt1.equals("-t")) {
        changetype = val1;
      }
      if (opt1.equals("-l")) {
        listAttributesToModify = val1;

        ind1= val1.indexOf(":");

        attributeName=val1.substring(0,ind1);
        if (ind1+1 < val1.length()) {
          // assume empty strings == no specific value
          attributeValue=val1.substring(ind1+1);
        } else {
          attributeValue = null;
        }

        BasicAttribute attrToComplete = null;

        Iterator it = attributeSet.iterator();
        while (attrToComplete == null && it.hasNext()) {
          BasicAttribute attr = (BasicAttribute) it.next();
          if ((attr.getID()).equalsIgnoreCase(attributeName)) {
            attrToComplete = attr;
          }
        }

        if (attrToComplete == null) {
          attrToComplete = new BasicAttribute(attributeName);
          attributeSet.add(attrToComplete);
        }
        if (attributeValue != null) {
          // as opposed to (attributeValue == null), for example in some
          // attribute delete operations
          attributeValue=attributeValue.replaceAll("QUOT","\\\"");
          attrToComplete.add(attributeValue);
        }
      }
      k++;
    }

    if ( attributeToModify != null && 
         ( newAttributeValue != null || changetype.equals("delete") ) ) {

      BasicAttribute attrToComplete = null;

      attrToComplete = new BasicAttribute(attributeToModify);
      attributeSet.add(attrToComplete);
      if (newAttributeValue != null) {
        // as opposed to (attributeValue == null), for example in some
        // attribute delete operations    	  
        newAttributeValue=newAttributeValue.replaceAll("QUOT","\\\"");
        attrToComplete.add(newAttributeValue);
      }
    }

    Iterator it2 = attributeSet.iterator();
    while (it2.hasNext()) {
      BasicAttribute attr = (BasicAttribute)it2.next();
      attributes.put(attr);
    }

    String provider = "ldap://"  + hostname + ":" + ldapPort  + "/";

    envLdap.put("java.naming.factory.initial",
        "com.sun.jndi.ldap.LdapCtxFactory");
    envLdap.put(Context.SECURITY_AUTHENTICATION, "simple");
    envLdap.put(Context.SECURITY_PRINCIPAL,  principal);
    envLdap.put(Context.SECURITY_CREDENTIALS, credential);
    envLdap.put(Context.PROVIDER_URL, provider);

    try {
      CompositeName entryDN = new CompositeName(dnToModify);
      System.out.println("Modify the entry " + dnToModify);

      // connect to server
      ctx = new InitialLdapContext(envLdap, null);

      // replace attribute
      if (changetype.equals("replace")) {
        ctx.modifyAttributes(entryDN,
            LdapContext.REPLACE_ATTRIBUTE,
            attributes);
      } else if (changetype.equals("add")) {
        // add attribute
        ctx.modifyAttributes(entryDN,
            LdapContext.ADD_ATTRIBUTE,
            attributes);
      } else if (changetype.equals("delete")) {
        // add attribute
        ctx.modifyAttributes(entryDN,
            LdapContext.REMOVE_ATTRIBUTE,
            attributes);
      }

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
