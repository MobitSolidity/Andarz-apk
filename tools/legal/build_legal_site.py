#!/usr/bin/env python3
"""Builds the hosted terms / privacy / risk page (D-96) from the in-app legal blocks.

    python3 tools/legal/build_legal_site.py

Inputs : app/src/main/assets/content_app_fa.json, content_app_en.json (the "legal" block)
Output : docs/index.html (served by GitHub Pages from main:/docs at https://mobitsolidity.github.io/Andarz-apk/)

The page is generated, never edited by hand, so the store link and the in-app text can never drift apart;
LegalContentTest fails when docs/index.html is older than the assets. No script, no font or tracker is loaded.
"""
import html, json, pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
A = ROOT / 'app/src/main/assets'


def load(name):
    return json.loads((A / name).read_text('utf-8'))['legal']


def block(legal, lang, rtl):
    out = ['<section lang="%s" dir="%s" id="%s">' % (lang, 'rtl' if rtl else 'ltr', lang)]
    out.append('<nav>' + ' · '.join('<a href="#%s-%s">%s</a>' % (lang, d['id'], html.escape(d['title'])) for d in legal['docs']) + '</nav>')
    for d in legal['docs']:
        out.append('<article id="%s-%s"><h2>%s</h2>' % (lang, d['id'], html.escape(d['title'])))
        for s in d['sections']:
            out.append('<h3>%s</h3>' % html.escape(s['h']))
            for p in s['p']:
                out.append('<p>%s</p>' % html.escape(p, quote=False))
        out.append('</article>')
    upd = 'آخرین به‌روزرسانی' if rtl else 'Last updated'
    sup = 'پشتیبانی' if rtl else 'Support'
    out.append('<footer>%s: %s · %s: <a href="mailto:%s">%s</a></footer>' % (
        upd, html.escape(legal['updated']), sup, legal['contact'], legal['contact']))
    out.append('</section>')
    return '\n'.join(out)


fa, en = load('content_app_fa.json'), load('content_app_en.json')
assert fa['version'] == en['version'], 'fa and en legal versions differ'
page = """<!doctype html>
<html lang="fa" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>اندرز — شرایط استفاده و حریم خصوصی | Andarz — Terms and privacy</title>
<meta name="description" content="Andarz terms of use, privacy policy and risk warning (version %(v)d).">
<style>
body{font-family:system-ui,-apple-system,"Segoe UI",Tahoma,sans-serif;max-width:46rem;margin:0 auto;padding:1rem 1.2rem 3rem;line-height:1.8;color:#1b1f24;background:#fbfbfa}
h1{font-size:1.5rem}h2{margin-top:2rem;border-bottom:1px solid #ddd;padding-bottom:.3rem}h3{font-size:1.05rem;margin-bottom:.2rem}
nav{margin:.5rem 0 1rem}a{color:#0b6e6e}section{margin-top:2.5rem}footer{margin-top:1.5rem;font-size:.9rem;color:#555}
.lang{font-size:.95rem}
@media (prefers-color-scheme:dark){body{background:#121417;color:#e6e6e6}a{color:#4fd1c5}h2{border-color:#333}footer{color:#aaa}}
</style>
</head>
<body>
<h1>اندرز · Andarz</h1>
<p class="lang"><a href="#fa">فارسی</a> · <a href="#en" lang="en">English</a> — نسخهٔ %(v)d / version %(v)d</p>
%(fa)s
%(en)s
</body>
</html>
""" % {'v': fa['version'], 'fa': block(fa, 'fa', True), 'en': block(en, 'en', False)}
out = ROOT / 'docs/index.html'
out.write_text(page, 'utf-8')
(ROOT / 'docs/.nojekyll').write_text('', 'utf-8')
print('wrote', out.relative_to(ROOT), len(page), 'chars')
