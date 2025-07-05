package com.example.fyersapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Scanner;

/**
 * Example CLI application that shows how to call FYERS API.
 * <p>
 * Before running, set the following environment variables (or replace with constants):
 * FYERS_CLIENT_ID, FYERS_SECRET_KEY, FYERS_REDIRECT_URI.
 * <p>
 * Note: The login flow requires you to open a browser once and copy the auth_code
 * from the redirected URL. This demo expects you to paste that code into the console.
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String clientId = System.getenv("FYERS_CLIENT_ID");
        String secretKey = System.getenv("FYERS_SECRET_KEY");
        String redirectUri = System.getenv("FYERS_REDIRECT_URI");

        if (clientId == null || secretKey == null || redirectUri == null) {
            log.error("Please set FYERS_CLIENT_ID, FYERS_SECRET_KEY, FYERS_REDIRECT_URI environment variables.");
            return;
        }

        FyersApiClient fyers = new FyersApiClient(clientId, secretKey, redirectUri);
        String loginUrl = fyers.buildLoginUrl("demo_state");
        log.info("1. Open the following URL in a browser and login to FYERS:\n{}", loginUrl);

        System.out.print("2. After successful login, you will be redirected to your redirect URI with a '?auth_code=' query parameter.\n   Paste the auth_code here: ");
        Scanner scanner = new Scanner(System.in);
        String authCode = scanner.nextLine().trim();

        fyers.generateAccessToken(authCode);
        log.info("Access token obtained successfully: {}", fyers.getAccessToken());

        // Fetch profile
        Map<String, Object> profile = fyers.getProfile();
        log.info("Profile API response:\n{}", profile);

        // Fetch quotes for a couple of symbols
        Map<String, Object> quotes = fyers.getQuotes("NSE:SBIN-EQ,NSE:INFY-EQ");
        log.info("Quotes API response:\n{}", quotes);

        // Fetch open & close prices for NIFTY100 in as few requests as possible
        Map<String, FyersApiClient.OpenClosePrice> oc = fyers.getOpenClosePricesForNifty100();
        log.info("NIFTY100 open/close sample (first 5):\n{}", oc.values().stream().limit(5));

        // NOTE: Placing orders will create real transactions in your account.
        // Uncomment only if you are sure.
        /*
        Map<String, Object> orderResponse = fyers.placeMarketOrder("NSE:SBIN-EQ", 1, "BUY");
        log.info("Order placed:\n{}", orderResponse);
        */
    }
}