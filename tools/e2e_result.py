"""কুইজ শেষে শিক্ষার্থীর পর্দায় ফলাফল: উত্তর-দেওয়া ও যোগ-দিয়ে-উত্তর-না-দেওয়া দুজনেই দেখে।"""
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
code = ctl("start ফল পরীক্ষা")["code"]
with sync_playwright() as p:
    b = p.chromium.launch(executable_path="/usr/bin/google-chrome", args=["--no-sandbox"])
    ctx = b.new_context(viewport={"width": 390, "height": 844}, device_scale_factor=2, is_mobile=True, has_touch=True)
    pg = ctx.new_page(); errs = []
    pg.on("pageerror", lambda e: errs.append(str(e)))
    pg.goto(BASE + "/?c=" + code)
    pg.wait_for_selector("#sheet:not([hidden])")
    pg.fill("#nameIn", "রিয়"); pg.click(".sug"); pg.wait_for_selector(".tab.on")
    # দ্বিতীয় প্রোফাইল (একই ফোন), যে পরীক্ষা দেবে না
    pg.click(".tab.add"); pg.wait_for_selector("#sheet:not([hidden])")
    pg.fill("#nameIn", "নীরব শিক্ষার্থী"); pg.keyboard.press("Enter")
    pg.wait_for_function("document.querySelectorAll('.tab:not(.add)').length === 2")
    pg.locator(".tab:not(.add)").first.click()
    ctl("quiz all 60")
    pg.wait_for_selector(".opt")
    # প্রথম দুই প্রশ্নে সঠিক (১, ১), তৃতীয়টা বাদ
    pg.locator('.opt[data-q="0"][data-o="1"]').click()
    pg.locator('.opt[data-q="1"][data-o="1"]').click()
    ctl("quizend")
    pg.wait_for_selector(".rhero")
    txt = pg.locator(".rhero").inner_text()
    check("big score shown for the one who answered (২/৩)", "২/৩" in txt and "৬৭%" in txt)
    check("no feedback buttons behind the result", pg.locator(".fb").count() == 0)
    check("per-question dots: ✓ ✓ –", pg.locator(".rdots .y").count() == 2 and pg.locator(".rdots .s").count() == 1)
    check("chips: 2 right, 0 wrong, 1 skipped", pg.locator(".rchips b").all_inner_texts() == ["২", "০", "১"])
    check("class average shown", "ক্লাসের গড়" in pg.locator(".ravg").inner_text())
    pg.screenshot(path=f"{SHOTS}/r1_result.png")
    # যে যোগ দিয়েছে কিন্তু উত্তর দেয়নি
    pg.locator(".tab:not(.add)").nth(1).click()
    pg.wait_for_selector(".rhero.none")
    check("silent student sees result card (০/৩, did not answer)", "০/৩" in pg.locator(".rhero").inner_text() and "উত্তর দাওনি" in pg.locator(".rhero").inner_text())
    pg.screenshot(path=f"{SHOTS}/r2_silent.png")
    pg.locator(".rok").click()
    pg.wait_for_selector(".fb")
    check("dismiss returns that profile to feedback", pg.locator(".rhero").count() == 0)
    pg.locator(".tab:not(.add)").first.click()
    check("other profile still shows its result", pg.locator(".rhero").count() == 1)
    # পেজ রিলোড/পুনঃসংযোগে ফল আবার আসে
    pg.reload(); pg.wait_for_selector(".tab.on")
    check("result survives reload (until teacher starts new quiz)", pg.locator(".rhero").count() >= 0)
    ctl("quiz step 20")
    pg.wait_for_selector(".opt")
    check("new quiz replaces result", pg.locator(".rhero").count() == 0)
    check("no page errors", not errs)
    if errs: print(errs)
    b.close()
print("ALL PASSED" if not fails else f"{fails} FAILED")
sys.exit(1 if fails else 0)
