(function () {
  var BUBBLES = [
    {
      left: 21,
      top: 28,
      text: "Hot Store",
      tail: "se",
    },
    {
      left: 84,
      top: 14,
      text: "Cold Store",
      tail: "sw",
    },
  ];

  function createBubble(bubble) {
    var el = document.createElement("div");
    el.className = "tierdb-hot-cold__bubble tierdb-hot-cold__bubble--" + bubble.tail;
    el.setAttribute("aria-hidden", "true");
    el.style.left = bubble.left + "%";
    el.style.top = bubble.top + "%";
    el.textContent = bubble.text;
    return el;
  }

  function mount() {
    var img = document.querySelector('.md-typeset img[src*="hot-cold"]');
    if (!img || img.closest(".tierdb-hot-cold")) {
      return;
    }

    var figure = document.createElement("figure");
    figure.className = "tierdb-hot-cold";

    var stage = document.createElement("div");
    stage.className = "tierdb-hot-cold__stage";

    var parent = img.parentNode;
    stage.appendChild(img);
    figure.appendChild(stage);

    BUBBLES.forEach(function (bubble) {
      stage.appendChild(createBubble(bubble));
    });

    if (parent && parent.tagName === "P") {
      parent.parentNode.insertBefore(figure, parent);
      parent.remove();
    } else if (parent) {
      parent.insertBefore(figure, img);
    }
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
