# -*- coding: utf-8 -*-
"""Andarz reference engine: the executable specification of every rule and formula.

Pure Python 3, no dependencies. Every number in andarz_golden_vectors.json is produced by this file.
The PARITY part (plan, gate, fill, curve, KPIs, dashboard limits) was differentially tested against the
audited Excel workbook; the GOVERNOR part (locks, risk reductions, review sessions) has no Excel twin and
is defined here.

Conventions
  * money / ratios are IEEE doubles; ROUND(x, d) is Excel-style: half away from zero on the shortest
    decimal representation of the double (see xl_round).
  * time is timezone-aware datetime. Local days / weeks use a fixed offset (default Asia/Tehran +03:30).
    The week starts on Saturday.
  * side is "LONG" | "SHORT".
"""
from decimal import Decimal, ROUND_HALF_UP
from datetime import datetime, timedelta, timezone, date

TZ_TEHRAN = timezone(timedelta(hours=3, minutes=30))
INF = datetime(9999, 12, 31, tzinfo=timezone.utc)

# --------------------------------------------------------------------------- parameters
CORE = {
    "Start_Equity": 2000.0, "Risk_Pct": 0.01, "Risk_Max": 0.02, "Lev_Max": 10.0, "Margin_Max": 0.10,
    "Maint_Rate": 0.005, "RR_Min": 1.5, "Liq_Mult": 2.0, "Open_Risk_Max": 0.03, "Max_Positions": 3,
    "Daily_Loss_Cap": 0.03, "Weekly_Loss_Cap": 0.06, "DD_Cap": 0.10, "Max_Trades_Day": 3,
    "Withdraw_Pct": 0.30, "Compliance_Target": 0.90, "PF_Min": 1.3, "Min_Trades": 30, "Chk_N": 8,
}
GOVERNOR = {
    "Cooldown_Min": 30, "Consec_Stops": 2, "Stopout_R": -0.5, "DD_Lock_Days": 7, "Recovery_Risk": 0.005,
    "Recovery_Trades": 20, "Violation_Pause_H": 48, "Violation_Trigger": 3, "Violation_Window_D": 30,
    "Reduced_Risk": 0.005, "Reduced_Trades": 20, "BigWin_R": 3.0, "BigWin_Day_Pct": 0.03, "BigWin_Hold_H": 24,
    "Setup_Test_Risk": 0.0025, "Setup_Test_Trades": 20, "Margin_Target": 0.05, "Time_Stop_Candles": 10,
    "Review_After_Loss_H": 2, "Loosen_Delay_H": 24, "Watchlist_Max": 10, "Report_Min_Chars": 20,
    "Partial_Exit_Pct": 0.5, "Partial_R_Lo": 1.0, "Partial_R_Hi": 1.5, "Trail_Lock_R": 3.0,
}
DEFAULTS = {**CORE, **GOVERNOR}

# "tighter" direction per parameter: moving the value this way makes the rules stricter.
TIGHTER = {
    "Start_Equity": "neutral", "Risk_Pct": "lower", "Risk_Max": "lower", "Lev_Max": "lower", "Margin_Max": "lower",
    "Maint_Rate": "higher", "RR_Min": "higher", "Liq_Mult": "higher", "Open_Risk_Max": "lower", "Max_Positions": "lower",
    "Daily_Loss_Cap": "lower", "Weekly_Loss_Cap": "lower", "DD_Cap": "lower", "Max_Trades_Day": "lower",
    "Withdraw_Pct": "neutral", "Compliance_Target": "higher", "PF_Min": "higher", "Min_Trades": "higher",
    "Chk_N": "neutral", "Cooldown_Min": "higher", "Consec_Stops": "lower", "Stopout_R": "higher",
    "DD_Lock_Days": "higher", "Recovery_Risk": "lower", "Recovery_Trades": "higher", "Violation_Pause_H": "higher",
    "Violation_Trigger": "lower", "Violation_Window_D": "higher", "Reduced_Risk": "lower", "Reduced_Trades": "higher",
    "BigWin_R": "lower", "BigWin_Day_Pct": "lower", "BigWin_Hold_H": "higher", "Setup_Test_Risk": "lower",
    "Setup_Test_Trades": "higher", "Margin_Target": "lower", "Time_Stop_Candles": "lower", "Review_After_Loss_H": "higher",
    "Loosen_Delay_H": "higher", "Watchlist_Max": "lower", "Report_Min_Chars": "higher", "Partial_Exit_Pct": "neutral",
    "Partial_R_Lo": "neutral", "Partial_R_Hi": "neutral", "Trail_Lock_R": "neutral",
}


