(function () {
  try {
    localStorage.removeItem("__palette");
  } catch (e) {
  }

  function lockScheme() {
    document.documentElement.setAttribute("data-md-color-scheme", "default");
    if (document.body) document.body.setAttribute("data-md-color-scheme", "default");
  }

  lockScheme();
  document.addEventListener("DOMContentLoaded", lockScheme);
})();

(function () {
  var desktop = window.matchMedia("(min-width: 76.25em)");

  function placeSearch() {
    var search = document.querySelector(".md-search");
    if (!search) return;

    if (desktop.matches) {
      var nav = document.querySelector(".md-sidebar--primary .md-nav--primary");
      if (!nav || search.parentElement === nav) return;
      var list = nav.querySelector(":scope > .md-nav__list");
      if (list) nav.insertBefore(search, list);
      else nav.appendChild(search);
      return;
    }

    var header = document.querySelector(".md-header__inner");
    if (!header || search.parentElement === header) return;
    var source = header.querySelector(".md-header__source");
    if (source) header.insertBefore(search, source);
    else header.appendChild(search);
  }

  function configureSearch() {
    if (window.__tierdbSearchConfigured) return;
    window.__tierdbSearchConfigured = true;

    function closeSearch() {
      var toggle = document.querySelector("#__search");
      var query = document.querySelector(".md-search__input");
      if (toggle) toggle.checked = false;
      if (query) query.blur();
    }

    document.addEventListener("reset", function (event) {
      var search = document.querySelector(".md-sidebar--primary .md-search");
      if (!search || !search.contains(event.target)) return;
      window.setTimeout(closeSearch, 0);
    }, true);

    document.addEventListener("keydown", function (event) {
      if (event.key !== "Escape") return;
      var toggle = document.querySelector("#__search");
      if (toggle && toggle.checked) closeSearch();
    });

    document.addEventListener("pointerdown", function (event) {
      var search = document.querySelector(".md-sidebar--primary .md-search");
      var toggle = document.querySelector("#__search");
      if (!search || !toggle || !toggle.checked) return;
      if (!search.contains(event.target)) closeSearch();
    });
  }

  function syncSectionExpansion() {
    var items = document.querySelectorAll(
        ".md-nav--primary > .md-nav__list > .md-nav__item--nested"
    );

    Array.prototype.forEach.call(items, function (item, index) {
      var toggle = item.querySelector(":scope > .md-nav__toggle");
      if (!toggle) return;
      if (desktop.matches) {
        if (index < 2) toggle.checked = true;
        return;
      }
      toggle.checked = !!item.querySelector(".md-nav__link--active");
    });
  }

  function styleRepositoryButtons(container) {
    var source = container && container.querySelector(".md-source");
    var label = source && source.querySelector(".md-source__repository");
    if (!source || !label || container.querySelector(".tierdb-github-star")) return;

    container.classList.add("tierdb-github-actions");
    source.classList.add("tierdb-github-button");
    label.textContent = "GitHub";

    var iconContainer = source.querySelector(".md-source__icon");
    var icon = iconContainer && iconContainer.querySelector("svg");
    if (icon) {
      icon.classList.add("tierdb-github-icon");
      source.appendChild(icon);
      iconContainer.remove();
    }

    var star = document.createElement("a");
    star.className = "tierdb-github-star";
    star.href = source.href.replace(/\/$/, "") + "/stargazers";
    star.title = "Star on GitHub";
    star.setAttribute("aria-label", "Star on GitHub");
    star.innerHTML =
        '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m12 2 3.09 6.26L22 9.27l-5 4.87L18.18 21 12 17.77 5.82 21 7 14.14 2 9.27l6.91-1.01L12 2Z"/></svg>' +
        "<span>Star</span>";
    container.insertBefore(star, source);
  }

  function configureRepositoryButtons() {
    styleRepositoryButtons(document.querySelector(".md-header__source"));
    styleRepositoryButtons(document.querySelector(".md-nav__source"));
  }

  function init() {
    placeSearch();
    configureSearch();
    syncSectionExpansion();
    configureRepositoryButtons();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }

  if (desktop.addEventListener) {
    desktop.addEventListener("change", function () {
      placeSearch();
      syncSectionExpansion();
    });
  } else {
    window.addEventListener("resize", function () {
      placeSearch();
      syncSectionExpansion();
    });
  }

  if (typeof document$ !== "undefined" && document$.subscribe) {
    document$.subscribe(function () {
      window.setTimeout(init, 0);
    });
  }
})();
