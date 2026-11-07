/*
 * Copyright (c) 2026 Fortrow LLC.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Fortrow-Commercial
 *
 * JFlux is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License, version 3, as published by
 * the Free Software Foundation.
 *
 * JFlux is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JFlux. If not, see <https://www.gnu.org/licenses/>.
 *
 * Alternatively, a separate commercial license is available from Fortrow LLC.
 * For terms permitting use without the AGPL copyleft requirements, contact
 * ryan@fortrow.com. Commercial rights require a separate written agreement.
 * See LICENSE and COMMERCIAL-LICENSE.md.
 *
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 */
package com.fortrow.jflux.inflection;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Inflector {

    private List<String> uncountable = new ArrayList();
    private List<Rule> singular = new ArrayList();
    private List<Rule> plural = new ArrayList();
    private Map<String, String> irregular = new HashMap();

    public String singularlize(String orig) {
        if (uncountable.contains(orig)) {
            return orig;
        }

        for (Map.Entry<String, String> entry : irregular.entrySet()) {
            if (entry.getValue().equals(orig)) {
                return entry.getKey();
            }
        }

        for (Rule r : singular) {
            Matcher m = r.getRegex().matcher(orig);
            if (m.find()) {
                return m.replaceAll(r.getReplacement());
            }
        }

        return orig;
    }

    public String pluralize(String orig) {
        if (uncountable.contains(orig)) {
            return orig;
        }

        String irr = irregular.get(orig);
        if (irr != null) {
            return irr;
        }

        for (Rule r : plural) {
            Matcher m = r.getRegex().matcher(orig);
            //System.out.println(m.pattern().pattern());
            if (m.find()) {
                //System.out.println("!!!found match!!!");
                return m.replaceAll(r.getReplacement());
            }
        }

        return orig;
    }

    public void addPlural(String regex, String replacement) {
        plural.add(0, new Rule(regex, replacement));
    }

    public void addSingular(String regex, String replacement) {
        singular.add(0, new Rule(regex, replacement));
    }

    public void addIrregular(String orig, String replacement) {
        irregular.put(orig, replacement);
    }

    public void addUncountable(String[] words) {
        uncountable.addAll(Arrays.asList(words));
    }

    public void addUncountable(String word) {
        uncountable.add(word);
    }

    static class Rule {
        private Pattern regex;
        private String replacement;

        public Rule(String regex, String replacement) {
            this.regex = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            this.replacement = replacement;
        }

        public Pattern getRegex() {
            return regex;
        }
        public String getReplacement() {
            return replacement;
        }
    }
}