def P(**over):
    p = dict(DEFAULTS)
    p.update(over)
    return p


# --------------------------------------------------------------------------- rounding
def xl_round(x, d=0):
    """Excel ROUND: round half away from zero on the shortest repr of the double."""
    if x is None:
        return None
    q = Decimal(repr(float(x))).quantize(Decimal(1).scaleb(-d), rounding=ROUND_HALF_UP)
    return float(q)


# --------------------------------------------------------------------------- plan / gate / size / fill
def plan(side, equity, entry, sl, tp, lev, p, risk_pct=None):
    """Planning numbers of one trade (journal columns: sld rr riskd notmax marg margp liq liqd liqx)."""
    rp = p["Risk_Pct"] if risk_pct is None else risk_pct
    o = {}
    o["sld"] = abs(entry - sl) / entry if entry else None
    o["rr"] = abs(tp - entry) / abs(entry - sl) if entry != sl else None
    o["riskUsd"] = equity * rp
    o["notionalMax"] = o["riskUsd"] / o["sld"] if o["sld"] else None
    o["margin"] = o["notionalMax"] / lev if (o["notionalMax"] is not None and lev) else None
    o["marginPct"] = o["margin"] / equity if (o["margin"] is not None and equity) else None
    m = p["Maint_Rate"]
    if lev:
        o["liq"] = entry * (1 - 1 / lev + m) if side == "LONG" else entry * (1 + 1 / lev - m)
    else:
        o["liq"] = None
    o["liqDist"] = abs(entry - o["liq"]) / entry if (o["liq"] is not None and entry) else None
    o["liqX"] = o["liqDist"] / o["sld"] if (o["liqDist"] is not None and o["sld"]) else None
    return o


REASON_ORDER = ["DIRECTION", "CHECKLIST", "LEVERAGE", "RR", "MARGIN", "LIQUIDATION"]


def gate(side, entry, sl, tp, lev, chk, calc, p):
    """Ordered list of rejection reason codes ([] = allowed). Ratios are compared after ROUND(x, 4)."""
    r = []
    ok_dir = (sl < entry and tp > entry) if side == "LONG" else (sl > entry and tp < entry)
    if not ok_dir:
        r.append("DIRECTION")
    if chk < p["Chk_N"]:
        r.append("CHECKLIST")
    if lev > p["Lev_Max"]:
        r.append("LEVERAGE")
    if calc["rr"] is not None and xl_round(calc["rr"], 4) < p["RR_Min"]:
        r.append("RR")
    if calc["marginPct"] is not None and xl_round(calc["marginPct"], 4) > p["Margin_Max"]:
        r.append("MARGIN")
    if calc["liqX"] is not None and xl_round(calc["liqX"], 4) < p["Liq_Mult"]:
        r.append("LIQUIDATION")
    return r


def size_verdict(rriskp, p, risk_cap=None, grade_a_allowed=True):
    """YES | GRADE_A_ONLY | OVER_CAP | RED_LINE. With defaults it is exactly the workbook's verdict."""
    cap = p["Risk_Pct"] if risk_cap is None else risk_cap
    r5 = xl_round(rriskp, 5)
    if r5 <= cap * 1.05:
        return "YES"
    if r5 <= p["Risk_Max"]:
        return "GRADE_A_ONLY" if grade_a_allowed else "OVER_CAP"
    return "RED_LINE"


def fill(side, entry, sl, equity, notional, legs, fee):
    """Actual risk and result. legs = [{"price":..,"fraction":..}], fractions sum to 1 (partial exits)."""
    assert abs(sum(l["fraction"] for l in legs) - 1.0) < 1e-9
    sld = abs(entry - sl) / entry
    rrisk = notional * sld
    o = {"rrisk": rrisk, "rriskp": rrisk / equity if equity else None}
    if legs:
        sign = 1 if side == "LONG" else -1
        gross = sum(sign * (l["price"] - entry) / entry * notional * l["fraction"] for l in legs)
        net = gross - fee
        o.update(gross=gross, net=net, R=(net / rrisk if rrisk else None),
                 result="WIN" if net > 0 else "LOSS" if net < 0 else "EVEN", equityAfter=equity + net)
    return o


def compliant(violations, gate_allowed, verdict):
    return (not violations) and gate_allowed and verdict in ("YES", "GRADE_A_ONLY")


