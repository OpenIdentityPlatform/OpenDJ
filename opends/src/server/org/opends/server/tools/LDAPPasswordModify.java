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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.tools;



import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.ldap.BindRequestProtocolOp;
import org.opends.server.protocols.ldap.BindResponseProtocolOp;
import org.opends.server.protocols.ldap.ExtendedRequestProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.protocols.ldap.UnbindRequestProtocolOp;
import org.opends.server.types.DN;

import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This program provides a utility that uses the LDAP password modify extended
 * operation to change the password for a user.  It exposes the three primary
 * options available for this operation, which are:
 *
 * <UL>
 *   <LI>The user identity whose password should be changed.</LI>
 *   <LI>The current password for the user.</LI>
 *   <LI>The new password for the user.
 * </UL>
 *
 * All of these are optional components that may be included or omitted from the
 * request.
 */
public class LDAPPasswordModify
{
  /**
   * Parses the command-line arguments, establishes a connection to the
   * Directory Server, sends the password modify request, and reads the
   * response.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    // Define default values for all the configurable arguments.
    boolean provideDNForAuthzID = false;
    boolean provideOldPW        = false;
    int     ldapPort            = 389;
    String  authzID             = null;
    String  bindDN              = null;
    String  bindPW              = null;
    String  ldapHost            = "127.0.0.1";
    String  newPassword         = null;
    String  oldPassword         = null;


    if (args.length == 0)
    {
      displayUsage();
      return;
    }


    // Iterate through and process the command-line arguments.
    for (int i=0; i < args.length; i++)
    {
      if (args[i].equals("-h"))
      {
        ldapHost = args[++i];
      }
      else if (args[i].equals("-p"))
      {
        ldapPort = Integer.parseInt(args[++i]);
      }
      else if (args[i].equals("-D"))
      {
        bindDN = args[++i];
      }
      else if (args[i].equals("-w"))
      {
        bindPW = args[++i];
      }
      else if (args[i].equals("-a"))
      {
        authzID = args[++i];
      }
      else if (args[i].equals("-A"))
      {
        provideDNForAuthzID = true;
      }
      else if (args[i].equals("-o"))
      {
        oldPassword = args[++i];
      }
      else if (args[i].equals("-O"))
      {
        provideOldPW = true;
      }
      else if (args[i].equals("-n"))
      {
        newPassword = args[++i];
      }
      else if (args[i].equals("-H"))
      {
        displayUsage();
        return;
      }
      else
      {
        System.err.println("ERROR:  Invalid argument \"" + args[i] + "\"");
        displayUsage();
        System.exit(1);
      }
    }


    // Make sure that all the required arguments were provided.
    if (provideOldPW)
    {
      if ((bindPW == null) || (bindPW.length() == 0))
      {
        System.err.println("ERROR:  The -O argument was used but no bind " +
                           "password was provided.");
        displayUsage();
        System.exit(1);
      }
      else
      {
        oldPassword = bindPW;
      }
    }

    if (provideDNForAuthzID)
    {
      if ((bindDN == null) || (bindDN.length() == 0))
      {
        System.err.println("ERROR:  The -A argument was used but no bind DN " +
                           "was provided.");
        displayUsage();
        System.exit(1);
      }
      else
      {
        authzID = "dn:" + bindDN;
      }
    }


    // Perform the necessary bootstrapping of the Directory Server code.
    DirectoryServer.bootstrapClient();


    // Establish a connection to the Directory Server.
    ASN1Reader reader = null;
    ASN1Writer writer = null;
    try
    {
      Socket socket = new Socket(ldapHost, ldapPort);
      reader = new ASN1Reader(socket);
      writer = new ASN1Writer(socket);
    }
    catch (Exception e)
    {
      System.err.println("ERROR:  Unable to connect to the Directory Server " +
                         ldapHost + ":" + ldapPort + ":");
      e.printStackTrace();
      System.exit(1);
    }


    // Send the LDAP bind request if appropriate.
    AtomicInteger nextMessageID = new AtomicInteger(1);
    if ((bindDN != null) && (bindDN.length() > 0) && (bindPW != null) &&
        (bindPW.length() > 0))
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(new ASN1OctetString(bindDN), 3,
                                     new ASN1OctetString(bindPW));
      LDAPMessage requestMessage =
           new LDAPMessage(nextMessageID.getAndIncrement(), bindRequest);

      try
      {
        writer.writeElement(requestMessage.encode());
      }
      catch (Exception e)
      {
        System.err.println("ERROR:  Could not send the bind request to the " +
                           "server:");
        e.printStackTrace();

        try
        {
          reader.close();
          writer.close();
        } catch (Exception e2) {}

        System.exit(1);
      }

      LDAPMessage responseMessage = null;
      try
      {
        ASN1Sequence responseSequence = reader.readElement().decodeAsSequence();
        responseMessage = LDAPMessage.decode(responseSequence);
      }
      catch (Exception e)
      {
        System.err.println("ERROR:  Could not read the bind response from " +
                           "the server:");
        e.printStackTrace();

        try
        {
          reader.close();
          writer.close();
        } catch (Exception e2) {}

        System.exit(1);
      }

      BindResponseProtocolOp bindResponse =
           responseMessage.getBindResponseProtocolOp();
      int resultCode = bindResponse.getResultCode();
      if (resultCode != LDAPResultCode.SUCCESS)
      {
        System.err.println("ERROR:  Bind failed with result code " +
                           resultCode);

        String errorMessage = bindResponse.getErrorMessage();
        if ((errorMessage != null) && (errorMessage.length() > 0))
        {
          System.err.println("Error Message:  " + errorMessage);
        }

        DN matchedDN = bindResponse.getMatchedDN();
        if (matchedDN != null)
        {
          System.err.println("Matched DN:  " + matchedDN.toString());
        }

        try
        {
          requestMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                           new UnbindRequestProtocolOp());
          writer.writeElement(requestMessage.encode());
        }
        catch (Exception e) {}

        try
        {
          reader.close();
          writer.close();
        } catch (Exception e) {}

        System.exit(resultCode);
      }
    }


    // Construct the password modify request.
    ArrayList<ASN1Element> requestElements = new ArrayList<ASN1Element>(3);
    if (authzID != null)
    {
      requestElements.add(new ASN1OctetString(TYPE_PASSWORD_MODIFY_USER_ID,
                                              authzID));
    }

    if (oldPassword != null)
    {
      requestElements.add(new ASN1OctetString(TYPE_PASSWORD_MODIFY_OLD_PASSWORD,
                                              oldPassword));
    }

    if (newPassword != null)
    {
      requestElements.add(new ASN1OctetString(TYPE_PASSWORD_MODIFY_NEW_PASSWORD,
                                              newPassword));
    }

    ASN1OctetString requestValue =
         new ASN1OctetString(new ASN1Sequence(requestElements).encode());

    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_PASSWORD_MODIFY_REQUEST,
                                       requestValue);
    LDAPMessage requestMessage =
         new LDAPMessage(nextMessageID.getAndIncrement(), extendedRequest);


    // Send the request to the server and read the response.
    try
    {
      writer.writeElement(requestMessage.encode());
    }
    catch (Exception e)
    {
      System.err.println("ERROR:  Could not send password modify request:");
      e.printStackTrace();

      try
      {
        requestMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                         new UnbindRequestProtocolOp());
        writer.writeElement(requestMessage.encode());
      }
      catch (Exception e2) {}

      try
      {
        reader.close();
        writer.close();
      } catch (Exception e2) {}

      System.exit(1);
    }


    // Read the response from the server.
    LDAPMessage responseMessage = null;
    try
    {
      ASN1Sequence responseSequence = reader.readElement().decodeAsSequence();
      responseMessage = LDAPMessage.decode(responseSequence);
    }
    catch (Exception e)
    {
      System.err.println("ERROR:  Could not read password modify response:");
      e.printStackTrace();

      try
      {
        requestMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                         new UnbindRequestProtocolOp());
        writer.writeElement(requestMessage.encode());
      }
      catch (Exception e2) {}

      try
      {
        reader.close();
        writer.close();
      } catch (Exception e2) {}

      System.exit(1);
    }


    // Make sure that the response was acceptable.
    ExtendedResponseProtocolOp extendedResponse =
         responseMessage.getExtendedResponseProtocolOp();
    int resultCode = extendedResponse.getResultCode();
    if (resultCode != LDAPResultCode.SUCCESS)
    {
      System.err.println("ERROR:  Password modify failed with result code " +
                         resultCode);

      String errorMessage = extendedResponse.getErrorMessage();
      if ((errorMessage != null) && (errorMessage.length() > 0))
      {
        System.err.println("Error Message:  " + errorMessage);
      }

      DN matchedDN = extendedResponse.getMatchedDN();
      if (matchedDN != null)
      {
        System.err.println("Matched DN:  " + matchedDN.toString());
      }

      try
      {
        requestMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                         new UnbindRequestProtocolOp());
        writer.writeElement(requestMessage.encode());
      }
      catch (Exception e) {}

      try
      {
        reader.close();
        writer.close();
      } catch (Exception e) {}

      System.exit(resultCode);
    }


    System.out.println("The user's password was successfully modified.");


    // See if the response included a generated password.
    ASN1OctetString responseValue = extendedResponse.getValue();
    if (responseValue != null)
    {
      try
      {
        ASN1Sequence responseSequence =
             ASN1Sequence.decodeAsSequence(responseValue.value());
        for (ASN1Element e : responseSequence.elements())
        {
          if (e.getType() == TYPE_PASSWORD_MODIFY_GENERATED_PASSWORD)
          {
            String generatedPassword = e.decodeAsOctetString().stringValue();
            System.out.println("The generated password was:  " +
                               generatedPassword);
          }
          else
          {
            throw new Exception("ERROR:  Unrecognized response value type " +
                                byteToHex(e.getType()));
          }
        }
      }
      catch (Exception e)
      {
        System.err.println("ERROR:  Could not decode response value to " +
                           "determine the generated password:");
        e.printStackTrace();

        try
        {
          requestMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                           new UnbindRequestProtocolOp());
          writer.writeElement(requestMessage.encode());
        }
        catch (Exception e2) {}

        try
        {
          reader.close();
          writer.close();
        } catch (Exception e2) {}

        System.exit(1);
      }
    }


    // Unbind from the server and close the connection.
    try
    {
      requestMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                       new UnbindRequestProtocolOp());
      writer.writeElement(requestMessage.encode());
    }
    catch (Exception e) {}

    try
    {
      reader.close();
      writer.close();
    } catch (Exception e) {}
  }



  /**
   * Displays usage information for this program.
   */
  public static void displayUsage()
  {
    System.err.println("USAGE:  java LDAPPasswordModify {options}");
    System.err.println("        where {options} include:");
    System.err.println("-h {ldapHost}  -- The Directory Server address");
    System.err.println("-p {ldapPort}  -- The Directory Server port");
    System.err.println("-D {bindDN}    -- The bind DN");
    System.err.println("-w {bindPW}    -- The bind password");
    System.err.println("-a {authzID}   -- The authorization ID");
    System.err.println("-A             -- Use bind DN as authorization ID");
    System.err.println("-o {oldPWD}    -- The old password");
    System.err.println("-O             -- Use bind password as old password");
    System.err.println("-n {newPWD}    -- The new password");
    System.err.println("-H             -- Display this usage information");
  }
}

