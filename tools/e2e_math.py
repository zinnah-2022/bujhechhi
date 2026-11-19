"""শিক্ষার্থীর পেজে গণিত (KaTeX, অফলাইন) দেখানোর পরীক্ষা।"""
import json, sys, os, urllib.request
from playwright.sync_api import sync_playwright
BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8099"
SHOTS = sys.argv[2] if len(sys.argv) > 2 else "/tmp/shots"
os.makedirs(SHOTS, exist_ok=True)
def ctl(c):
    return json.loads(urllib.request.urlopen(urllib.request.Request(BASE + "/ctl", data=c.encode("utf-8"), method="POST"), timeout=10).read().decode("utf-8"))
fails = 0
def check(n, c):
    global fails
    print(("ok   " if c else "FAIL ") + n)
    if not c: fails += 1
code = ctl("start গণিত ক্লাস")["code"]
with sync_playwright() as p:
    b = p.chromium.launch(executable_path="/usr/bin/google-chrome", args=["--no-sandbox"])
    ctx = b.new_context(viewport={"width": 390, "height": 844}, device_scale_factor=2, is_mobile=True, has_touch=True)
    ext = []
    ctx.on("request", lambda r: ext.append(r.url) if not r.url.startswith(BASE) and not r.url.startswith("data:") else None)
    pg = ctx.new_page(); errs = []
    pg.on("pageerror", lambda e: errs.append(str(e)))
    pg.goto(BASE + "/?c=" + code)
    pg.wait_for_selector("#sheet:not([hidden])")
    pg.fill("#nameIn", "রিয়"); pg.click(".sug"); pg.wait_for_selector(".tab.on")
    check("katex loaded offline", pg.evaluate("typeof katex") == "object")
    ctl("mathquiz")
    pg.wait_for_selector(".qt .katex")
    check("question math rendered", pg.locator(".qt .katex").count() >= 1 and pg.locator(".qt .katex .mfrac").count() == 2)
    check("option sqrt/fraction rendered", pg.locator(".opt .katex").count() >= 3)
    check("currency $5 and $6 stays plain text", "$5" in pg.locator(".qt").inner_text() and "$6" in pg.locator(".qt").inner_text())
    check("html in option is escaped, not injected", "<b>" in pg.locator(".opt >> nth=3").inner_text() and pg.locator(".opt > span b").count() == 0)
    check("bad latex does not break page", pg.locator(".opt").count() == 4)
    pg.screenshot(path=f"{SHOTS}/m1_quiz.png")
    pg.locator(".opt").first.click()
    ctl("reveal")
    pg.wait_for_selector(".expl .katex-display")
    check("explanation display math rendered", pg.locator(".expl .katex-display").count() == 1)
    check("tag in explanation escaped", pg.locator(".expl i").count() == 0)
    pg.screenshot(path=f"{SHOTS}/m2_reveal.png")
    ok = pg.evaluate("document.fonts.ready.then(()=>[...document.fonts].filter(f=>f.family.startsWith('KaTeX')&&f.status==='loaded').length)")
    check("katex fonts load from the phone", ok >= 1)
    check("no external requests", not ext)
    check("no page errors", not errs)
    if errs: print(errs)
    if ext: print(ext)
    b.close()
print("ALL PASSED" if not fails else f"{fails} FAILED")
sys.exit(1 if fails else 0)
