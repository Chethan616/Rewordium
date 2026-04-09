import { initExploderScene, initShowcaseScene } from "./threeScene.js";

const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

window.addEventListener(
  "load",
  () => {
    initializePortfolioPage();
  },
  { once: true }
);

function initializePortfolioPage() {
  const gsap = window.gsap;
  const ScrollTrigger = window.ScrollTrigger;
  const hasGsap = Boolean(gsap && ScrollTrigger);

  if (hasGsap) {
    gsap.registerPlugin(ScrollTrigger);
  }

  setYear();
  setupSmoothNavigation();

  const navLinks = Array.from(document.querySelectorAll("[data-nav-link]"));
  const sections = Array.from(document.querySelectorAll("main section[id]"));
  const featureCards = Array.from(document.querySelectorAll("[data-feature-card]"));
  const heroOrb = document.querySelector(".hero-orb");

  if (hasGsap) {
    gsap.set(featureCards, { autoAlpha: 0, y: 22, scale: 0.97 });
  }

  if (hasGsap && !prefersReducedMotion) {
    runHeroEntrance(gsap);
    runScrollRevealSystem(gsap, ScrollTrigger);
    setupActiveSectionState(navLinks, sections, ScrollTrigger);
    if (heroOrb) {
      setupHeroParallax(heroOrb, gsap);
    }
  } else {
    featureCards.forEach((card) => card.classList.remove("visible"));
    setupActiveSectionFallback(navLinks, sections);
  }

  initializeThreeScenes({
    featureCards,
    animateCards: (isVisible) => {
      toggleFeatureCards(featureCards, isVisible, gsap, hasGsap);
    }
  });

  initializeParticleBackground();
}

function setYear() {
  const yearNode = document.getElementById("year");
  if (yearNode) {
    yearNode.textContent = String(new Date().getFullYear());
  }
}

function setupSmoothNavigation() {
  const navLinks = document.querySelectorAll(".nav-link");

  navLinks.forEach((link) => {
    link.addEventListener("click", (event) => {
      const target = event.currentTarget;
      const href = target.getAttribute("href");

      if (!href || !href.startsWith("#")) {
        return;
      }

      const section = document.querySelector(href);
      if (!section) {
        return;
      }

      event.preventDefault();
      section.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  });
}

function runHeroEntrance(gsap) {
  const timeline = gsap.timeline({ defaults: { ease: "power3.out" } });

  timeline
    .from(".hero-copy .eyebrow", { autoAlpha: 0, y: 24, duration: 0.55 })
    .from(".hero-copy h1", { autoAlpha: 0, y: 34, scale: 0.98, duration: 0.8 }, "-=0.2")
    .from(".hero-copy .tagline", { autoAlpha: 0, y: 24, duration: 0.64 }, "-=0.42")
    .from(".hero-copy .description", { autoAlpha: 0, y: 18, duration: 0.52 }, "-=0.34")
    .from(".hero-actions", { autoAlpha: 0, y: 14, duration: 0.5 }, "-=0.25")
    .from(".hero-orb-wrap", { autoAlpha: 0, y: 28, scale: 0.9, duration: 1.0 }, "-=0.72");
}

function runScrollRevealSystem(gsap, ScrollTrigger) {
  const revealItems = gsap.utils.toArray(".reveal");

  revealItems.forEach((item) => {
    gsap.from(item, {
      autoAlpha: 0,
      y: 42,
      scale: 0.985,
      duration: 0.85,
      ease: "power2.out",
      scrollTrigger: {
        trigger: item,
        start: "top 84%",
        toggleActions: "play none none none"
      }
    });
  });

  gsap.from(".doc-block", {
    autoAlpha: 0,
    y: 24,
    duration: 0.72,
    stagger: 0.1,
    ease: "power2.out",
    scrollTrigger: {
      trigger: ".docs-grid",
      start: "top 82%"
    }
  });

  ScrollTrigger.refresh();
}

function setupHeroParallax(heroOrb, gsap) {
  const section = document.getElementById("hero");
  if (!section) {
    return;
  }

  const rotateXTo = gsap.quickTo(heroOrb, "rotationX", { duration: 0.55, ease: "power2.out" });
  const rotateYTo = gsap.quickTo(heroOrb, "rotationY", { duration: 0.55, ease: "power2.out" });

  section.addEventListener("pointermove", (event) => {
    const bounds = section.getBoundingClientRect();
    const relativeX = (event.clientX - bounds.left) / bounds.width - 0.5;
    const relativeY = (event.clientY - bounds.top) / bounds.height - 0.5;

    rotateXTo(-relativeY * 9);
    rotateYTo(relativeX * 11);
  });

  section.addEventListener("pointerleave", () => {
    rotateXTo(0);
    rotateYTo(0);
  });
}

function setupActiveSectionState(navLinks, sections, ScrollTrigger) {
  sections.forEach((section) => {
    ScrollTrigger.create({
      trigger: section,
      start: "top 38%",
      end: "bottom 38%",
      onToggle: (self) => {
        if (self.isActive) {
          setActiveNav(navLinks, section.id);
        }
      }
    });
  });
}

function setupActiveSectionFallback(navLinks, sections) {
  if (!("IntersectionObserver" in window)) {
    return;
  }

  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          setActiveNav(navLinks, entry.target.id);
        }
      });
    },
    {
      threshold: 0.45
    }
  );

  sections.forEach((section) => observer.observe(section));
}

