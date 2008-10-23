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
package org.opends.server.servicetag;

import java.io.*;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.TimeZone;
import java.util.UUID;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.debug.DebugLogger.*;

/**
 * Utility class for org.opends.server.servicetag package.
 */
class Util {

    /**
    * The tracer object for the debug logger.
    */
    private static final DebugTracer TRACER = getTracer();

    /**
     * Returns a generated a random instance URN.
     * @return a String "urn:st:....".
     */
    public static String generateURN() {
        return "urn:st:" + UUID.randomUUID().toString();
    }

    /**
     * Returns the int represntation of the String.
     * @param value to tranform.
     * @return the int value.
     */
    public static int getIntValue(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"" + value + "\"" +
                " expected to be an integer");
        }
    }


    /**
     * Parses a timestamp string in YYYY-MM-dd HH:mm:ss GMT format.
     * @param timestamp Timestamp in the YYYY-MM-dd HH:mm:ss GMT format.
     * @return Date.
     */
    public static Date parseTimestamp(String timestamp) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        try {
            return df.parse(timestamp);
        } catch (ParseException e) {
           if (debugEnabled()) {
                TRACER.debugCaught(DebugLogLevel.WARNING, e);
           }
           return new Date();
        }
    }

    /**
     * Returns the output of the process execution.
     * @param p process.
     * @return the output of the process execution.
     * @throws java.io.IOException if a problem occurs.
     */
    public static String commandOutput(Process p) throws IOException {
        Reader r = null;
        Reader err = null;
        try {
            r = new InputStreamReader(p.getInputStream());
            err = new InputStreamReader(p.getErrorStream());
            String output = commandOutput(r);
            String errorMsg = commandOutput(err);
            p.waitFor();
            return output + errorMsg.trim();
        } catch (InterruptedException e) {
            return e.getMessage();
        } finally {
            if (r != null) {
                r.close();
            }
            if (err != null) {
                err.close();
            }
        }
    }

    /**
     * Returns the reader content as a String.
     * @param r Reader.
     * @return the Reader content.
     * @throws java.io.IOException if a problem occurs.
     */
    public static String commandOutput(Reader r) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = r.read()) > 0) {
            if (c != '\r') {
                sb.append((char) c);
            }
        }
        return sb.toString();
    }

    /**
     * Returns this java string as a null-terminated byte array.
     */
    private static byte[] stringToByteArray(String str) {
        return (str + "\u0000").getBytes();
    }

    /**
     * Converts a null-terminated byte array to java string.
     */
    private static String byteArrayToString(byte[] array) {
      return new String(array, 0, array.length -1);
    }

    /**
     * Gets the stclient path using a well known location from
     * the Windows platform Registry, otherwise it will return null.
     * @return the File representation of the stclient on windows platform.
     */
    public static File getWindowsStClientFile() {
        File out = null;
        String regKey =
       "software\\microsoft\\windows\\currentversion\\app paths\\stclient.exe";
        String keyName = "" ; // use the default  key
        String path = getRegistryKey(regKey, keyName);

        if (path != null && (new File(path)).exists()) {
            out = new File(path);
        }
        return out;
    }

    /**
     * This uses reflection to access a private java windows registry
     * interface, any changes to that Class must be appropriately adjusted.
     * Returns a null if unsuccessful.
     */
    private static String getRegistryKey(String regKey, String keyName) {
        String out = null;
        try {
            Class<?> clazz = Class.forName(
                    "java.util.prefs.WindowsPreferences");

            // Get the registry methods
            Method winRegOpenKeyM = clazz.getDeclaredMethod(
                    "WindowsRegOpenKey",
                    int.class, byte[].class, int.class);
            winRegOpenKeyM.setAccessible(true);

            Method winRegCloseKeyM = clazz.getDeclaredMethod(
                    "WindowsRegCloseKey",
                    int.class);
            winRegCloseKeyM.setAccessible(true);

            Method winRegQueryValueM = clazz.getDeclaredMethod(
                    "WindowsRegQueryValueEx",
                    int.class, byte[].class);
            winRegQueryValueM.setAccessible(true);

            // Get all the constants we need
            int HKLM = getValueFromStaticField("HKEY_LOCAL_MACHINE", clazz);
            int KEY_READ = getValueFromStaticField("KEY_READ", clazz);
            int ERROR_CODE = getValueFromStaticField("ERROR_CODE", clazz);
            int NATIVE_HANDLE = getValueFromStaticField("NATIVE_HANDLE", clazz);
            int ERROR_SUCCESS = getValueFromStaticField("ERROR_SUCCESS", clazz);

            // Convert keys
            byte[] reg = stringToByteArray(regKey);
            byte[] key = stringToByteArray(keyName);

            // Open the registry
            int[] result = (int[]) winRegOpenKeyM.invoke(
                    null, HKLM, reg, KEY_READ);

            if (result[ERROR_CODE] == ERROR_SUCCESS) {
                byte[] stvalue = (byte[]) winRegQueryValueM.invoke(null,
                    result[NATIVE_HANDLE], key);
                out = byteArrayToString(stvalue);
                winRegCloseKeyM.invoke(null, result[NATIVE_HANDLE]);
            }
        } catch (Exception ex) {
        }
        return out;
    }

    private static int getValueFromStaticField(
            String fldName, Class<?> klass) throws Exception {
        Field f = klass.getDeclaredField(fldName);
        f.setAccessible(true);
        return f.getInt(null);
    }
}
