/* Sub-page interactions: floating pill nav + reveal fallback */
(function(){
  const navdock = document.querySelector('.navdock');
  let lastY = 0;
  addEventListener('scroll', ()=>{
    const y = scrollY;
    if (navdock){ if (y > 240 && y > lastY) navdock.classList.add('hide'); else navdock.classList.remove('hide'); }
    lastY = y;
  }, { passive: true });

  const glow = document.getElementById('pillGlow');
  const pill = document.querySelector('.pillnav');
  const links = [...document.querySelectorAll('.pillnav .pl')];
  function move(l){ if(!glow) return; glow.style.opacity='1'; glow.style.left=l.offsetLeft+'px'; glow.style.width=l.offsetWidth+'px'; }
  links.forEach(l=>l.addEventListener('mouseenter', ()=>{ l.classList.add('active'); move(l); }));
  links.forEach(l=>l.addEventListener('mouseleave', ()=>l.classList.remove('active')));
  if (pill) pill.addEventListener('mouseleave', ()=>{ if(glow) glow.style.opacity='0'; });

  const els = [...document.querySelectorAll('.reveal')];
  const io = new IntersectionObserver((es)=>es.forEach(e=>{ if(e.isIntersecting){ e.target.classList.add('in'); io.unobserve(e.target); } }), { threshold:0.1, rootMargin:'0px 0px -40px 0px' });
  els.forEach(e=>io.observe(e));
  requestAnimationFrame(()=>requestAnimationFrame(()=>els.forEach(e=>{ if(e.getBoundingClientRect().top < innerHeight*1.05) e.classList.add('in'); })));
  setTimeout(()=>els.forEach(e=>{ e.style.transition='none'; e.style.opacity='1'; e.style.transform='none'; e.classList.add('in'); }), 1200);
})();
