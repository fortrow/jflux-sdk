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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 17:38:20 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.server.containers;

import com.fortrow.jflux.todash.__;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class IpAddress {

    public enum Version {
        IPV4,
        IPV6
    }

    private String address;
    private Version version;

    public IpAddress(String ip) throws ParseException {

        boolean ipv4 = isValidIPv4(ip);
        boolean ipv6 = isValidIPv6(ip);

        if(ipv4 || ipv6) {
            if(ipv4) version = Version.IPV4;
            else if(ipv6) version = Version.IPV6;
            this.address = ip;
        } else {
            throw new ParseException("Invalid ip address", 0);
        }

    }

    public String getAddress() {
        return address;
    }

    public Version getVersion() {
        return version;
    }

    private static Optional<int[]> parseIPv4(String ip) {

        if(!__.isset(ip) || ip.isBlank()) return Optional.empty();

        String[] parts = ip.split("\\.", -1);
        if(parts.length != 4) return Optional.empty();

        int[] parsed = new int[4];
        for(int i=0; i<4; i++) {
            String part = parts[i];
            if(part.isEmpty() || part.length() > 3) return Optional.empty();
            if(part.length() > 1 && part.charAt(0) == '0') return Optional.empty();

            int value = 0;
            for(char c : part.toCharArray()) {
                if(!Character.isDigit(c)) return Optional.empty();
                value = value * 10 + (c - '0');
            }
            if(value > 255) return Optional.empty();
            parsed[i] = value;
        }

        return Optional.of(parsed);

    }

    private static Optional<int[]> parseIPv6(String ip) {

        if(!__.isset(ip) || ip.isBlank()) return Optional.empty();
        if(ip.contains("%")) return Optional.empty();
        if(ip.indexOf("::") != ip.lastIndexOf("::")) return Optional.empty();

        String[] halves = ip.split("::", -1);
        List<Integer> groups = new ArrayList<>();

        if(halves.length == 1) {
            if(!addGroups(halves[0], groups)) return Optional.empty();
            if(groups.size() != 8) return Optional.empty();
        } else if(halves.length == 2) {
            List<Integer> left = new ArrayList<>();
            List<Integer> right = new ArrayList<>();

            if(!halves[0].isEmpty() && !addGroups(halves[0], left)) return Optional.empty();
            if(!halves[1].isEmpty() && !addGroups(halves[1], right)) return Optional.empty();

            int missingGroups = 8 - left.size() - right.size();
            if(missingGroups < 1) return Optional.empty();

            groups.addAll(left);
            for(int i=0; i<missingGroups; i++) groups.add(0);
            groups.addAll(right);
        } else return Optional.empty();

        int[] parsed = new int[8];
        for(int i=0; i<8; i++) parsed[i] = groups.get(i);
        return Optional.of(parsed);

    }

    private static boolean addGroups(String section, List<Integer> groups) {

        String[] parts = section.split(":", -1);

        for(String part : parts) {
            if(part.isEmpty() || part.length() > 4) return false;
            int value = 0;
            for(char c : part.toCharArray()) {
                int digit = Character.digit(c, 16);
                if(digit == -1) return false;
                value = value * 16 + digit;
            }
            groups.add(value);
        }

        return true;

    }

    private static boolean isValidIPv4(String ip) {
        return parseIPv4(ip).isPresent();
    }

    private static boolean isValidIPv6(String ip) {
        return parseIPv6(ip).isPresent();
    }

}
