/* ==========================================================================
   Rewordium Technical Documentation - Interactive Logic
   Provides:
   - Theme toggle (light/dark) with persistence
   - Interactive tab selectors for API structures
   - Client-side keyword search & filter system
   - Responsive sidebar toggler for mobile devices
   - Scroll-Spy for auto-highlighting navigation elements
   - Copy-to-clipboard buttons with animations
   ========================================================================== */

document.addEventListener("DOMContentLoaded", () => {
  // --- Theme Toggler & Manager ---
  const themeToggleBtn = document.getElementById("theme-toggle-btn");
  const currentTheme = localStorage.getItem("rewordium-docs-theme") || "dark";

  // Set initial theme
  document.documentElement.setAttribute("data-theme", currentTheme);

  themeToggleBtn.addEventListener("click", () => {
    const activeTheme = document.documentElement.getAttribute("data-theme");
    const newTheme = activeTheme === "dark" ? "light" : "dark";

    document.documentElement.setAttribute("data-theme", newTheme);
    localStorage.setItem("rewordium-docs-theme", newTheme);
  });

  // --- Mobile Drawer Navigation Toggler ---
  const mobileMenuBtn = document.getElementById("mobile-menu-btn");
  const sidebar = document.querySelector("aside");

  if (mobileMenuBtn && sidebar) {
    mobileMenuBtn.addEventListener("click", (e) => {
      sidebar.classList.toggle("active");
      e.stopPropagation();
    });

    // Close sidebar on tapping any links in mobile view
    document.querySelectorAll(".nav-link").forEach((link) => {
      link.addEventListener("click", () => {
        sidebar.classList.remove("active");
      });
    });

    // Close sidebar when clicking anywhere outside it
    document.addEventListener("click", (e) => {
      if (sidebar.classList.contains("active") && !sidebar.contains(e.target) && e.target !== mobileMenuBtn) {
        sidebar.classList.remove("active");
      }
    });
  }

  // --- Scroll-Spy Wayfinding ---
  const sections = document.querySelectorAll(".content-section");
  const navLinks = document.querySelectorAll(".nav-link");

  const observerOptions = {
    root: null,
    rootMargin: "-20% 0px -60% 0px", // Focus center viewport segment
    threshold: 0
  };

  const spyObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        const id = entry.target.getAttribute("id");
        navLinks.forEach((link) => {
          if (link.getAttribute("href") === `#${id}`) {
            link.classList.add("active");
          } else {
            link.classList.remove("active");
          }
        });
      }
    });
  }, observerOptions);

  sections.forEach((section) => spyObserver.observe(section));

  // --- API Providers Tab Switching ---
  const tabContainers = document.querySelectorAll(".tab-container");
  tabContainers.forEach((container) => {
    const tabBtns = container.querySelectorAll(".tab-btn");
    const tabContents = container.querySelectorAll(".tab-content");

    tabBtns.forEach((btn) => {
      btn.addEventListener("click", () => {
        const targetTab = btn.getAttribute("data-tab");

        tabBtns.forEach((b) => b.classList.remove("active"));
        tabContents.forEach((c) => c.classList.remove("active"));

        btn.classList.add("active");
        const matchingContent = container.querySelector(`.tab-content[data-tab-content="${targetTab}"]`);
        if (matchingContent) {
          matchingContent.classList.add("active");
        }
      });
    });
  });

  // --- Code Blocks Copy-to-Clipboard ---
  const preElements = document.querySelectorAll("pre");
  preElements.forEach((pre) => {
    // Wrap pre content in a helper container if not done
    const codeContainer = document.createElement("div");
    codeContainer.className = "code-container";
    pre.parentNode.insertBefore(codeContainer, pre);
    codeContainer.appendChild(pre);

    // Create Copy Button
    const copyBtn = document.createElement("button");
    copyBtn.className = "copy-btn";
    copyBtn.innerText = "Copy";
    codeContainer.appendChild(copyBtn);

    copyBtn.addEventListener("click", async () => {
      const codeText = pre.querySelector("code").innerText;
      try {
        await navigator.clipboard.writeText(codeText);
        copyBtn.innerText = "Copied!";
        copyBtn.classList.add("copied");

        setTimeout(() => {
          copyBtn.innerText = "Copy";
          copyBtn.classList.remove("copied");
        }, 2000);
      } catch (err) {
        console.error("Clipboard copy failed: ", err);
        copyBtn.innerText = "Failed";
      }
    });
  });

  // --- High-Performance Document Keyword Search ---
  const searchInput = document.getElementById("search-input");
  
  if (searchInput) {
    searchInput.addEventListener("input", (e) => {
      const query = e.target.value.toLowerCase().trim();

      // ALWAYS remove existing highlights first to prevent stale markers
      sections.forEach((section) => {
        removeHighlight(section);
      });

      if (query === "") {
        // Reset: show all sections
        sections.forEach((section) => {
          section.style.display = "block";
        });
        return;
      }

      sections.forEach((section) => {
        const textContent = section.innerText.toLowerCase();
        const matches = textContent.includes(query);

        if (matches) {
          section.style.display = "block";
          highlightText(section, query);
        } else {
          section.style.display = "none";
        }
      });
    });
  }

  // Highlight search matches
  function highlightText(element, query) {
    // Avoid running search inside pre blocks or SVGs to keep them clean
    const walker = document.createTreeWalker(
      element,
      NodeFilter.SHOW_TEXT,
      {
        acceptNode: (node) => {
          const parent = node.parentElement;
          if (
            parent.tagName === "CODE" || 
            parent.tagName === "PRE" || 
            parent.tagName === "SCRIPT" ||
            parent.tagName === "STYLE" ||
            parent.closest(".flow-diagram")
          ) {
            return NodeFilter.FILTER_REJECT;
          }
          return NodeFilter.FILTER_ACCEPT;
        }
      }
    );

    const nodesToReplace = [];
    while (walker.nextNode()) {
      const node = walker.currentNode;
      if (node.nodeValue.toLowerCase().includes(query)) {
        nodesToReplace.push(node);
      }
    }

    nodesToReplace.forEach((node) => {
      const text = node.nodeValue;
      const regex = new RegExp(`(${escapeRegExp(query)})`, "gi");
      
      const tempSpan = document.createElement("span");
      tempSpan.innerHTML = text.replace(regex, `<mark style="background-color: var(--brand-primary-light); color: var(--brand-primary); padding: 0.1em 0.25em; border-radius: 3px; font-weight: 600;">$1</mark>`);
      
      // Replace node with span elements safely
      const parent = node.parentNode;
      if (parent) {
        while (tempSpan.firstChild) {
          parent.insertBefore(tempSpan.firstChild, node);
        }
        parent.removeChild(node);
      }
    });
  }

  // Remove search matches highlights
  function removeHighlight(element) {
    const marks = element.querySelectorAll("mark");
    marks.forEach((mark) => {
      const parent = mark.parentNode;
      if (parent) {
        const textNode = document.createTextNode(mark.innerText);
        parent.replaceChild(textNode, mark);
        parent.normalize(); // merge neighboring text nodes
      }
    });
  }

  // Helper function to escape special regex chars
  function escapeRegExp(string) {
    return string.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  }
});
