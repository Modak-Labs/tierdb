(function () {
  var EYES = [
    { cx: 20.68, cy: 52.91 },
    { cx: 27.63, cy: 44.12 },
    { cx: 70.40, cy: 37.02 },
    { cx: 78.29, cy: 44.95 },
  ];
  var EYE_RADIUS_PCT = 2.7;
  var PUPIL_RADIUS_RATIO = 0.48;
  var SOFT_DISTANCE_PX = 80;

  function pupilOffset(eyeX, eyeY, mouseX, mouseY, eyeRadiusPx) {
    var dx = mouseX - eyeX;
    var dy = mouseY - eyeY;
    var dist = Math.hypot(dx, dy);
    if (dist === 0) return { x: 0, y: 0 };
    var pupilRadiusPx = eyeRadiusPx * PUPIL_RADIUS_RATIO;
    var maxOffset = Math.max(eyeRadiusPx - pupilRadiusPx, 0);
    var scale = Math.min(dist / SOFT_DISTANCE_PX, 1);
    return {
      x: Number(((dx / dist) * maxOffset * scale).toFixed(2)),
      y: Number(((dy / dist) * maxOffset * scale).toFixed(2)),
    };
  }

  function assetUrl(file) {
    var scripts = document.querySelectorAll('script[src*="mascots"]');
    var src =
      scripts.length > 0
        ? scripts[scripts.length - 1].getAttribute("src")
        : "";
    if (src) {
      return src.replace(/[^/]*$/, "") + "../" + file;
    }
    return "assets/" + file;
  }

  function mount() {
    if (document.querySelector(".tierdb-crew")) return;

    var host =
      document.querySelector(".md-sidebar--secondary .md-sidebar__inner") ||
      document.querySelector(".md-sidebar--secondary");
    if (!host) return;

    var wrap = document.createElement("aside");
    wrap.className = "tierdb-crew";
    wrap.setAttribute("aria-hidden", "true");

    var stage = document.createElement("div");
    stage.className = "tierdb-crew__stage";

    var img = document.createElement("img");
    img.className = "tierdb-crew__image";
    img.src = assetUrl("images/crew.png");
    img.alt = "";
    img.draggable = false;
    img.width = 604;
    img.height = 287;
    stage.appendChild(img);

    var pupils = [];
    EYES.forEach(function (eye) {
      var el = document.createElement("span");
      el.className = "tierdb-crew__pupil";
      el.style.left = eye.cx + "%";
      el.style.top = eye.cy + "%";
      stage.appendChild(el);
      pupils.push(el);
    });

    wrap.appendChild(stage);
    host.insertBefore(wrap, host.firstChild);

    var reduceMotion =
      window.matchMedia &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduceMotion) return;

    var last = { x: window.innerWidth * 0.55, y: window.innerHeight * 0.4 };
    var raf = 0;

    function update(mx, my) {
      var rect = stage.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0) return;
      var eyeRadiusPx = (EYE_RADIUS_PCT / 100) * rect.width;

      EYES.forEach(function (eye, i) {
        var pupil = pupils[i];
        if (!pupil) return;
        var eyeX = rect.left + (eye.cx / 100) * rect.width;
        var eyeY = rect.top + (eye.cy / 100) * rect.height;
        var off = pupilOffset(eyeX, eyeY, mx, my, eyeRadiusPx);
        pupil.style.transform =
          "translate(calc(-50% + " + off.x + "px), calc(-50% + " + off.y + "px))";
      });
    }

    function schedule() {
      if (raf) return;
      raf = window.requestAnimationFrame(function () {
        raf = 0;
        update(last.x, last.y);
      });
    }

    window.addEventListener(
      "mousemove",
      function (e) {
        last.x = e.clientX;
        last.y = e.clientY;
        schedule();
      },
      { passive: true }
    );
    window.addEventListener(
      "touchmove",
      function (e) {
        var t = e.touches[0];
        if (!t) return;
        last.x = t.clientX;
        last.y = t.clientY;
        schedule();
      },
      { passive: true }
    );
    window.addEventListener("scroll", schedule, { passive: true });
    window.addEventListener("resize", schedule, { passive: true });

    img.addEventListener("load", schedule);
    schedule();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", mount);
  } else {
    mount();
  }

  if (typeof document$ !== "undefined" && document$.subscribe) {
    document$.subscribe(function () {
      window.setTimeout(mount, 0);
    });
  }
})();
