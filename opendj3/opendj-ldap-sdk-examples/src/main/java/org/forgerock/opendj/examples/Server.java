/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2012 ForgeRock AS
 */

package org.forgerock.opendj.examples;



import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.net.ssl.SSLContext;

import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.requests.*;
import org.forgerock.opendj.ldap.responses.*;
import org.forgerock.opendj.ldif.*;



/**
 * An LDAP directory server which exposes data contained in an LDIF file. This
 * is implementation is very simple and is only intended as an example:
 * <ul>
 * <li>It does not support SSL connections
 * <li>It does not support StartTLS
 * <li>It does not support Abandon or Cancel requests
 * <li>Very basic authentication and authorization support.
 * </ul>
 * This example takes the following command line parameters:
 *
 * <pre>
 *  &lt;listenAddress> &lt;listenPort> [&lt;ldifFile>]
 * </pre>
 */
public final class Server
{
  private static final class MemoryBackend implements
      RequestHandler<RequestContext>
  {
    private final ConcurrentSkipListMap<DN, Entry> entries;
    private final ReentrantReadWriteLock entryLock = new ReentrantReadWriteLock();



    private MemoryBackend(final ConcurrentSkipListMap<DN, Entry> entries)
    {
      this.entries = entries;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleAdd(final RequestContext requestContext,
        final AddRequest request,
        final IntermediateResponseHandler intermediateResponseHandler,
        final ResultHandler<? super Result> resultHandler)
    {
      // TODO: controls.
      entryLock.writeLock().lock();
      try
      {
        DN dn = request.getName();
        if (entries.containsKey(dn))
        {
          resultHandler.handleErrorResult(ErrorResultException.newErrorResult(
              ResultCode.ENTRY_ALREADY_EXISTS, "The entry " + dn.toString()
                  + " already exists"));
        }

        DN parent = dn.parent();
        if (!entries.containsKey(parent))
        {
          resultHandler.handleErrorResult(ErrorResultException.newErrorResult(
              ResultCode.NO_SUCH_OBJECT,
              "The parent entry " + parent.toString() + " does not exist"));
        }
        else
        {
          entries.put(dn, request);
          resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
        }
      }
      finally
      {
        entryLock.writeLock().unlock();
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleBind(final RequestContext requestContext,
        final int version, final BindRequest request,
        final IntermediateResponseHandler intermediateResponseHandler,
        final ResultHandler<? super BindResult> resultHandler)
    {
      if (request.getAuthenticationType() != ((byte) 0x80))
      {
        // TODO: SASL authentication not implemented.
        resultHandler.handleErrorResult(newErrorResult(
            ResultCode.PROTOCOL_ERROR,
            "non-SIMPLE authentication not supported: "
                + request.getAuthenticationType()));
      }
      else
      {
        // TODO: always succeed.
        resultHandler.handleResult(Responses.newBindResult(ResultCode.SUCCESS));
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleCompare(final RequestContext requestContext,
        final CompareRequest request,
        final IntermediateResponseHandler intermediateResponseHandler,
        final ResultHandler<? super CompareResult> resultHandler)
    {
      // TODO:
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleDelete(final RequestContext requestContext,
        final DeleteRequest request,
        final IntermediateResponseHandler intermediateResponseHandler,
        final ResultHandler<? super Result> resultHandler)
    {
      // TODO: controls.
      entryLock.writeLock().lock();
      try
      {
        // TODO: check for children.
        DN dn = request.getName();
        if (!entries.containsKey(dn))
        {
          resultHandler.handleErrorResult(ErrorResultException.newErrorResult(
              ResultCode.NO_SUCH_OBJECT, "The entry " + dn.toString()
                  + " does not exist"));
        }
        else
        {
          entries.remove(dn);
          resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
        }
      }
      finally
      {
        entryLock.writeLock().unlock();
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public <R extends ExtendedResult> void handleExtendedRequest(
        final RequestContext requestContext, final ExtendedRequest<R> request,
        final IntermediateResponseHandler intermediateResponseHandler,
        final ResultHandler<? super R> resultHandler)
    {
      // TODO: not implemented.
      resultHandler.handleErrorResult(newErrorResult(ResultCode.PROTOCOL_ERROR,
          "Extended request operation not supported"));
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleModify(final RequestContext requestContext,
        final ModifyRequest request,
        final IntermediateResponseHandler intermediateResponseHandler,
        final ResultHandler<? super Result> resultHandler)
    {
      // TODO: controls.
      // TODO: read lock is not really enough since concurrent updates may
      // still occur to the same entry.
      entryLock.readLock().lock();
      try
      {
        DN dn = request.getName();
        Entry entry = entries.get(dn);
        if (entry == null)
        {
          resultHandler.handleErrorResult(ErrorResultException.newErrorResult(
              ResultCode.NO_SUCH_OBJECT, "The entry " + dn.toString()
                  + " does not exist"));
        }

        Entry newEntry = new LinkedHashMapEntry(entry);
        for (Modification mod : request.getModifications())
        {
          ModificationType modType = mod.getModificationType();
          if (modType.equals(ModificationType.ADD))
          {
            // TODO: Reject empty attribute and duplicate values.
            newEntry.addAttribute(mod.getAttribute(), null);
          }
          else if (modType.equals(ModificationType.DELETE))
          {
            // TODO: Reject missing values.
            newEntry.removeAttribute(mod.getAttribute(), null);
          }
          else if (modType.equals(ModificationType.REPLACE))
          {
            newEntry.replaceAttribute(mod.getAttribute());
          }
          else
          {
            resultHandler.handleErrorResult(newErrorResult(
                ResultCode.PROTOCOL_ERROR,
                "Modify request contains an unsupported modification type"));
            return;
          }
        }

        entries.put(dn, newEntry);
        resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
      }
      finally
      {
        entryLock.readLock().unlock();
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleModifyDN(final RequestContext requestContext,
        final ModifyDNRequest request,
        final IntermediateResponseHandler intermediateResponseHandler,
        final ResultHandler<? super Result> resultHandler)
    {
      // TODO: not implemented.
      resultHandler.handleErrorResult(newErrorResult(ResultCode.PROTOCOL_ERROR,
          "ModifyDN request operation not supported"));
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleSearch(final RequestContext requestContext,
        final SearchRequest request,
        final IntermediateResponseHandler intermediateResponseHandler,
        final SearchResultHandler resultHandler)
    {
      // TODO: controls, limits, etc.
      entryLock.readLock().lock();
      try
      {
        DN dn = request.getName();
        Entry baseEntry = entries.get(dn);
        if (baseEntry == null)
        {
          resultHandler.handleErrorResult(ErrorResultException.newErrorResult(
              ResultCode.NO_SUCH_OBJECT, "The entry " + dn.toString()
                  + " does not exist"));
          return;
        }

        SearchScope scope = request.getScope();
        Filter filter = request.getFilter();
        Matcher matcher = filter.matcher();

        if (scope.equals(SearchScope.BASE_OBJECT))
        {
          if (matcher.matches(baseEntry).toBoolean())
          {
            sendEntry(request, resultHandler, baseEntry);
          }
        }
        else if (scope.equals(SearchScope.SINGLE_LEVEL))
        {
          NavigableMap<DN, Entry> subtree = entries.tailMap(dn, false);
          for (Entry entry : subtree.values())
          {
            // Check for cancellation.
            requestContext.checkIfCancelled(false);

            DN childDN = entry.getName();
            if (childDN.isChildOf(dn))
            {
              if (!matcher.matches(entry).toBoolean())
              {
                continue;
              }

              if (!sendEntry(request, resultHandler, entry))
              {
                // Caller has asked to stop sending results.
                break;
              }
            }
            else if (!childDN.isSubordinateOrEqualTo(dn))
            {
              // The remaining entries will be out of scope.
              break;
            }
          }
        }
        else if (scope.equals(SearchScope.WHOLE_SUBTREE))
        {
          NavigableMap<DN, Entry> subtree = entries.tailMap(dn);
          for (Entry entry : subtree.values())
          {
            // Check for cancellation.
            requestContext.checkIfCancelled(false);

            DN childDN = entry.getName();
            if (childDN.isSubordinateOrEqualTo(dn))
            {
              if (!matcher.matches(entry).toBoolean())
              {
                continue;
              }

              if (!sendEntry(request, resultHandler, entry))
              {
                // Caller has asked to stop sending results.
                break;
              }
            }
            else
            {
              // The remaining entries will be out of scope.
              break;
            }
          }
        }
        else
        {
          resultHandler.handleErrorResult(newErrorResult(
              ResultCode.PROTOCOL_ERROR,
              "Search request contains an unsupported search scope"));
          return;
        }

        resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
      }
      catch (CancelledResultException e)
      {
        resultHandler.handleErrorResult(e);
      }
      finally
      {
        entryLock.readLock().unlock();
      }
    }



    private boolean sendEntry(SearchRequest request,
        SearchResultHandler resultHandler, Entry entry)
    {
      // TODO: check filter, strip attributes.
      return resultHandler.handleEntry(Responses.newSearchResultEntry(entry));
    }
  }



  /**
   * Main method.
   *
   * @param args
   *          The command line arguments: listen address, listen port, ldifFile
   */
  public static void main(final String[] args)
  {
    if (args.length != 3 && args.length != 6)
    {
      System.err.println("Usage: listenAddress listenPort ldifFile "
          + "[keyStoreFile keyStorePassword certNickname]");
      System.exit(1);
    }

    // Parse command line arguments.
    final String localAddress = args[0];
    final int localPort = Integer.parseInt(args[1]);
    final String ldifFileName = args[2];
    final String keyStoreFileName = (args.length == 6) ? args[3] : null;
    final String keyStorePassword = (args.length == 6) ? args[4] : null;
    final String certNickname = (args.length == 6) ? args[5] : null;

    // Create the memory backend.
    final ConcurrentSkipListMap<DN, Entry> entries =
        readEntriesFromLDIF(ldifFileName);
    final MemoryBackend backend = new MemoryBackend(entries);

    // Create a server connection adapter.
    final ServerConnectionFactory<LDAPClientContext, Integer> connectionHandler =
        Connections.newServerConnectionFactory(backend);

    // Create listener.
    LDAPListener listener = null;
    try
    {
      final LDAPListenerOptions options = new LDAPListenerOptions()
          .setBacklog(4096);

      if (keyStoreFileName != null)
      {
        // Configure SSL/TLS and enable it when connections are accepted.
        final SSLContext sslContext = new SSLContextBuilder()
            .setKeyManager(
                KeyManagers.useSingleCertificate(certNickname, KeyManagers
                    .useKeyStoreFile(keyStoreFileName,
                        keyStorePassword.toCharArray(), null)))
            .setTrustManager(TrustManagers.trustAll()).getSSLContext();

        ServerConnectionFactory<LDAPClientContext, Integer> sslWrapper =
            new ServerConnectionFactory<LDAPClientContext, Integer>()
        {

          public ServerConnection<Integer> handleAccept(
              LDAPClientContext clientContext) throws ErrorResultException
          {
            clientContext.enableTLS(sslContext, null, null, false, false);
            return connectionHandler.handleAccept(clientContext);
          }
        };

        listener = new LDAPListener(localAddress, localPort, sslWrapper,
            options);
      }
      else
      {
        // No SSL.
        listener = new LDAPListener(localAddress, localPort, connectionHandler,
            options);
      }
      System.out.println("Press any key to stop the server...");
      System.in.read();
    }
    catch (final Exception e)
    {
      System.out
          .println("Error listening on " + localAddress + ":" + localPort);
      e.printStackTrace();
    }
    finally
    {
      if (listener != null)
      {
        listener.close();
      }
    }
  }



  /**
   * Reads the entries from the named LDIF file.
   *
   * @param ldifFileName
   *          The name of the LDIF file.
   * @return The entries.
   */
  private static ConcurrentSkipListMap<DN, Entry> readEntriesFromLDIF(
      final String ldifFileName)
  {
    final ConcurrentSkipListMap<DN, Entry> entries;
    // Read the LDIF.
    InputStream ldif;
    try
    {
      ldif = new FileInputStream(ldifFileName);
    }
    catch (final FileNotFoundException e)
    {
      System.err.println(e.getMessage());
      System.exit(ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue());
      return null; // Satisfy compiler.
    }

    entries = new ConcurrentSkipListMap<DN, Entry>();
    final LDIFEntryReader reader = new LDIFEntryReader(ldif);
    try
    {
      while (reader.hasNext())
      {
        Entry entry = reader.readEntry();
        entries.put(entry.getName(), entry);
      }
    }
    catch (final IOException e)
    {
      System.err.println(e.getMessage());
      System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
      return null; // Satisfy compiler.
    }
    finally
    {
      try
      {
        reader.close();
      }
      catch (final IOException ignored)
      {
        // Ignore.
      }
    }

    // Quickly sanity check that every entry (except root entries) have a
    // parent.
    boolean isValid = true;
    for (DN dn : entries.keySet())
    {
      if (dn.size() > 1)
      {
        DN parent = dn.parent();
        if (!entries.containsKey(parent))
        {
          System.err.println("The entry \"" + dn.toString()
              + "\" does not have a parent");
          isValid = false;
        }
      }
    }
    if (!isValid)
    {
      System.exit(1);
    }
    return entries;
  }



  private Server()
  {
    // Not used.
  }
}
