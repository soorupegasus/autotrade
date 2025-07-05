import time
from typing import Dict, Any, List, Optional

import requests


class FyersAPIError(Exception):
    """Custom exception for FYERS API errors."""


class FyersAPI:
    """Lightweight Python wrapper for FYERS trading & market-data REST APIs.

    This class exposes convenient helper methods that map 1-to-1 with the
    official FYERS REST endpoints. For full details refer to the broker's
    documentation (login required): https://fyers.in/api/docs/

    Basic usage example
    -------------------
    >>> api = FyersAPI(
    ...     app_id="YOUR_APP_ID",
    ...     secret_key="YOUR_SECRET",
    ...     redirect_uri="https://your.app/redirect",
    ... )
    >>> print("Login here:", api.generate_auth_url())
    # ⬆️  After authorising, grab the `auth_code` from your redirect URI.
    >>> api.generate_access_token(auth_code="…")
    >>> print(api.get_profile())
    """

    # Default production base. Use ``sandbox=True`` to switch to the paper-trading cluster.
    _BASE_URL_PROD = "https://api.fyers.in"
    _BASE_URL_TEST = "https://api-t2.fyers.in"

    def __init__(
        self,
        app_id: str,
        secret_key: str,
        redirect_uri: str,
        access_token: Optional[str] = None,
        sandbox: bool = False,
        session: Optional[requests.Session] = None,
    ) -> None:
        self.app_id = app_id
        self.secret_key = secret_key
        self.redirect_uri = redirect_uri
        self.base_url = self._BASE_URL_TEST if sandbox else self._BASE_URL_PROD

        # Use a shared session so that TCP connections & headers are reused.
        self.session = session or requests.Session()
        self.session.headers.update(
            {
                "Content-Type": "application/json",
                "User-Agent": "fyers-client/1.0 (+https://fyers.in)",
            }
        )

        self.access_token = access_token
        if access_token:
            self.session.headers["Authorization"] = f"Bearer {access_token}"

    # ---------------------------------------------------------------------
    # Authentication helpers
    # ---------------------------------------------------------------------
    def generate_auth_url(self, state: str = "sample_state") -> str:
        """Return the URL to which the user should be redirected for OAuth."""
        params = {
            "client_id": self.app_id,
            "redirect_uri": self.redirect_uri,
            "response_type": "code",
            "state": state,
        }
        query = "&".join(f"{k}={v}" for k, v in params.items())
        return f"{self.base_url}/api/v2/generate-authcode?{query}"

    def generate_access_token(self, auth_code: str) -> Dict[str, Any]:
        """Exchange *auth_code* for an *access_token* and store it."""
        payload = {
            "appIdHash": self.app_id,
            "code": auth_code,
            "secretKey": self.secret_key,
        }
        data = self._request("POST", "/api/v2/validate-authcode", json=payload)

        # The response contains keys: "access_token", "code", etc.
        self.access_token = data.get("access_token") or data.get("accessToken")
        if not self.access_token:
            raise FyersAPIError("Could not retrieve access token from response")
        self.session.headers["Authorization"] = f"Bearer {self.access_token}"
        return data

    # ---------------------------------------------------------------------
    # Portfolio & profile
    # ---------------------------------------------------------------------
    def get_profile(self) -> Dict[str, Any]:
        return self._request("GET", "/api/v2/profile")

    def get_funds(self) -> Dict[str, Any]:
        return self._request("GET", "/api/v2/funds")

    def get_holdings(self) -> Dict[str, Any]:
        return self._request("GET", "/api/v2/holdings")

    def get_positions(self) -> Dict[str, Any]:
        return self._request("GET", "/api/v2/positions")

    # ---------------------------------------------------------------------
    # Orders
    # ---------------------------------------------------------------------
    def place_order(self, order_params: Dict[str, Any]) -> Dict[str, Any]:
        """Place a new order.

        Minimum expected keys in *order_params* (refer docs for full list):
        symbol • qty • type • side • productType • limitPrice • stopPrice •
        disclosedQty • validity • offlineOrder
        """
        return self._request("POST", "/api/v2/orders", json=order_params)

    def modify_order(self, order_id: str, order_params: Dict[str, Any]) -> Dict[str, Any]:
        order_params["id"] = order_id
        return self._request("PUT", "/api/v2/orders", json=order_params)

    def cancel_order(self, order_id: str) -> Dict[str, Any]:
        return self._request("DELETE", f"/api/v2/orders/{order_id}")

    def get_orders(self) -> Dict[str, Any]:
        return self._request("GET", "/api/v2/orders")

    # ---------------------------------------------------------------------
    # Market data
    # ---------------------------------------------------------------------
    def get_quotes(self, symbols: List[str]) -> Dict[str, Any]:
        symbols_csv = ",".join(symbols)
        return self._request("GET", f"/api/v6/quotes/{symbols_csv}")

    def get_depth(self, symbol: str) -> Dict[str, Any]:
        return self._request("GET", f"/api/v6/marketDepth/{symbol}")

    def get_historical_data(
        self,
        symbol: str,
        resolution: str,
        date_format: int,
        range_from: int,
        range_to: int,
        cont_flag: int = 1,
    ) -> Dict[str, Any]:
        params = {
            "symbol": symbol,
            "resolution": resolution,
            "date_format": date_format,
            "range_from": range_from,
            "range_to": range_to,
            "cont_flag": cont_flag,
        }
        return self._request("GET", "/api/v2/history", params=params)

    # ---------------------------------------------------------------------
    # Internal helpers
    # ---------------------------------------------------------------------
    def _request(self, method: str, path: str, **kwargs) -> Dict[str, Any]:
        url = f"{self.base_url}{path}"
        resp = self.session.request(method, url, timeout=15, **kwargs)
        if not resp.ok:
            raise FyersAPIError(f"HTTP {resp.status_code}: {resp.text}")

        try:
            data: Dict[str, Any] = resp.json()
        except ValueError as exc:
            raise FyersAPIError("Invalid JSON in response") from exc

        # FYERS responses typically have key "s": "ok" | "error"
        status = data.get("s") or data.get("status")
        if status not in {"ok", "success"}:
            raise FyersAPIError(f"API error: {data}")
        return data