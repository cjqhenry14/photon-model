/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.model.adapters.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vmware.xenon.common.Utils;

/**
 * URI utilities.
 */
public class AdapterUriUtil {
    /** Captures URI template variable names. */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{([^/]+?)\\}");

    /**
     * Expands a pathTemplate {@link String} with the given variable values and returns the expanded
     * URI. The replacement of variables and their values are based on the order of the variable
     * values. For example, pathTemplate "localhost/resourceA/{b}/{c}" with pathVariableValues ["d",
     * "e"] will expand to "localhost/resourceA/d/e" "{123}", etc...
     *
     * @param pathTemplate
     *        A {@link String} that represents the pathTemplate in form of ".../{a}/{b}/...".
     * @param pathVariableValues
     *        The values that will replace the variables from the template.
     * @return The expanded URI.
     */
    public static String expandUriPathTemplate(String pathTemplate, String... pathVariableValues) {
        for (String value : pathVariableValues) {
            if (containsPathVariables(pathTemplate)) {
                try {
                    pathTemplate = VARIABLE_PATTERN.matcher(pathTemplate)
                            .replaceFirst(URLEncoder.encode(value, Utils.CHARSET));
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalStateException("Unsupported encoding");
                }
            } else {
                throw new IllegalArgumentException("Too many variable values to expand");
            }
        }
        checkRemainingVariables(pathTemplate);
        return pathTemplate;
    }

    /**
     * Determines whether a {@link String} contains URI variable, a character sequence like "{a}",
     * "{123}", etc...
     *
     * @param pathTemplate
     *        A string to examine.
     * @return True if URI contains a variable, false otherwise.
     */
    private static boolean containsPathVariables(String pathTemplate) {
        return VARIABLE_PATTERN.matcher(pathTemplate).find();
    }

    private static void checkRemainingVariables(String pathTemplate) {
        StringBuilder errorMessage = null;
        Matcher match = VARIABLE_PATTERN.matcher(pathTemplate);
        while (match.find()) {
            if (errorMessage == null) {
                errorMessage = new StringBuilder("Missing variable values to expand");
                errorMessage.append(" ").append(wrapVariable(match.group(0)));
            } else {
                errorMessage.append(", ").append(wrapVariable(match.group(0)));
            }
            pathTemplate = VARIABLE_PATTERN.matcher(pathTemplate).replaceFirst("");
            match = VARIABLE_PATTERN.matcher(pathTemplate);
        }
        if (errorMessage != null) {
            throw new IllegalArgumentException(errorMessage.toString());
        }
    }

    private static String wrapVariable(String variable) {
        return "'" + variable + "'";
    }
}
