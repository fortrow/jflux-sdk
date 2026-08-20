/*
 * -------------------------------------------------------------------------
 * 
 * Copyright (c) 2026 Fortrow LLC. All rights reserved.
 *  
 * NOTICE: All information contained herein is, and remains the property of 
 * Fortrow, LLC. The intellectual and technical concepts contained herein are 
 * proprietary to [Company Name] and may be covered by U.S. and Foreign Patents, 
 * patents in process, and are protected by trade secret or copyright law.
 * 
 * Dissemination of this information or reproduction of this material is 
 * strictly forbidden unless prior written permission is obtained from 
 * Fortrow, LLC.
 * 
 * -------------------------------------------------------------------------
 * 
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 * 
 * -------------------------------------------------------------------------

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
