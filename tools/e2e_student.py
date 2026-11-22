"""শিক্ষার্থীর পেজ ব্রাউজারে আসল ClassCore-এর বিরুদ্ধে পরীক্ষা (bridge.mjs চালু থাকতে হবে)।"""
import json, sys, time, urllib.request
from playwright.sync_api import sync_playwright

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8099"
SHOTS = sys.argv[2] if len(sys.argv) > 2 else "/tmp/shots"
import os; os.makedirs(SHOTS, exist_ok=True)

def ctl(cmd):
    req = urllib.request.Request(BASE + "/ctl", data=cmd.encode("utf-8"), method="POST")
    return json.loads(urllib.request.urlopen(req, timeout=10).read().decode("utf-8"))

fails = 0
def check(name, cond):
    global fails
    print(("ok   " if cond else "FAIL ") + name)
    if not cond: fails += 1

def wait_snap(pred, tries=30):
    for _ in range(tries):
        s = ctl("snap")
        if pred(s): return s
        time.sleep(0.2)
    return ctl("snap")

sess = ctl("start গণিত — ক্লাস ১")
code = sess["code"]

with sync_playwright() as p:
    b = p.chromium.launch(executable_path="/usr/bin/google-chrome", args=["--no-sandbox"])
    ctx = b.new_context(viewport={"width": 390, "height": 844}, device_scale_factor=2, is_mobile=True, has_touch=True, locale="bn-BD")
    pg = ctx.new_page()
    errors = []
    pg.on("pageerror", lambda e: errors.append(str(e)))
    pg.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)

    # ১. কোড ছাড়া খুললে কোড-স্ক্রিন; ভুল কোড
    pg.goto(BASE + "/")
    pg.wait_for_selector("#codeIn")
    check("code screen shown without ?c", True)
    pg.screenshot(path=f"{SHOTS}/01_code.png")
    pg.fill("#codeIn", "0000")
    pg.wait_for_selector("text=কোড মেলেনি")
    check("wrong code message", True)

    # ২. সঠিক কোড → নাম-শিট
    pg.fill("#codeIn", code)
    pg.wait_for_selector("#sheet:not([hidden])")
    check("join sheet opens automatically", True)
    pg.wait_for_selector(".sug")
    check("roster suggestions shown", pg.locator(".sug").count() == 6)
    pg.fill("#nameIn", "রিয়")
    check("suggestions filter", pg.locator(".sug").count() == 1)
    pg.screenshot(path=f"{SHOTS}/02_join.png")
    pg.click(".sug")
    pg.wait_for_selector(".tab.on")
    check("joined: tab visible", pg.locator(".tab.on").inner_text() == "রিয়া")
    check("sheet closed after join", pg.locator("#sheet").is_hidden())
    pg.screenshot(path=f"{SHOTS}/03_waiting.png")
    check("buttons disabled until topic", pg.locator(".fb").first.is_disabled())

    # ৩. বিষয় → ফিডব্যাক
    ctl("topic ভগ্নাংশের যোগ ও বিয়োগ: হর সমান করার নিয়ম")
    pg.wait_for_selector("#topicCard .txt:has-text('ভগ্নাংশ')")
    check("topic appears", True)
    pg.click(".fb.r")
    s = wait_snap(lambda s: s["r"] == 1)
    check("teacher sees red", s["r"] == 1 and s["students"][0]["n"] == "রিয়া")
    pg.screenshot(path=f"{SHOTS}/04_feedback_red.png")
    pg.click(".fb.y")
    s = wait_snap(lambda s: s["y"] == 1)
    check("changing vote updates counts", s["y"] == 1 and s["r"] == 0)
    pg.click(".fb.g")
    wait_snap(lambda s: s["g"] == 1)

    # ৪. একই ফোনে দ্বিতীয় জন
    pg.click(".tab.add")
    pg.wait_for_selector("#sheet:not([hidden])")
    pg.fill("#nameIn", "সুমি")
    pg.click("#joinBtn")
    pg.wait_for_function("document.querySelectorAll('.tab:not(.add)').length === 2")
    check("second profile on same phone", pg.locator(".tab.on").inner_text() == "সুমি")
    check("new profile has no vote yet", pg.locator(".fb.sel").count() == 0)
    pg.click(".fb.r")
    s = wait_snap(lambda s: s["r"] == 1 and s["g"] == 1)
    check("two names on one phone counted separately", s["r"] == 1 and s["g"] == 1 and all(x["sh"] for x in s["students"]))
    pg.click(".tab:has-text('রিয়া')")
    check("switching tab shows that profile's own vote", pg.locator(".fb.sel.g").count() == 1)
    pg.screenshot(path=f"{SHOTS}/05_two_profiles.png")

    # ৫. অন্য ডিভাইস থেকে একই নাম → আপত্তি
    ctx2 = b.new_context(viewport={"width": 390, "height": 844}, is_mobile=True)
    pg2 = ctx2.new_page()
    pg2.goto(BASE + "/?c=" + code)
    pg2.wait_for_selector("#sheet:not([hidden])")
    pg2.fill("#nameIn", "রিয়া")
    pg2.click("#joinBtn")
    pg2.wait_for_selector("#sheetErr:has-text('অন্য ডিভাইসে')")
    check("same name on another online phone is refused", True)
    pg2.fill("#nameIn", "করিম")
    pg2.click("#joinBtn")
    pg2.wait_for_selector(".tab.on")
    pg2.click(".fb.r")
    s = wait_snap(lambda s: s["r"] == 2)
    check("second device vote reaches teacher", s["r"] == 2 and s["online"] == 2)

    # ৬. কুইজ (একটা একটা করে)
    ctl("quiz step 20")
    pg.wait_for_selector(".qcard")
    check("quiz appears on student", pg.locator(".opt").count() == 4)
    check("timer visible", pg.locator("#tm").count() == 1)
    pg.screenshot(path=f"{SHOTS}/06_quiz.png")
    pg.click(".tab:has-text('রিয়া')")
    pg.click(".opt >> nth=1")
    s = wait_snap(lambda s: s["quiz"] and s["quiz"]["counts"][0][1] == 1)
    check("answer counted live", s["quiz"]["counts"][0][1] == 1)
    pg.click(".tab:has-text('সুমি')")
    check("other profile's answer not shown selected", pg.locator(".opt.sel").count() == 0)
    pg.click(".opt >> nth=0")
    s = wait_snap(lambda s: s["quiz"]["counts"][0][0] == 1)
    check("second profile answers separately", s["quiz"]["counts"][0][:2] == [1, 1])
    ctl("reveal")
    pg.wait_for_selector(".opt.right")
    check("reveal marks right and wrong", pg.locator(".opt.right").count() == 1 and pg.locator(".opt.wrong").count() == 1)
    check("explanation shown", pg.locator(".expl").inner_text().find("দুই অর্ধেক") >= 0)
    check("options disabled after reveal", pg.locator(".opt").first.is_disabled())
    pg.screenshot(path=f"{SHOTS}/07_reveal.png")
    ctl("next")
    pg.wait_for_function("document.querySelector('.qhead .n').innerText.indexOf('২') >= 0")
    check("next question shown", True)
    ctl("extend 5")
    ctl("quizend")
    pg.wait_for_selector(".rhero")
    check("result card shown when quiz ends", True)
    pg.locator(".rok").click()
    pg.wait_for_selector(".fbs")
    check("back to feedback after dismissing result", True)

    # ৭. রিলোড করলে প্রোফাইল ফিরে আসে
    pg.reload()
    pg.wait_for_selector(".tab.on")
    check("reload restores both profiles", pg.locator(".tab:not(.add)").count() == 2)

    # ৮. সংযোগ কাটলে আবার যুক্ত হয়
    urllib.request.urlopen(BASE + "/drop", timeout=5).read()
    pg.wait_for_selector(".netbar", timeout=15000)
    check("offline banner shown after connection drop", True)
    pg.wait_for_selector(".netbar", state="detached", timeout=30000)
    check("auto reconnect, profiles intact", pg.locator(".tab:not(.add)").count() == 2)

    # ৯. ডার্ক মোড স্ক্রিনশট
    ctx3 = b.new_context(viewport={"width": 390, "height": 844}, device_scale_factor=2, is_mobile=True, color_scheme="dark")
    pg3 = ctx3.new_page()
    pg3.goto(BASE + "/?c=" + code)
    pg3.wait_for_selector("#sheet:not([hidden])")
    pg3.fill("#nameIn", "রফিক"); pg3.click("#joinBtn")
    pg3.wait_for_selector(".tab.on")
    pg3.click(".fb.g")
    pg3.screenshot(path=f"{SHOTS}/08_dark.png")

    # ১০. ক্লাস শেষ
    rep_id = ctl("end")["id"]
    pg.wait_for_selector("text=ক্লাস শেষ")
    check("ended screen", True)

    check("no JS errors", not errors)
    if errors: print(errors[:5])
    b.close()

rep = ctl("report " + str(rep_id)) if False else None
print("\nALL PASSED" if fails == 0 else f"\n{fails} FAILED")
sys.exit(1 if fails else 0)
