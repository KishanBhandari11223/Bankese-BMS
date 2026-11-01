package com.bankease.mobile;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class ServerAddressActivityTest {

    @Test
    public void acceptsLanIpv4AddressAndPort() {
        assertNull(ServerAddressActivity.validateAddress("192.168.29.251:8080"));
    }

    @Test
    public void acceptsHostnameAndValidPort() {
        assertNull(ServerAddressActivity.validateAddress("bank-api.example.com:443"));
    }

    @Test
    public void rejectsMissingOrInvalidPort() {
        assertNotNull(ServerAddressActivity.validateAddress("192.168.29.251"));
        assertNotNull(ServerAddressActivity.validateAddress("192.168.29.251:0"));
        assertNotNull(ServerAddressActivity.validateAddress("192.168.29.251:65536"));
    }

    @Test
    public void rejectsMalformedAddressesAndUrlPaths() {
        assertNotNull(ServerAddressActivity.validateAddress("999.168.29.251:8080"));
        assertNotNull(ServerAddressActivity.validateAddress("http://192.168.29.251:8080"));
        assertNotNull(ServerAddressActivity.validateAddress("192.168.29.251:8080/api/v1"));
    }

    @Test
    public void explainsThatLoopbackPointsToTheAndroidDevice() {
        String message = ServerAddressActivity.validateAddress("127.0.0.1:8080");
        assertNotNull(message);
        org.junit.Assert.assertTrue(message.contains("10.0.2.2:8080"));
        org.junit.Assert.assertNotNull(
                ServerAddressActivity.validateAddress("localhost:8080"));
    }
}