from typing import Dict, Optional
import threading
import time
import datetime
import json
import urllib.request


class CurrencyConverter:
    _instance = None
    _lock = threading.Lock()

    def __new__(cls):
        with cls._lock:
            if cls._instance is None:
                cls._instance = super().__new__(cls)
                cls._instance._rates = {}
                cls._instance._last_update = 0
                cls._instance._updating = False
                cls._instance._rate_times = {}
            return cls._instance

    def __init__(self):
        pass

    def get_rate(self, from_currency: str, to_currency: str) -> float:
        if from_currency == to_currency:
            return 1.0

        key = (from_currency, to_currency)
        if key in self._rates:
            return self._rates[key]

        rate = self._fetch_rate(from_currency, to_currency)
        if rate is None:
            rate = 1.0
        self._rates[key] = rate
        self._rate_times[key] = datetime.datetime.now()
        return rate

    def get_rate_time(self, from_currency: str, to_currency: str):
        return self._rate_times.get((from_currency, to_currency))

    def get_all_rates(self, base_currency: str, currencies):
        rows = []
        for code in currencies:
            if code == base_currency:
                rows.append((code, 1.0, None))
                continue
            try:
                rate = self.get_rate(base_currency, code)
            except Exception:
                rate = 1.0
            t = self._rate_times.get((base_currency, code))
            rows.append((code, rate, t))
        return rows

    def _fetch_rate(self, from_currency: str, to_currency: str) -> Optional[float]:
        # Live rates from open.er-api.com (free, no API key required), same source
        # the Android app uses, so both platforms compute identical conversions.
        try:
            url = f"https://open.er-api.com/v6/latest/{from_currency}"
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=8) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            if data.get("result") == "success" and "rates" in data:
                rates = data["rates"]
                if to_currency in rates:
                    rate = float(rates[to_currency])
                    self._last_update = time.time()
                    return rate
        except Exception as e:
            print(f"Failed to fetch exchange rate: {e}")

        fallback_rates = {
            ("HKD", "CNY"): 0.92,
            ("CNY", "HKD"): 1.087,
            ("USD", "CNY"): 7.25,
            ("CNY", "USD"): 0.138,
            ("USD", "HKD"): 7.82,
            ("HKD", "USD"): 0.128,
            ("EUR", "CNY"): 7.85,
            ("CNY", "EUR"): 0.127,
            ("GBP", "CNY"): 9.15,
            ("CNY", "GBP"): 0.109,
            ("JPY", "CNY"): 0.048,
            ("CNY", "JPY"): 20.83,
        }

        key = (from_currency, to_currency)
        if key in fallback_rates:
            return fallback_rates[key]

        reverse_key = (to_currency, from_currency)
        if reverse_key in fallback_rates:
            return 1.0 / fallback_rates[reverse_key]

        return 1.0

    def convert(self, amount: float, from_currency: str, to_currency: str) -> float:
        rate = self.get_rate(from_currency, to_currency)
        return amount * rate

    def refresh_rates(self):
        self._rates.clear()
        self._rate_times.clear()
        self._last_update = 0

    def prefetch_rates(self, currencies, base_currency: str):
        """Fetch rates only for the given currencies against the base currency."""
        for cur in set(currencies):
            if cur != base_currency:
                try:
                    self.get_rate(cur, base_currency)
                except Exception:
                    pass

    def get_supported_currencies(self) -> list:
        return ["CNY", "HKD", "USD", "EUR", "GBP", "JPY", "TWD", "KRW", "SGD", "AUD", "CAD"]

    def validate_currency(self, code: str) -> bool:
        """Return True if `code` looks like a real currency.

        Checks the code against the live open.er-api.com symbol list; if the
        network is unavailable it falls back to accepting any well-formed
        3-letter alphabetic code.
        """
        code = (code or "").strip().upper()
        if len(code) != 3 or not code.isalpha():
            return False
        try:
            url = "https://open.er-api.com/v6/latest/USD"
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=8) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            rates = data.get("rates", {})
            return code in rates
        except Exception:
            return True


def get_used_currencies(db) -> list:
    """Collect all currencies actually used by accounts and debts."""
    currencies = {db.get_base_currency()}
    for acc in db.get_accounts():
        currencies.add(acc.currency)
    for debt in db.get_debts():
        currencies.add(debt.currency)
    return sorted(currencies)
