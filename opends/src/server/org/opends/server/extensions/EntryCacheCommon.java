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
package org.opends.server.extensions;
import org.opends.messages.Message;

import static org.opends.server.loggers.ErrorLogger.logError;
import org.opends.messages.MessageDescriptor;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.SortedSet;

import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;

import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;


/**
 * This class provides some common tools to all entry cache implementations.
 */
public class EntryCacheCommon
{
  /**
   * Configuration phases. Each value identifies a configuration step:
   * - PHASE_INIT       when invoking method initializeEntryCache()
   * - PHASE_ACCEPTABLE when invoking method isConfigurationChangeAcceptable()
   * - PHASE_APPLY      when invoking method applyConfigurationChange()
   */
  public static enum ConfigPhase
  {
    /**
     * Indicates that entry cache is in initialization check phase.
     */
    PHASE_INIT,

    /**
     * Indicates that entry cache is in configuration check phase.
     */
    PHASE_ACCEPTABLE,

    /**
     * Indicates that entry cache is applying its configuration.
     */
    PHASE_APPLY
  }

  /**
   * Error handler used by local methods to report configuration error.
   * The error handler simplifies the code of initializeEntryCache(),
   * isConfigurationChangeAcceptable() and applyConfigurationChanges() methods.
   */
  public class ConfigErrorHandler
  {
    // Configuration phase.
    private EntryCacheCommon.ConfigPhase _configPhase;

    // Unacceptable reasons. Used when _configPhase is PHASE_ACCEPTABLE.
    private List<Message> _unacceptableReasons;

    // Error messages. Used when _configPhase is PHASE_APPLY.
    private ArrayList<Message> _errorMessages;

    // Result code. Used when _configPhase is PHASE_APPLY.
    private ResultCode _resultCode;

    // Acceptable Configuration ? Used when _configPhase is PHASE_ACCEPTABLE
    // or PHASE_APPLY.
    private boolean _isAcceptable;

    /**
     * Create an error handler.
     *
     * @param configPhase          the configuration phase for which the
     *                             error handler is used
     * @param unacceptableReasons  the reasons why the configuration cannot
     *                             be applied (during PHASE_ACCEPTABLE phase)
     * @param errorMessages        the errors found when applying a new
     *                             configuration (during PHASE_APPLY phase)
     */
    public ConfigErrorHandler (
        EntryCacheCommon.ConfigPhase configPhase,
        List<Message> unacceptableReasons,
        ArrayList<Message>            errorMessages
        )
    {
      _configPhase         = configPhase;
      _unacceptableReasons = unacceptableReasons;
      _errorMessages       = errorMessages;
      _resultCode          = ResultCode.SUCCESS;
      _isAcceptable        = true;
    }

    /**
     * Report an error.
     *
     * @param error        the error to report
     * @param isAcceptable <code>true</code> if the configuration is acceptable
     * @param resultCode   the change result for the current configuration
     */
    public void reportError(
            Message error,
            boolean isAcceptable,
            ResultCode resultCode
    )
    {
      switch (_configPhase)
      {
      case PHASE_INIT:
        {
        logError (error);
        break;
        }
      case PHASE_ACCEPTABLE:
        {
        _unacceptableReasons.add (error);
        _isAcceptable = isAcceptable;
        break;
        }
      case PHASE_APPLY:
        {
        _errorMessages.add (error);
        _isAcceptable = isAcceptable;
        if (_resultCode == ResultCode.SUCCESS)
        {
          _resultCode = resultCode;
        }
        break;
        }
      }
    }

    /**
     * Get the current result code that was elaborated right after a
     * configuration has been applied.
     *
     * @return the current result code
     */
    public ResultCode getResultCode()
    {
      return _resultCode;
    }

    /**
     * Get the current isAcceptable flag. The isAcceptable flag is elaborated
     * right after the configuration was checked.
     *
     * @return the isAcceptable flag
     */
    public boolean getIsAcceptable()
    {
      return _isAcceptable;
    }

