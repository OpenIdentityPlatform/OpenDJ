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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.controls;
import org.opends.messages.Message;



import java.util.concurrent.locks.Lock;
import java.io.IOException;

import org.opends.server.api.IdentityMapper;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordPolicyState;
import org.opends.server.protocols.asn1.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.util.Validator.ensureNotNull;


/**
 * This class implements version 2 of the proxied authorization control as
 * defined in RFC 4370.  It makes it possible for one user to request that an
 * operation be performed under the authorization of another.  The target user
 * is specified using an authorization ID, which may be in the form "dn:"
 * immediately followed by the DN of that user, or "u:" followed by a user ID
 * string.
 */
public class ProxiedAuthV2Control
       extends Control
{
  /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private static final class Decoder
      implements ControlDecoder<ProxiedAuthV2Control>
  {
    /**
     * {@inheritDoc}
     */
    public ProxiedAuthV2Control decode(boolean isCritical, ByteString value)
        throws DirectoryException
    {
      if (!isCritical)
      {
        Message message = ERR_PROXYAUTH2_CONTROL_NOT_CRITICAL.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      if (value == null)
      {
        Message message = ERR_PROXYAUTH2_NO_CONTROL_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      ByteString authorizationID;
      try
      {
        // Try the legacy encoding where the value is wrapped by an
        // extra octet string
        authorizationID = reader.readOctetString();
      }
      catch (Exception e)
      {
        // Try just getting the value.
        authorizationID = value;
        String lowerAuthZIDStr = toLowerCase(authorizationID.toString());
        if (!lowerAuthZIDStr.startsWith("dn:") &&
            !lowerAuthZIDStr.startsWith("u:"))
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message =
              ERR_PROXYAUTH2_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message,
              e);
        }
      }

      return new ProxiedAuthV2Control(isCritical, authorizationID);
    }

    public String getOID()
    {
      return OID_PROXIED_AUTH_V2;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<ProxiedAuthV2Control> DECODER =
    new Decoder();

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // The authorization ID from the control value.
  private ByteString authorizationID;



  /**
   * Creates a new instance of the proxied authorization v2 control with the
   * provided information.
   *
   * @param  authorizationID  The authorization ID from the control value.
   */
  public ProxiedAuthV2Control(ByteString authorizationID)
  {
    this(true, authorizationID);
  }



  /**
   * Creates a new instance of the proxied authorization v2 control with the
   * provided information.
   *
   * @param  isCritical       Indicates whether support for this control
   *                          should be considered a critical part of the
   *                          server processing.
   * @param  authorizationID  The authorization ID from the control value.
   */
  public ProxiedAuthV2Control(boolean isCritical, ByteString authorizationID)
  {
    super(OID_PROXIED_AUTH_V2, isCritical);

    ensureNotNull(authorizationID);

    this.authorizationID = authorizationID;
  }



  /**
   * Writes this control's value to an ASN.1 writer. The value (if any) must be
   * written as an ASN1OctetString.
   *
   * @param writer The ASN.1 writer to use.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  @Override
  protected void writeValue(ASN1Writer writer) throws IOException {
    writer.writeOctetString(authorizationID);
  }



  /**
   * Retrieves the authorization ID for this proxied authorization V2 control.
   *
   * @return  The authorization ID for this proxied authorization V2 control.
   */
  public ByteString getAuthorizationID()
  {
    return authorizationID;
  }



  /**
   * Retrieves the authorization entry for this proxied authorization V2
   * control.  It will also perform any necessary password policy checks to
   * ensure that the associated user account is suitable for use in performing
   * this processing.
   *
   * @return  The entry for user specified as the authorization identity in this
   *          proxied authorization V1 control, or {@code null} if the
   *          authorization DN is the null DN.
   *
   * @throws  DirectoryException  If the target user does not exist or is not
   *                              available for use, or if a problem occurs
   *                              while making the determination.
   */
  public Entry getAuthorizationEntry()
         throws DirectoryException
  {
    // Check for a zero-length value, which would be for an anonymous user.
    if (authorizationID.length() == 0)
    {
      return null;
    }


    // Get a lowercase string representation.  It must start with either "dn:"
    // or "u:".
    String lowerAuthzID = toLowerCase(authorizationID.toString());
    if (lowerAuthzID.startsWith("dn:"))
    {
      // It's a DN, so decode it and see if it exists.  If it's the null DN,
      // then just assume that it does.
      DN authzDN = DN.decode(lowerAuthzID.substring(3));
      if (authzDN.isNullDN())
      {
        return null;
      }
      else
      {
        // See if the authorization DN is one of the alternate bind DNs for one
        // of the root users and if so then map it accordingly.
        DN actualDN = DirectoryServer.getActualRootBindDN(authzDN);
        if (actualDN != null)
        {
          authzDN = actualDN;
        }

        Lock entryLock = null;
        for (int i=0; i < 3; i++)
        {
          entryLock = LockManager.lockRead(authzDN);
          if (entryLock != null)
          {
            break;
          }
        }

        if (entryLock == null)
        {
          Message message =
              ERR_PROXYAUTH2_CANNOT_LOCK_USER.get(String.valueOf(authzDN));
          throw new DirectoryException(
                  ResultCode.AUTHORIZATION_DENIED, message);
        }

        try
        {
          Entry userEntry = DirectoryServer.getEntry(authzDN);
          if (userEntry == null)
          {
            // The requested user does not exist.
            Message message = ERR_PROXYAUTH2_NO_SUCH_USER.get(lowerAuthzID);
            throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED,
                                         message);
          }

          // FIXME -- We should provide some mechanism for enabling debug
          // processing.
          PasswordPolicyState pwpState =
               new PasswordPolicyState(userEntry, false);
          if (pwpState.isDisabled() || pwpState.isAccountExpired() ||
              pwpState.lockedDueToFailures() ||
              pwpState.lockedDueToIdleInterval() ||
              pwpState.lockedDueToMaximumResetAge() ||
              pwpState.isPasswordExpired())
          {
            Message message =
                ERR_PROXYAUTH2_UNUSABLE_ACCOUNT.get(String.valueOf(authzDN));
            throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED,
                                         message);
          }


          // If we've made it here, then the user is acceptable.
          return userEntry;
        }
        finally
        {
          LockManager.unlock(authzDN, entryLock);
        }
      }
    }
    else if (lowerAuthzID.startsWith("u:"))
    {
      // If the authorization ID is just "u:", then it's an anonymous request.
      if (lowerAuthzID.length() == 2)
      {
        return null;
      }


      // Use the proxied authorization identity mapper to resolve the username
      // to an entry.
      IdentityMapper<?> proxyMapper =
           DirectoryServer.getProxiedAuthorizationIdentityMapper();
      if (proxyMapper == null)
      {
        Message message = ERR_PROXYAUTH2_NO_IDENTITY_MAPPER.get();
        throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED, message);
      }

      Entry userEntry = proxyMapper.getEntryForID(lowerAuthzID.substring(2));
      if (userEntry == null)
      {
        Message message = ERR_PROXYAUTH2_NO_SUCH_USER.get(lowerAuthzID);
        throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED, message);
      }
      else
      {
        // FIXME -- We should provide some mechanism for enabling debug
        // processing.
        PasswordPolicyState pwpState =
             new PasswordPolicyState(userEntry, false);
        if (pwpState.isDisabled() || pwpState.isAccountExpired() ||
            pwpState.lockedDueToFailures() ||
            pwpState.lockedDueToIdleInterval() ||
            pwpState.lockedDueToMaximumResetAge() ||
            pwpState.isPasswordExpired())
        {
          Message message = ERR_PROXYAUTH2_UNUSABLE_ACCOUNT.get(
              String.valueOf(userEntry.getDN()));
          throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED,
                                       message);
        }

        return userEntry;
      }
    }
    else
    {
      Message message = ERR_PROXYAUTH2_INVALID_AUTHZID.get(lowerAuthzID);
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
    }
  }



  /**
   * Appends a string representation of this proxied auth v2 control to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("ProxiedAuthorizationV2Control(authzID=\"");
    buffer.append(authorizationID);
    buffer.append("\")");
  }
}