def leverage_advisor(side, equity, entry, sl, tp, chk, p, risk_pct=None):
    """Integer leverages whose margin and liquidation checks both pass, found with the gate itself."""
    sld = abs(entry - sl) / entry
    rp = p["Risk_Pct"] if risk_pct is None else risk_pct
    feas = []
    for L in range(1, int(p["Lev_Max"]) + 1):
        c = plan(side, equity, entry, sl, tp, L, p, rp)
        reasons = gate(side, entry, sl, tp, L, chk, c, p)
        if "MARGIN" not in reasons and "LIQUIDATION" not in reasons:
            feas.append((L, c["marginPct"]))
    out = {"lMinReal": (rp / sld) / p["Margin_Max"], "lMaxLiqReal": 1 / (p["Liq_Mult"] * sld + p["Maint_Rate"]),
           "feasibleMin": feas[0][0] if feas else None, "feasibleMax": feas[-1][0] if feas else None}
    if feas:
        best = min(feas, key=lambda t: (abs(t[1] - p["Margin_Target"]), t[0]))
        out["recommended"] = best[0]
    else:
        out["recommended"] = None
    return out


# --------------------------------------------------------------------------- equity curve, KPIs, dashboard
def curve(nets, start):
    """P&L-only curve in exit order -> [{"cum","peak","dd"}]. Deposits/withdrawals never enter."""
    out, cum, peak = [], start, start
    run = 0.0
    for n in nets:
        run += n
        cum = start + run
        peak = max(peak, cum)
        out.append({"cum": cum, "peak": peak, "dd": (peak - cum) / peak if peak else 0.0})
    return out


def kpis(closed, ps, pe, p):
    """closed: [{"exitDate":date,"net","R","ok":bool,"chk":int,"dd":float}], period inclusive."""
    rows = [t for t in closed if ps <= t["exitDate"] <= pe]
    n = len(rows)
    o = {"n": n}
    rs = [t["R"] for t in rows if t["R"] is not None]
    wins = [r for r in rs if r > 0]
    loss = [r for r in rs if r < 0]
    wsum = sum(t["net"] for t in rows if t["net"] > 0)
    lsum = sum(t["net"] for t in rows if t["net"] < 0)
    o["compliance"] = sum(1 for t in rows if t["ok"]) / n if n else None
    o["expectancy"] = sum(rs) / n if n else None
    o["avgWinR"] = sum(wins) / len(wins) if wins else None
    o["avgLossR"] = sum(loss) / len(loss) if loss else None
    o["realRR"] = o["avgWinR"] / abs(o["avgLossR"]) if (wins and loss) else None
    o["winRate"] = sum(1 for t in rows if t["net"] > 0) / n if n else None
    o["profitFactor"] = wsum / abs(lsum) if (n and lsum != 0) else None
    o["maxDD"] = max([t["dd"] for t in rows]) if n else None
    o["avgChk"] = sum(t["chk"] for t in rows) / n if n else None
    o["net"] = sum(t["net"] for t in rows)
    o["sumR"] = sum(rs)
    o["withdrawSuggest"] = max(0.0, o["net"]) * p["Withdraw_Pct"]
    st = {}
    st["n"] = "ENOUGH_DATA" if n >= p["Min_Trades"] else "LOW_DATA"
    if o["compliance"] is not None:
        st["compliance"] = "ON_TARGET" if o["compliance"] >= p["Compliance_Target"] else "BELOW_TARGET"
    if o["expectancy"] is not None:
        st["expectancy"] = "POSITIVE" if o["expectancy"] > 0 else "NON_POSITIVE"
    if o["realRR"] is not None:
        st["realRR"] = "ON_TARGET" if o["realRR"] >= p["RR_Min"] else "BELOW_TARGET"
    if o["profitFactor"] is not None:
        st["profitFactor"] = "ON_TARGET" if o["profitFactor"] >= p["PF_Min"] else "BELOW_TARGET"
    if o["maxDD"] is not None:
        st["maxDD"] = "IN_RANGE" if o["maxDD"] < p["DD_Cap"] else "OVER_CAP"
    if o["avgChk"] is not None:
        st["avgChk"] = "COMPLETE" if o["avgChk"] >= p["Chk_N"] else "INCOMPLETE"
    o["status"] = st
    return o