function setActiveNav(navLinks, sectionId) {
  navLinks.forEach((link) => {
    const targetId = link.getAttribute("data-nav-link");
    link.classList.toggle("active", targetId === sectionId);
  });
}

function initializeThreeScenes({ featureCards, animateCards }) {
  const showcaseCanvas = document.getElementById("showcase-canvas");
  const exploderCanvas = document.getElementById("exploder-canvas");
  const explodeButton = document.getElementById("explode-button");
  const initialized = new Set();

  const setupBySection = {
    showcase: () => {
      if (showcaseCanvas) {
        initShowcaseScene({ canvas: showcaseCanvas });
      }
    },
    features: () => {
      if (exploderCanvas) {
        initExploderScene({
          canvas: exploderCanvas,
          triggerButton: explodeButton,
          onToggle: animateCards
        });
      }
    }
  };

  const sectionNodes = {
    showcase: document.getElementById("showcase"),
    features: document.getElementById("features")
  };

  if (!("IntersectionObserver" in window)) {
    Object.keys(setupBySection).forEach((key) => {
      if (!initialized.has(key)) {
        setupBySection[key]();
        initialized.add(key);
      }
    });
    return;
  }

  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        const key = entry.target.id;
        if (entry.isIntersecting && !initialized.has(key)) {
          setupBySection[key]?.();
          initialized.add(key);
        }
      });
    },
    {
      rootMargin: "220px 0px"
    }
  );

  Object.values(sectionNodes).forEach((node) => {
    if (node) {
      observer.observe(node);
    }
  });

  featureCards.forEach((card) => card.classList.remove("visible"));
}

function toggleFeatureCards(featureCards, isVisible, gsap, hasGsap) {
  featureCards.forEach((card, index) => {
    card.classList.toggle("visible", isVisible);

    if (hasGsap && !prefersReducedMotion) {
      gsap.to(card, {
        autoAlpha: isVisible ? 1 : 0,
        y: isVisible ? 0 : 22,
        scale: isVisible ? 1 : 0.97,
        duration: 0.45,
        delay: isVisible ? index * 0.08 : 0,
        ease: "power2.out"
      });
    }
  });
}

function initializeParticleBackground() {
  const canvas = document.getElementById("particle-canvas");
  const context = canvas?.getContext("2d");

  if (!canvas || !context) {
    return;
  }

  const particleCount = prefersReducedMotion ? 20 : 48;
  const particles = [];
  let width = 0;
  let height = 0;

  function createParticle() {
    return {
      x: Math.random() * width,
      y: Math.random() * height,
      radius: 0.8 + Math.random() * 2.3,
      vx: (Math.random() - 0.5) * 0.25,
      vy: (Math.random() - 0.5) * 0.25
    };
  }

  function resetCanvasSize() {
    const ratio = Math.min(window.devicePixelRatio || 1, 1.5);
    width = window.innerWidth;
    height = window.innerHeight;

    canvas.width = Math.floor(width * ratio);
    canvas.height = Math.floor(height * ratio);
    canvas.style.width = `${width}px`;
    canvas.style.height = `${height}px`;

    context.setTransform(ratio, 0, 0, ratio, 0, 0);
  }

  function draw() {
    context.clearRect(0, 0, width, height);

    particles.forEach((particle) => {
      particle.x += particle.vx;
      particle.y += particle.vy;

      if (particle.x > width + 8) {
        particle.x = -8;
      } else if (particle.x < -8) {
        particle.x = width + 8;
      }

      if (particle.y > height + 8) {
        particle.y = -8;
      } else if (particle.y < -8) {
        particle.y = height + 8;
      }

      context.beginPath();
      context.arc(particle.x, particle.y, particle.radius, 0, Math.PI * 2);
      context.fillStyle = "rgba(130, 214, 207, 0.35)";
      context.fill();
    });

    for (let i = 0; i < particles.length; i += 1) {
      for (let j = i + 1; j < particles.length; j += 1) {
        const first = particles[i];
        const second = particles[j];
        const dx = first.x - second.x;
        const dy = first.y - second.y;
        const distance = Math.hypot(dx, dy);

        if (distance < 130) {
          const alpha = (1 - distance / 130) * 0.12;
          context.beginPath();
          context.moveTo(first.x, first.y);
          context.lineTo(second.x, second.y);
          context.strokeStyle = `rgba(126, 207, 200, ${alpha})`;
          context.lineWidth = 1;
          context.stroke();
        }
      }
    }

    if (!prefersReducedMotion) {
      window.requestAnimationFrame(draw);
    }
  }

  resetCanvasSize();
  particles.length = 0;

  for (let i = 0; i < particleCount; i += 1) {
    particles.push(createParticle());
  }

  draw();

  window.addEventListener("resize", () => {
    resetCanvasSize();
  });
}