    /**
     * Get the current unacceptable reasons. The unacceptable reasons are
     * elaborated when the configuration is checked.
     *
     * @return the list of unacceptable reasons
     */
    public List<Message> getUnacceptableReasons()
    {
      return _unacceptableReasons;
    }

    /**
     * Get the current error messages. The error messages are elaborated
     * when the configuration is applied.
     *
     * @return the list of error messages
     */
    public ArrayList<Message> getErrorMessages()
    {
      return _errorMessages;
    }

    /**
     * Get the current configuration phase. The configuration phase indicates
     * whether the entry cache is in initialization step, or in configuration
     * checking step or in configuration being applied step.
     *
     * @return the current configuration phase.
     */
    public ConfigPhase getConfigPhase()
    {
      return _configPhase;
    }
  } // ConfigErrorHandler


  /**
   * Reads a list of string filters and convert it to a list of search
   * filters.
   *
   * @param filters  the list of string filter to convert to search filters
   * @param decodeErrorMsg  the error message ID to use in case of error
   * @param errorHandler      an handler used to report errors
   *                          during decoding of filter
   * @param noFilterMsg     the error message ID to use when none of the
   *                          filters was decoded properly
   * @param configEntryDN     the DN of the configuration entry for the
   *                          entry cache
   *
   * @return the set of search filters
   */
  public static HashSet<SearchFilter> getFilters (
      SortedSet<String>       filters,
      MessageDescriptor.Arg3<CharSequence, CharSequence, CharSequence>
                              decodeErrorMsg,
      MessageDescriptor.Arg1<CharSequence>
                              noFilterMsg,
      ConfigErrorHandler      errorHandler,
      DN                      configEntryDN
      )
  {
    // Returned value
    HashSet<SearchFilter> searchFilters = new HashSet<SearchFilter>();

    // Convert the string filters to search filters.
    if ((filters != null) && (! filters.isEmpty()))
    {
      for (String curFilter: filters)
      {
        try
        {
          searchFilters.add (SearchFilter.createFilterFromString (curFilter));
        }
        catch (DirectoryException de)
        {
          // We couldn't decode this filter. Log a warning and continue.
          errorHandler.reportError(
                  decodeErrorMsg.get(
                          String.valueOf(configEntryDN),
                          curFilter,
                          stackTraceToSingleLineString(de)),
                  false,
                  ResultCode.INVALID_ATTRIBUTE_SYNTAX
          );
        }
      }

      // If none of the filters was decoded properly log an error message
      // (only if we are in initialize phase).
      if ((errorHandler.getConfigPhase() == ConfigPhase.PHASE_INIT)
          && searchFilters.isEmpty())
      {
        if (noFilterMsg != null) {
          errorHandler.reportError(
                  noFilterMsg.get(String.valueOf(configEntryDN)),
                  false, null);
        } else {
          errorHandler.reportError( // TODO: i18n
                  Message.raw("No filter provided: %s", configEntryDN),
                  false, null);
        }
      }
    }

    // done
    return searchFilters;
  }


  /**
   * Create a new error handler.
   *
   * @param configPhase          the configuration phase for which the
   *                             error handler is used
   * @param unacceptableReasons  the reasons why the configuration cannot
   *                             be applied (during PHASE_ACCEPTABLE phase)
   * @param errorMessages        the errors found when applying a new
   *                             configuration (during PHASE_APPLY phase)
   *
   * @return a new configuration error handler
   */
  public static ConfigErrorHandler getConfigErrorHandler (
      EntryCacheCommon.ConfigPhase  configPhase,
      List<Message> unacceptableReasons,
      ArrayList<Message>             errorMessages
      )
  {
    ConfigErrorHandler errorHandler = null;

    EntryCacheCommon ec = new EntryCacheCommon();

    errorHandler = ec.new ConfigErrorHandler (
        configPhase, unacceptableReasons, errorMessages
        );
    return errorHandler;
  }

}