def dashboard(trades_today, dnet, wnet, equity_now, equity_sod, equity_sow, dd_now, open_count, open_risk_usd, p):
    """The six limits of the Today screen + the overall verdict (workbook parity)."""
    dp = dnet / equity_sod if equity_sod else 0.0
    wp = wnet / equity_sow if equity_sow else 0.0
    orisk = open_risk_usd / equity_now if equity_now else 0.0
    s = {}
    s["trades"] = "TRADES_CAP" if trades_today >= p["Max_Trades_Day"] else "OK"
    s["daily"] = "DAILY_RED" if xl_round(-dp, 6) >= p["Daily_Loss_Cap"] else "OK"
    s["weekly"] = "WEEKLY_RED" if xl_round(-wp, 6) >= p["Weekly_Loss_Cap"] else "OK"
    s["drawdown"] = "DD_RED" if xl_round(dd_now, 6) >= p["DD_Cap"] else "OK"
    s["positions"] = ("POSITIONS_OVER" if open_count > p["Max_Positions"]
                      else "POSITIONS_FULL" if open_count == p["Max_Positions"] else "OK")
    s["openRisk"] = "RISK_FULL" if orisk + p["Risk_Pct"] > p["Open_Risk_Max"] + 0.000001 else "OK"
    return {"tradesToday": trades_today, "dailyPct": dp, "weeklyPct": wp, "drawdown": dd_now,
            "openPositions": open_count, "openRiskPct": orisk, "status": s,
            "allowed": all(v == "OK" for v in s.values())}


def tally(rows, key):
    out = {}
    for t in rows:
        for v in t.get(key, []) or []:
            out[v] = out.get(v, 0) + 1
    return out


# --------------------------------------------------------------------------- time helpers
def ts(s):
    return datetime.fromisoformat(s)


def iso(dt):
    if dt is None:
        return None
    if dt >= INF:
        return None
    return dt.astimezone(TZ_TEHRAN).isoformat()


def local_date(dt, tz=TZ_TEHRAN):
    return dt.astimezone(tz).date()


def day_start(d, tz=TZ_TEHRAN):
    return datetime(d.year, d.month, d.day, tzinfo=tz)


def week_start_of(d):
    """Saturday on/before d (the trading week starts on Saturday)."""
    return d - timedelta(days=(d.weekday() - 5) % 7)


# --------------------------------------------------------------------------- ledger / equity
def equity_before(t, ledger, trades):
    """Account equity just before instant t: ledger flows (< t) + net P&L of trades closed (< t)."""
    tot = 0.0
    for e in ledger:
        if ts(e["time"]) < t:
            tot += e["amount"] if e["type"] in ("BASELINE", "DEPOSIT", "ADJUSTMENT") else -e["amount"] if e["type"] == "WITHDRAWAL" else 0.0
    for tr in trades:
        if tr.get("exitTime") and ts(tr["exitTime"]) < t and tr.get("account", "REAL") == "REAL":
            tot += tr["net"]
    return tot


def equity_now(now, ledger, trades):
    return equity_before(now + timedelta(microseconds=1), ledger, trades)


# --------------------------------------------------------------------------- governor
STOP_REASON = "STOP_LOSS"
LOCK_PRIORITY = ["VIOLATION", "DRAWDOWN", "WEEKLY_LOSS", "DAILY_LOSS", "DAY_OVER", "TRADE_CAP", "COOLDOWN"]


def is_stop_out(t, p):
    if not t.get("exitTime"):
        return False
    if t.get("exitReason") == STOP_REASON:
        return True
    return t["net"] < 0 and t.get("R") is not None and t["R"] <= p["Stopout_R"]


def _real(trades):
    return [t for t in trades if t.get("account", "REAL") == "REAL"]


def _closed(trades, now):
    c = [t for t in _real(trades) if t.get("exitTime") and ts(t["exitTime"]) <= now]
    return sorted(c, key=lambda t: (ts(t["exitTime"]), ts(t["entryTime"]), t["id"]))


def _entered(trades, now):
    return [t for t in _real(trades) if ts(t["entryTime"]) <= now]


def _qualifying_reviews(reviews, kinds, after):
    xs = [ts(r["completedAt"]) for r in reviews if r["type"] in kinds and ts(r["completedAt"]) >= after]
    return sorted(xs)


