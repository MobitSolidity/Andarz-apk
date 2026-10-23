# -*- coding: utf-8 -*-
"""Jalali (Persian/Solar Hijri) <-> Gregorian, jalaali-js algorithm (MIT), valid for Jalali years -61..3177.
The golden vectors only use 1300..1500; android.icu.util.PersianCalendar may be used on-device instead
as long as it passes the same vectors."""
BREAKS = [-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178]

def _div(a, b):
    q = abs(a) // abs(b)           # truncating division, as in the JavaScript original
    return q if (a < 0) == (b < 0) else -q
def _mod(a, b): return a - _div(a, b) * b

def jal_cal(jy, without_leap=False):
    bl = len(BREAKS); gy = jy + 621; leap_j = -14; jp = BREAKS[0]
    if jy < jp or jy >= BREAKS[bl - 1]:
        raise ValueError("Invalid Jalaali year %d" % jy)
    jump = 0
    for i in range(1, bl):
        jm = BREAKS[i]; jump = jm - jp
        if jy < jm: break
        leap_j += _div(jump, 33) * 8 + _div(_mod(jump, 33), 4)
        jp = jm
    n = jy - jp
    leap_j += _div(n, 33) * 8 + _div(_mod(n, 33) + 3, 4)
    if _mod(jump, 33) == 4 and jump - n == 4: leap_j += 1
    leap_g = _div(gy, 4) - _div((_div(gy, 100) + 1) * 3, 4) - 150
    march = 20 + leap_j - leap_g
    leap = None
    if not without_leap:
        if jump - n < 6: n = n - jump + _div(jump + 4, 33) * 33
        leap = _mod(_mod(n + 1, 33) - 1, 4)
        if leap == -1: leap = 4
    return {"leap": leap, "gy": gy, "march": march}

def g2d(gy, gm, gd):
    d = _div((gy + _div(gm - 8, 6) + 100100) * 1461, 4) + _div(153 * _mod(gm + 9, 12) + 2, 5) + gd - 34840408
    return d - _div(_div(gy + 100100 + _div(gm - 8, 6), 100) * 3, 4) + 752

def d2g(jdn):
    j = 4 * jdn + 139361631
    j = j + _div(_div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
    i = _div(_mod(j, 1461), 4) * 5 + 308
    gd = _div(_mod(i, 153), 5) + 1
    gm = _mod(_div(i, 153), 12) + 1
    gy = _div(j, 1461) - 100100 + _div(8 - gm, 6)
    return gy, gm, gd

def j2d(jy, jm, jd):
    r = jal_cal(jy, True)
    return g2d(r["gy"], 3, r["march"]) + (jm - 1) * 31 - _div(jm, 7) * (jm - 7) + jd - 1

def d2j(jdn):
    gy = d2g(jdn)[0]; jy = gy - 621
    r = jal_cal(jy); jdn1f = g2d(gy, 3, r["march"]); k = jdn - jdn1f
    if k >= 0:
        if k <= 185:
            return jy, 1 + _div(k, 31), _mod(k, 31) + 1
        k -= 186
    else:
        jy -= 1; k += 179
        if r["leap"] == 1: k += 1
    return jy, 7 + _div(k, 30), _mod(k, 30) + 1

def gregorian_to_jalali(gy, gm, gd): return d2j(g2d(gy, gm, gd))
def jalali_to_gregorian(jy, jm, jd): return d2g(j2d(jy, jm, jd))
def is_leap(jy): return jal_cal(jy)["leap"] == 0
def month_length(jy, jm): return 31 if jm <= 6 else 30 if jm <= 11 else (30 if is_leap(jy) else 29)
