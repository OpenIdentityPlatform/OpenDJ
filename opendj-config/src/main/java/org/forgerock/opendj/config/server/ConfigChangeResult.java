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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.server;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.util.Utils;

/**
 * This class defines a data structure that can be used to hold information
 * about the result of processing a configuration change.
 */
public final class ConfigChangeResult {
    /**
     * A set of messages describing the changes that were made, any
     * action that may be required, or any problems that were encountered.
     */
    private final List<LocalizableMessage> messages = new ArrayList<>();

    /**
     * Indicates whether one or more of the changes requires
     * administrative action in order to take effect.
     */
    private boolean adminActionRequired;

    /** The result code to return to the client from this configuration change. */
    private ResultCode resultCode = ResultCode.SUCCESS;

    /** Creates a new config change result object with the provided information. */
    public ConfigChangeResult() {
        // nothing more to do
    }

    /**
     * Retrieves the result code for this config change result.
     *
     * @return The result code for this config change result.
     */
    public ResultCode getResultCode() {
        return resultCode;
    }

    /**
     * Specifies the result code for this config change result.
     *
     * @param resultCode
     *            The result code for this config change result.
     */
    public void setResultCode(ResultCode resultCode) {
        this.resultCode = resultCode;
    }

    /**
     * Sets the provided result code for this config change result
     * if the current result code is success.
     *
     * @param newResultCode
     *          The new result code for this config change result.
     */
    public void setResultCodeIfSuccess(ResultCode newResultCode) {
        if (getResultCode() == ResultCode.SUCCESS) {
            setResultCode(newResultCode);
        }
    }

    /**
     * Aggregates the results from the provided config change result.
     *
     * @param other
     *          The config change result to aggregate
     */
    public void aggregate(ConfigChangeResult other) {
        if (other.getResultCode() != ResultCode.SUCCESS) {
            setResultCodeIfSuccess(other.getResultCode());
            messages.addAll(other.getMessages());
        }
    }

    /**
     * Indicates whether administrative action is required before one or more of
     * the changes will take effect.
     *
     * @return <CODE>true</CODE> if one or more of the configuration changes
     *         require administrative action to take effect, or
     *         <CODE>false</CODE> if not.
     */
    public boolean adminActionRequired() {
        return adminActionRequired;
    }

    /**
     * Specifies whether administrative action is required before one or more of
     * the changes will take effect.
     *
     * @param adminActionRequired
     *            Specifies whether administrative action is required before one
     *            or more of the changes will take effect.
     */
    public void setAdminActionRequired(boolean adminActionRequired) {
        this.adminActionRequired = adminActionRequired;
    }

    /**
     * Retrieves the set of messages that provide explanation for the processing
     * of the configuration changes. This list may be modified by the caller.
     *
     * @return The set of messages that provide explanation for the processing
     *         of the configuration changes.
     */
    public List<LocalizableMessage> getMessages() {
        return messages;
    }

    /**
     * Adds the provided message to the set of messages for this config change
     * result.
     *
     * @param message
     *            The message to add to the set of messages for this config
     *            change result.
     */
    public void addMessage(LocalizableMessage message) {
        messages.add(message);
    }

    /**
     * Retrieves a string representation of this config change result.
     *
     * @return A string representation of this config change result.
     */
    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        toString(buffer);
        return buffer.toString();
    }

    /**
     * Appends a string representation of this config change result to the
     * provided buffer.
     *
     * @param buffer
     *            The buffer to which the information should be appended.
     */
    public void toString(StringBuilder buffer) {
        buffer.append("ConfigChangeResult(result=");
        buffer.append(resultCode);
        buffer.append(", adminActionRequired=");
        buffer.append(adminActionRequired);
        buffer.append(", messages={");
        Utils.joinAsString(buffer, ",", messages);
        buffer.append("})");
    }
}