def drawdown_state(closed, start, p, reviews, now):
    """Walk the P&L curve; a breach locks trading, a release (>= DD_Lock_Days AND a saved review) re-bases the peak."""
    cum, peak = start, start
    locked, breach_at = False, None
    releases = []
    i = 0
    state = {"lock": None, "recoveryStarts": [], "ddNow": 0.0, "peak": start, "cum": start}

    def try_release(limit):
        nonlocal locked, breach_at, peak
        if not locked:
            return False
        revs = _qualifying_reviews(reviews, ("WEEKLY", "MONTHLY", "DRAWDOWN"), breach_at)
        rel = max(breach_at + timedelta(days=p["DD_Lock_Days"]), revs[0]) if revs else None
        if rel is not None and rel <= limit:
            peak = cum
            releases.append(rel)
            locked, breach_at = False, None
            return True
        return False

    while True:
        nxt = ts(closed[i]["exitTime"]) if i < len(closed) else None
        limit = nxt if nxt is not None else now
        while try_release(limit):
            pass
        if nxt is None:
            break
        t = closed[i]
        cum += t["net"]
        peak = max(peak, cum)
        dd = (peak - cum) / peak if peak else 0.0
        if not locked and xl_round(dd, 6) >= p["DD_Cap"]:
            locked, breach_at = True, nxt
        i += 1
    dd_now = (peak - cum) / peak if peak else 0.0
    state.update(ddNow=dd_now, peak=peak, cum=cum, recoveryStarts=releases)
    if locked:
        revs = _qualifying_reviews(reviews, ("WEEKLY", "MONTHLY", "DRAWDOWN"), breach_at)
        state["lock"] = {"code": "DRAWDOWN", "since": breach_at, "until": breach_at + timedelta(days=p["DD_Lock_Days"]),
                         "needs": [] if revs else ["REVIEW"]}
    return state


def violation_events(trades, now, p):
    """[{start,tradeId,open,reportOk}] where start = max(violationAt, exit time)."""
    out = []
    for t in _real(trades):
        v = t.get("violations") or []
        if not v or not t.get("violationAt"):
            continue
        va = ts(t["violationAt"])
        if va > now:
            continue
        ex = ts(t["exitTime"]) if t.get("exitTime") and ts(t["exitTime"]) <= now else None
        start = max(va, ex) if ex else va
        out.append({"id": t["id"], "start": start, "open": ex is None,
                    "reportOk": len((t.get("violationReport") or "").strip()) >= p["Report_Min_Chars"]})
    return sorted(out, key=lambda e: (e["start"], e["id"]))


def violation_triggers(events, p):
    """Rolling window: when Violation_Trigger violations fall in Violation_Window_D days, fire and consume them."""
    window, fired = [], []
    for e in events:
        window = [w for w in window if e["start"] - w <= timedelta(days=p["Violation_Window_D"])]
        window.append(e["start"])
        if len(window) >= p["Violation_Trigger"]:
            fired.append(e["start"])
            window = []
    return fired


def big_win_hold(closed, ledger, trades, p, now):
    """Latest end of a 24h hold started by a trade with R > BigWin_R or a day gain > BigWin_Day_Pct."""
    hold = None
    day_net = {}
    for t in closed:
        te = ts(t["exitTime"])
        starts = []
        if t.get("R") is not None and t["R"] > p["BigWin_R"]:
            starts.append(te)
        d = local_date(te)
        day_net[d] = day_net.get(d, 0.0) + t["net"]
        sod = equity_before(day_start(d), ledger, trades)
        if sod and xl_round(day_net[d] / sod, 6) > p["BigWin_Day_Pct"]:
            starts.append(te)
        for s in starts:
            end = s + timedelta(hours=p["BigWin_Hold_H"])
            hold = end if hold is None or end > hold else hold
    return hold if hold and hold > now else None


