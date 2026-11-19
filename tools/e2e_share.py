"""প্রশ্ন শেয়ার (ছবি, জুম, পাতা সিঙ্ক, দাগ, সেভ) ব্রাউজারে আসল ClassCore-এর বিরুদ্ধে পরীক্ষা।"""
import json, sys, time, os, urllib.request
from playwright.sync_api import sync_playwright

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8099"
SHOTS = sys.argv[2] if len(sys.argv) > 2 else "/tmp/shots"
os.makedirs(SHOTS, exist_ok=True)

def ctl(cmd):
    req = urllib.request.Request(BASE + "/ctl", data=cmd.encode("utf-8"), method="POST")
    return json.loads(urllib.request.urlopen(req, timeout=10).read().decode("utf-8"))

fails = 0
def check(name, cond):
    global fails
    print(("ok   " if cond else "FAIL ") + name)
    if not cond: fails += 1

sess = ctl("start শেয়ার পরীক্ষা"); code = sess["code"]

with sync_playwright() as p:
    b = p.chromium.launch(executable_path="/usr/bin/google-chrome", args=["--no-sandbox"])
    ctx = b.new_context(viewport={"width": 390, "height": 844}, device_scale_factor=2, is_mobile=True, has_touch=True, locale="bn-BD", accept_downloads=True)
    pg = ctx.new_page()
    errors = []
    pg.on("pageerror", lambda e: errors.append(str(e)))
    pg.goto(BASE + "/?c=" + code)
    pg.wait_for_selector("#sheet:not([hidden])")
    pg.fill("#nameIn", "রিয়"); pg.click(".sug")
    pg.wait_for_selector(".tab.on")
    check("no viewer before share", pg.locator("#viewer").is_hidden() and pg.locator("#shareFab").is_hidden())

    sid = ctl("shareset 3")["id"]
    time.sleep(0.4)
    check("hidden share: still no viewer", pg.locator("#viewer").is_hidden())
    check("hidden share: image refused", not ctl(f"imgok {sid} 0 {code} 0")["ok"])

    ctl("shareshow 1")
    pg.wait_for_selector("#viewer:not([hidden])")
    check("viewer opens when teacher shows", True)
    pg.wait_for_function("document.getElementById('vimg').naturalWidth > 0")
    check("image loaded", True)
    check("title shows page 1/3", "১/৩" in pg.locator("#vtitle").inner_text())
    check("save hidden by default", pg.locator("#vsave").is_hidden())
    pg.screenshot(path=f"{SHOTS}/s1_viewer.png")
    w0 = pg.evaluate("document.getElementById('vwrap').getBoundingClientRect().width")

    # দুইবার ট্যাপে জুম
    box = pg.locator("#vstage").bounding_box()
    cx, cy = box["x"] + box["width"] / 2, box["y"] + box["height"] / 2
    pg.mouse.dblclick(cx, cy)
    time.sleep(0.3)
    w1 = pg.evaluate("document.getElementById('vwrap').getBoundingClientRect().width")
    check("double-tap zooms in", w1 > w0 * 2)
    pg.screenshot(path=f"{SHOTS}/s2_zoomed.png")
    pg.mouse.dblclick(cx, cy)
    time.sleep(0.3)
    w2 = pg.evaluate("document.getElementById('vwrap').getBoundingClientRect().width")
    check("double-tap again zooms out", abs(w2 - w0) < 2)
    # ওয়াইল জুম
    pg.mouse.move(cx, cy); pg.mouse.wheel(0, -400); time.sleep(0.2)
    w3 = pg.evaluate("document.getElementById('vwrap').getBoundingClientRect().width")
    check("wheel zoom works", w3 > w0 * 1.1)
    pg.mouse.dblclick(cx, cy); time.sleep(0.2)
    if pg.evaluate("document.getElementById('vwrap').getBoundingClientRect().width") > w0 * 1.05:
        pg.mouse.dblclick(cx, cy); time.sleep(0.2)

    # দুই আঙুলে পিঞ্চ (আসল টাচ ইভেন্ট, CDP দিয়ে)
    cdp = ctx.new_cdp_session(pg)
    def touch(kind, pts):
        cdp.send("Input.dispatchTouchEvent", {"type": kind, "touchPoints": [{"x": x, "y": y, "id": i} for i, (x, y) in enumerate(pts)]})
    touch("touchStart", [(cx - 30, cy), (cx + 30, cy)])
    for step in range(1, 9):
        d = 30 + step * 18
        touch("touchMove", [(cx - d, cy), (cx + d, cy)])
        time.sleep(0.03)
    touch("touchEnd", [])
    time.sleep(0.3)
    wp = pg.evaluate("document.getElementById('vwrap').getBoundingClientRect().width")
    check("pinch zoom works", wp > w0 * 1.8)
    pg.screenshot(path=f"{SHOTS}/s2b_pinched.png")
    # এক আঙুলে টেনে সরানো
    r0 = pg.evaluate("document.getElementById('vwrap').getBoundingClientRect().left")
    touch("touchStart", [(cx, cy)])
    for step in range(1, 7):
        touch("touchMove", [(cx + step * 15, cy)]); time.sleep(0.02)
    touch("touchEnd", [])
    time.sleep(0.2)
    r1 = pg.evaluate("document.getElementById('vwrap').getBoundingClientRect().left")
    check("one-finger pan moves image", r1 > r0 + 20 or r1 == 0 or abs(r1 - r0) > 20)
    pg.mouse.dblclick(cx, cy); time.sleep(0.3)
    wq = pg.evaluate("document.getElementById('vwrap').getBoundingClientRect().width")
    if wq > w0 * 1.05:
        pg.mouse.dblclick(cx, cy); time.sleep(0.3)

    # লাইভ দাগ
    k = ctl("drawstart #2563EB 0.1,0.2,0.3,0.25")["k"]
    ctl(f"drawmore {k} 0.5,0.3,0.7,0.2")
    time.sleep(0.3)
    px = pg.evaluate("""(() => { const c=document.getElementById('vcan'); const x=c.getContext('2d'); const d=x.getImageData(0,0,c.width,c.height).data; let n=0; for(let i=3;i<d.length;i+=4) if(d[i]>0) n++; return n; })()""")
    check("teacher stroke painted on canvas", px > 200)
    pg.screenshot(path=f"{SHOTS}/s3_drawn.png")

    # পাতা সিঙ্ক ও দাগ পাতা-ভিত্তিক
    ctl("sharegoto 1")
    pg.wait_for_function("document.getElementById('vtitle').textContent.includes('২/৩')")
    check("page sync to 2/3", True)
    time.sleep(0.4)
    px2 = pg.evaluate("""(() => { const c=document.getElementById('vcan'); const x=c.getContext('2d'); const d=x.getImageData(0,0,c.width,c.height).data; let n=0; for(let i=3;i<d.length;i+=4) if(d[i]>0) n++; return n; })()""")
    check("strokes do not leak to other page", px2 == 0)
    ctl("sharegoto 0")
    pg.wait_for_function("document.getElementById('vtitle').textContent.includes('১/৩')")
    time.sleep(0.4)
    px3 = pg.evaluate("""(() => { const c=document.getElementById('vcan'); const x=c.getContext('2d'); const d=x.getImageData(0,0,c.width,c.height).data; let n=0; for(let i=3;i<d.length;i+=4) if(d[i]>0) n++; return n; })()""")
    check("strokes restored when returning to page", px3 > 200)
    ctl("undo"); time.sleep(0.3)
    px4 = pg.evaluate("""(() => { const c=document.getElementById('vcan'); const x=c.getContext('2d'); const d=x.getImageData(0,0,c.width,c.height).data; let n=0; for(let i=3;i<d.length;i+=4) if(d[i]>0) n++; return n; })()""")
    check("undo clears stroke", px4 == 0)

    # ছোট করা ও ফ্যাব
    pg.click("#vmin")
    check("minimize shows fab", pg.locator("#shareFab").is_visible() and pg.locator("#viewer").is_hidden())
    ctl("sharegoto 2"); time.sleep(0.3)
    check("fab shows page while minimized", "৩/৩" in pg.locator("#shareFab").inner_text() and pg.locator("#viewer").is_hidden())
    pg.screenshot(path=f"{SHOTS}/s4_fab.png")
    pg.click("#shareFab")
    check("fab reopens viewer", pg.locator("#viewer").is_visible())

    # সেভ অনুমতি
    ctl("sharesave 1")
    pg.wait_for_selector("#vsave:not([hidden])")
    check("save button appears when allowed", True)
    with pg.expect_download(timeout=5000) as dl:
        pg.click("#vsave")
    check("download works", dl.value.suggested_filename.startswith("q") )
    ctl("sharesave 0")
    pg.wait_for_selector("#vsave", state="hidden")
    check("save button hides again", True)
    check("download refused by server when off", not ctl(f"imgok {sid} 2 {code} 1")["ok"])

    # লুকান
    ctl("shareshow 0")
    pg.wait_for_selector("#viewer", state="hidden")
    check("hide closes viewer and fab", pg.locator("#shareFab").is_hidden())
    ctl("shareshow 1")
    pg.wait_for_selector("#viewer:not([hidden])")
    check("show again reopens", True)

    # রিকানেক্ট: ফোন সংযোগ হারিয়ে আবার এলে
    urllib.request.urlopen(BASE + "/drop").read()
    pg.wait_for_function("document.getElementById('viewer').hidden === false", timeout=10000)
    time.sleep(2.5)
    check("viewer persists after reconnect", pg.locator("#viewer").is_visible())

    ctl("shareremove")
    pg.wait_for_selector("#viewer", state="hidden")
    check("remove closes viewer", True)
    check("no page errors", not errors)
    if errors: print(errors)
    b.close()

print("ALL PASSED" if fails == 0 else f"{fails} FAILED")
sys.exit(1 if fails else 0)
