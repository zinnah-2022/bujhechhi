"""স্ক্রিন শেয়ার (শুধু-দেখা মোড): ব্রাউজারে আসল ClassCore-এর বিরুদ্ধে পরীক্ষা।"""
import json, sys, time, os, urllib.request
from playwright.sync_api import sync_playwright

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8099"
SHOTS = sys.argv[2] if len(sys.argv) > 2 else "/tmp/shots"
os.makedirs(SHOTS, exist_ok=True)

def ctl(cmd):
    req = urllib.request.Request(BASE + "/ctl", data=cmd.encode("utf-8"), method="POST")
    return json.loads(urllib.request.urlopen(req, timeout=10).read().decode("utf-8"))
def frame():
    return int(urllib.request.urlopen(BASE + "/castframe").read())

fails = 0
def check(name, cond):
    global fails
    print(("ok   " if cond else "FAIL ") + name)
    if not cond: fails += 1

sess = ctl("start স্ক্রিন পরীক্ষা"); code = sess["code"]
with sync_playwright() as p:
    b = p.chromium.launch(executable_path="/usr/bin/google-chrome", args=["--no-sandbox"])
    ctx = b.new_context(viewport={"width": 390, "height": 844}, device_scale_factor=2, is_mobile=True, has_touch=True, locale="bn-BD")
    pg = ctx.new_page()
    errors = []
    pg.on("pageerror", lambda e: errors.append(str(e)))
    pg.goto(BASE + "/?c=" + code)
    pg.wait_for_selector("#sheet:not([hidden])")
    pg.fill("#nameIn", "রিয়"); pg.click(".sug"); pg.wait_for_selector(".tab.on")
    check("no viewer before cast", pg.locator("#viewer").is_hidden())

    # একটি প্রশ্ন শেয়ার আগে থেকেই দেখানো, পরে স্ক্রিন শেয়ার সেটাকে ঢেকে দেবে
    ctl("shareset 2"); ctl("shareshow 1")
    pg.wait_for_selector("#viewer:not([hidden])")
    pg.wait_for_function("document.getElementById('vimg').naturalWidth > 0")
    check("question viewer open first", "প্রশ্ন" in pg.locator("#vtitle").inner_text())

    ctl("caststart 1"); frame()
    pg.wait_for_function("document.getElementById('viewer').classList.contains('castmode')")
    check("cast mode on", pg.locator("#vtitle").inner_text().startswith("📺"))
    check("minimize/save hidden in cast mode", pg.locator("#vmin").is_hidden() and pg.locator("#vsave").is_hidden())
    pg.wait_for_function("document.getElementById('vimg').src.startsWith('blob:') && document.getElementById('vimg').naturalWidth > 0")
    check("first frame shown", pg.evaluate("document.getElementById('vwrap').style.visibility") == "visible")
    pg.screenshot(path=f"{SHOTS}/c1_cast.png")
    src1 = pg.evaluate("document.getElementById('vimg').src")

    # নতুন ফ্রেম এলে ছবি বদলায়
    frame()
    pg.wait_for_function("(s) => document.getElementById('vimg').src !== s", arg=src1, timeout=8000)
    check("new frame replaces image", True)

    # শুধু দেখা: জুম চলে, কিন্তু কোনো কন্ট্রোল নেই
    box = pg.locator("#vstage").bounding_box()
    w0 = pg.evaluate("document.getElementById('vwrap').getBoundingClientRect().width")
    pg.mouse.dblclick(box["x"] + box["width"] / 2, box["y"] + box["height"] / 2); time.sleep(0.3)
    w1 = pg.evaluate("document.getElementById('vwrap').getBoundingClientRect().width")
    check("zoom allowed for reading", w1 > w0 * 2)
    frame(); time.sleep(0.8)
    w2 = pg.evaluate("document.getElementById('vwrap').getBoundingClientRect().width")
    check("zoom kept when a new frame arrives", abs(w2 - w1) < 2)
    pg.mouse.dblclick(box["x"] + box["width"] / 2, box["y"] + box["height"] / 2); time.sleep(0.3)

    # বিরতি
    ctl("castpause 1")
    pg.wait_for_function("document.getElementById('vmsg').textContent.includes('থামিয়েছেন')")
    check("pause blanks the screen", pg.evaluate("document.getElementById('vwrap').style.visibility") == "hidden")
    check("server refuses frames while paused", not ctl(f"castok {code}")["ok"])
    pg.screenshot(path=f"{SHOTS}/c2_paused.png")
    ctl("castpause 0"); frame()
    pg.wait_for_function("document.getElementById('vwrap').style.visibility === 'visible'", timeout=8000)
    check("resume shows frames again", True)

    # বন্ধ হলে আগের প্রশ্ন-ভিউয়ারে ফেরে
    ctl("caststop")
    pg.wait_for_function("!document.getElementById('viewer').classList.contains('castmode')")
    pg.wait_for_function("document.getElementById('vtitle').textContent.includes('প্রশ্ন')")
    check("back to question viewer after stop", pg.locator("#viewer").is_visible() and pg.locator("#vmin").is_visible())
    pg.wait_for_function("document.getElementById('vimg').naturalWidth > 0")
    check("question image restored", True)

    # শেয়ার মুছে শুধু স্ক্রিন: বন্ধ হলে সব বন্ধ
    ctl("shareremove"); pg.wait_for_selector("#viewer", state="hidden")
    ctl("caststart 0"); frame()
    pg.wait_for_function("document.getElementById('viewer').classList.contains('castmode')")
    ctl("caststop")
    pg.wait_for_selector("#viewer", state="hidden")
    check("stop closes viewer when nothing else shared", pg.locator("#shareFab").is_hidden())

    # ক্লাস শেষ
    ctl("caststart 1"); frame()
    pg.wait_for_function("document.getElementById('viewer').classList.contains('castmode')")
    ctl("end")
    pg.wait_for_selector("text=ক্লাস শেষ")
    check("class end closes cast viewer", pg.locator("#viewer").is_hidden())
    check("no page errors", not errors)
    if errors: print(errors)
    b.close()

print("ALL PASSED" if fails == 0 else f"{fails} FAILED")
sys.exit(1 if fails else 0)