def governor(now, trades, ledger, reviews, setups, p, setup_id=None):
    """Everything the wizard needs before it lets a new trade be planned or entered."""
    closed = _closed(trades, now)
    entered = _entered(trades, now)
    today = local_date(now)
    wk = week_start_of(today)
    locks = []

    # 1) violations: pause (start + Violation_Pause_H), written report, and "close it now" while still open
    for e in violation_events(trades, now, p):
        until = e["start"] + timedelta(hours=p["Violation_Pause_H"])
        needs = [] if e["reportOk"] else ["REPORT"]
        if e["open"]:
            locks.append({"code": "VIOLATION", "until": INF, "needs": needs + ["CLOSE_POSITION"], "tradeId": e["id"]})
        elif now < until or needs:
            locks.append({"code": "VIOLATION", "until": until, "needs": needs, "tradeId": e["id"]})

    # 2) drawdown cap
    ddst = drawdown_state(closed, p["Start_Equity"], p, reviews, now)
    if ddst["lock"]:
        locks.append({k: v for k, v in ddst["lock"].items() if k != "since"})

    # 3) weekly loss cap: scan every week that has a breach; lock until next week AND a weekly review of that week
    wk_net, wk_seen = {}, {}
    for t in closed:
        te = ts(t["exitTime"])
        w = week_start_of(local_date(te))
        wk_net[w] = wk_net.get(w, 0.0) + t["net"]
        sow = equity_before(day_start(w), ledger, trades)
        if sow and w not in wk_seen and xl_round(-wk_net[w] / sow, 6) >= p["Weekly_Loss_Cap"]:
            wk_seen[w] = te
    for w, tb in sorted(wk_seen.items()):
        nxt_w = day_start(w + timedelta(days=7))
        has_rev = any(r["type"] == "WEEKLY" and r.get("weekStart") == w.isoformat() for r in reviews)
        if now < nxt_w or not has_rev:
            locks.append({"code": "WEEKLY_LOSS", "until": nxt_w, "needs": [] if has_rev else ["WEEKLY_REVIEW"]})

    # 4) daily loss cap (today only)
    dnet = sum(t["net"] for t in closed if local_date(ts(t["exitTime"])) == today)
    sod = equity_before(day_start(today), ledger, trades)
    day_run = 0.0
    breach_today = False
    for t in closed:
        if local_date(ts(t["exitTime"])) == today:
            day_run += t["net"]
            if sod and xl_round(-day_run / sod, 6) >= p["Daily_Loss_Cap"]:
                breach_today = True
    if breach_today:
        locks.append({"code": "DAILY_LOSS", "until": day_start(today + timedelta(days=1)), "needs": []})

    # 5) two stop-outs in a row today -> day over
    todays = [t for t in closed if local_date(ts(t["exitTime"])) == today]
    run = 0
    for t in todays:
        run = run + 1 if is_stop_out(t, p) else 0
        if run >= p["Consec_Stops"]:
            locks.append({"code": "DAY_OVER", "until": day_start(today + timedelta(days=1)), "needs": []})
            break

    # 6) trades per day (entries by local entry date)
    n_today = sum(1 for t in entered if local_date(ts(t["entryTime"])) == today)
    if n_today >= p["Max_Trades_Day"]:
        locks.append({"code": "TRADE_CAP", "until": day_start(today + timedelta(days=1)), "needs": []})

    # 7) cool-down after the latest stop-out
    stops = [ts(t["exitTime"]) for t in closed if is_stop_out(t, p)]
    if stops:
        until = max(stops) + timedelta(minutes=p["Cooldown_Min"])
        if now < until:
            locks.append({"code": "COOLDOWN", "until": until, "needs": []})

    locks.sort(key=lambda l: (LOCK_PRIORITY.index(l["code"]), l["until"]))

    # risk reductions: count entered real trades after each trigger
    reductions = []

    def add_reduction(code, start, risk, n):
        used = sum(1 for t in entered if ts(t["entryTime"]) >= start)
        if used < n:
            reductions.append({"code": code, "start": start, "riskPct": risk, "remaining": n - used})

    for s in ddst["recoveryStarts"]:
        add_reduction("DD_RECOVERY", s, p["Recovery_Risk"], p["Recovery_Trades"])
    for s in violation_triggers(violation_events(trades, now, p), p):
        add_reduction("VIOLATIONS", s, p["Reduced_Risk"], p["Reduced_Trades"])
    if setup_id is not None:
        used = sum(1 for t in entered if t.get("setupId") == setup_id)
        if used < p["Setup_Test_Trades"]:
            reductions.append({"code": "SETUP_TEST", "start": None, "riskPct": p["Setup_Test_Risk"],
                               "remaining": p["Setup_Test_Trades"] - used})
    risk_cap = min([p["Risk_Pct"]] + [r["riskPct"] for r in reductions])
    hold = big_win_hold(closed, ledger, trades, p, now)
    grade = next((s["grade"] for s in setups if s["id"] == setup_id), None) if setup_id else None
    grade_a_allowed = grade == "A" and not reductions and hold is None
    open_trades = [t for t in _real(entered) if not (t.get("exitTime") and ts(t["exitTime"]) <= now)]
    return {
        "canEnter": not locks, "locks": locks, "riskCap": risk_cap, "reductions": reductions,
        "gradeAAllowed": grade_a_allowed, "bigWinHoldUntil": hold,
        "tradesToday": n_today, "openPositions": len(open_trades),
        "ddNow": ddst["ddNow"], "dailyNet": dnet,
    }


