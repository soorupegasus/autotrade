# FYERS API Java Example

This small Maven project demonstrates how to call FYERS REST API v3 from Java 11.
It contains:

* `FyersApiClient` – lightweight wrapper that implements the essential authentication logic (login link generation, exchange `auth_code` for access-token, token refresh) and a handful of sample endpoints (`/profile`, `/quotes`, `/orders`).
* `Main` – CLI example that walks you through the full login flow and prints sample API responses.

## Prerequisites

1. **A FYERS account** with API access enabled.
2. **Your application credentials**
   * **Client ID** (`app_id`)
   * **Secret Key** – *SHA-256* hash of the app secret provided by FYERS (they call it `appIdHash`).
   * **Redirect URI** – the URL you whitelisted in the FYERS developer portal.
3. **Java 11+** and **Maven 3.8+** installed locally.

## Getting started

```bash
# 1. Clone or download this repository (already in your workspace)
cd fyers-api-java   # adjust if needed

# 2. Export your credentials (or store them in any other secure place)
export FYERS_CLIENT_ID="YOUR_APP_ID"
export FYERS_SECRET_KEY="YOUR_APP_SECRET_HASH"
export FYERS_REDIRECT_URI="https://example.com/callback"

# 3. Build a self-contained JAR
mvn -q package

# 4. Run the demo
java -jar target/fyers-api-java-1.0-SNAPSHOT-jar-with-dependencies.jar
```

The program will:

1. Print a login URL – open it in your browser and finish the FYERS login & PIN steps.
2. FYERS will redirect to your redirect URI with a long `auth_code` parameter in the query string.
3. Paste that `auth_code` back into the terminal. The client exchanges it for short-lived **access-token** & long-lived **refresh-token**.
4. It then calls a couple of sample endpoints and prints the raw JSON responses.

### Placing real orders

The example contains commented-out code for placing a **live market order**. If you want to test order placement, *uncomment* the lines in `Main.java`, re-build, and run again **— be cautious, this will execute on your live account!**

## Extending the client

The official docs ( https://myapi.fyers.in/docsv3 ) list dozens of endpoints. You can add new wrapper methods to `FyersApiClient` following the existing patterns:

```java
public Map<String, Object> getPositions() {
    return getJson(BASE_URL + "/positions");
}
```

Feel free to replace the simplistic `Map<String, Object>` with proper POJOs if you need strong typing.

## License

MIT – do whatever you like, attribution appreciated.