def lock_json(g):
    return {
        "canEnter": g["canEnter"],
        "locks": [{"code": l["code"], "until": iso(l["until"]), "needs": sorted(l["needs"])} for l in g["locks"]],
        "riskCap": g["riskCap"],
        "reductions": sorted([{"code": r["code"], "riskPct": r["riskPct"], "remaining": r["remaining"]} for r in g["reductions"]],
                             key=lambda r: r["code"]),
        "gradeAAllowed": g["gradeAAllowed"], "bigWinHoldUntil": iso(g["bigWinHoldUntil"]),
        "tradesToday": g["tradesToday"], "openPositions": g["openPositions"],
    }


# --------------------------------------------------------------------------- review sessions & parameter changes
def can_start_session(now, trades, p):
    if any(not t.get("exitTime") and ts(t["entryTime"]) <= now for t in _real(trades)):
        return {"allowed": False, "reason": "OPEN_POSITION", "until": None}
    losses = [ts(t["exitTime"]) for t in _real(trades) if t.get("exitTime") and ts(t["exitTime"]) <= now and t["net"] < 0]
    if losses:
        until = max(losses) + timedelta(hours=p["Review_After_Loss_H"])
        if now < until:
            return {"allowed": False, "reason": "RECENT_LOSS", "until": iso(until)}
    return {"allowed": True, "reason": None, "until": None}


def classify_change(key, old, new):
    if new == old:
        return "NONE"
    d = TIGHTER[key]
    if d == "neutral":
        return "NEUTRAL"
    return "TIGHTEN" if ((new < old) == (d == "lower")) else "LOOSEN"


def change_effective_at(key, old, new, applied_at, p):
    kind = classify_change(key, old, new)
    return applied_at + (timedelta(hours=p["Loosen_Delay_H"]) if kind == "LOOSEN" else timedelta(0)), kind


def params_at(base, changes, t):
    """Value of every parameter at instant t. changes: [{key,value,appliedAt,effectiveAt}]."""
    p = dict(base)
    for c in sorted(changes, key=lambda c: (ts(c["effectiveAt"]), ts(c["appliedAt"]))):
        if ts(c["effectiveAt"]) <= t:
            p[c["key"]] = c["value"]
    return p


def grade_a_eligible(setup_closed, p):
    n = len(setup_closed)
    if n < p["Min_Trades"]:
        return {"eligible": False, "n": n, "expectancy": None}
    e = sum(t["R"] for t in setup_closed if t.get("R") is not None) / n
    return {"eligible": e > 0, "n": n, "expectancy": e}


# --------------------------------------------------------------------------- number formatting & input
PD = "۰۱۲۳۴۵۶۷۸۹"
AD = "٠١٢٣٤٥٦٧٨٩"
EN = "0123456789"
FA_DEC, FA_GRP, MINUS = "٫", "٬", "−"


def to_fa_digits(s):
    return s.translate(str.maketrans(EN, PD))


def format_number(x, decimals=2, fa=True, group=True, trim=False):
    """Money/number text. Rounds half away from zero (xl_round). Negative uses U+2212."""
    r = xl_round(x, decimals)
    neg = r < 0
    s = f"{abs(r):.{decimals}f}"
    ip, _, fp = s.partition(".")
    if group:
        ip = f"{int(ip):,}"
    if trim:
        fp = fp.rstrip("0")
    out = ip + ("." + fp if fp else "")
    if fa:
        out = to_fa_digits(out).replace(",", FA_GRP).replace(".", FA_DEC)
    return (MINUS if neg and float(s) != 0 else "") + out


def format_signed(x, decimals=2, fa=True):
    r = xl_round(x, decimals)
    s = format_number(abs(r), decimals, fa, group=True)
    return ("+" if r > 0 else MINUS if r < 0 else "") + s


def format_percent(x, decimals=1, fa=True, signed=False):
    r = xl_round(x * 100, decimals)
    core = format_signed(r, decimals, fa) if signed else format_number(r, decimals, fa, group=False)
    return core + ("٪" if fa else "%")


def normalize_input(text):
    """User text -> float or None. Accepts Persian/Arabic-Indic digits, ٫ ٬ , . and U+2212."""
    if text is None:
        return None
    s = text.strip()
    if not s:
        return None
    s = s.translate(str.maketrans(PD, EN)).translate(str.maketrans(AD, EN))
    s = s.replace(MINUS, "-").replace("\u200c", "").replace(" ", "")
    s = s.replace("٬", "").replace("٫", ".")
    if "," in s and "." in s:
        s = s.replace(",", "")
    elif "," in s:
        head, _, tail = s.rpartition(",")
        s = head.replace(",", "") + ("." + tail if len(tail) != 3 or head == "" else tail)
    import re
    if not re.fullmatch(r"[+-]?(\d+\.?\d*|\.\d+)", s):
        return None
    return float(s)


# --------------------------------------------------------------------------- live constitution text
WORDS = {1: "یک", 2: "دو", 3: "سه", 4: "چهار", 5: "پنج", 6: "شش", 7: "هفت", 8: "هشت", 9: "نه", 10: "ده"}


def fmt_ref(v, fmt):
    if fmt == "pct":
        return format_number(xl_round(v * 100, 2), 2, True, group=False, trim=True) + "٪"
    if fmt == "int":
        return to_fa_digits(str(int(round(v))))
    if fmt == "dec":
        return format_number(v, 2, True, group=False, trim=True)
    if fmt == "word":
        return WORDS[int(v)] if float(v).is_integer() and int(v) in WORDS else format_number(v, 2, True, group=False, trim=True)
    raise ValueError(fmt)


def render_clause(text, refs, p):
    derived = {"@SL_Floor": p["Risk_Pct"] / (p["Margin_Max"] * p["Lev_Max"])}
    for r in refs:
        v = derived[r["param"]] if r["param"].startswith("@") else p[r["param"]]
        assert r["literal"] in text, (r["literal"], text)
        text = text.replace(r["literal"], r["template"].replace("{v}", fmt_ref(v, r["fmt"])), 1)
    return text


# --------------------------------------------------------------------------- trade pipeline (what the app calls)
def resolve_trade(t, p):
    """Raw trade -> derived fields (rrisk, rriskp, verdict and, when closed, net / R / ok)."""
    o = dict(t)
    ctx = t.get("ctx") or {}
    sld = abs(t["entry"] - t["sl"]) / t["entry"]
    o["rrisk"] = t["notional"] * sld
    o["rriskp"] = o["rrisk"] / t["eq"]
    o["verdict"] = size_verdict(o["rriskp"], p, ctx.get("riskCap"), ctx.get("gradeA", True))
    if t.get("exitTime"):
        f = fill(t["side"], t["entry"], t["sl"], t["eq"], t["notional"], t["legs"], t.get("fee", 0.0))
        o.update(net=f["net"], R=f["R"], gross=f["gross"], result=f["result"])
        o["ok"] = compliant(t.get("violations") or [], t["gateAllowed"], o["verdict"])
    return o


def snapshot(now, p, ledger, trades_raw, reviews, setups, setup_id=None):
    """Equity, the six dashboard limits and the governor state at instant `now`."""
    trades = [resolve_trade(t, p) for t in trades_raw]
    closed = _closed(trades, now)
    today = local_date(now)
    wk = week_start_of(today)
    eq_now = equity_now(now, ledger, trades)
    sod = equity_before(day_start(today), ledger, trades)
    sow = equity_before(day_start(wk), ledger, trades)
    dnet = sum(t["net"] for t in closed if local_date(ts(t["exitTime"])) == today)
    wnet = sum(t["net"] for t in closed if wk <= local_date(ts(t["exitTime"])) <= today)
    g = governor(now, trades, ledger, reviews, setups, p, setup_id)
    open_real = [t for t in _real(_entered(trades, now)) if not (t.get("exitTime") and ts(t["exitTime"]) <= now)]
    d = dashboard(g["tradesToday"], dnet, wnet, eq_now, sod, sow, g["ddNow"], len(open_real),
                  sum(t["rrisk"] for t in open_real), p)
    return {"equity": {"now": eq_now, "startOfDay": sod, "startOfWeek": sow}, "dashboard": d, "governor": lock_json(g)}


def kpi_from_trades(trades_raw, ps, pe, p):
    trades = [resolve_trade(t, p) for t in trades_raw]
    closed = sorted([t for t in _real(trades) if t.get("exitTime")],
                    key=lambda t: (ts(t["exitTime"]), ts(t["entryTime"]), t["id"]))
    cv = curve([t["net"] for t in closed], p["Start_Equity"])
    rows = [{"id": t["id"], "exitDate": local_date(ts(t["exitTime"])), "net": t["net"], "R": t["R"], "ok": t["ok"],
             "chk": t["chk"], "dd": cv[i]["dd"], "cum": cv[i]["cum"], "peak": cv[i]["peak"],
             "mistake": [t["mistake"]] if t.get("mistake") else [], "violations": t.get("violations") or []}
            for i, t in enumerate(closed)]
    k = kpis(rows, ps, pe, p)
    inp = [r for r in rows if ps <= r["exitDate"] <= pe]
    k["mistakeTally"] = tally(inp, "mistake")
    k["violationTally"] = tally(inp, "violations")
    return k, rows
