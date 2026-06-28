/* ════════════════════════════════════════════════════════════════
   REWORDIUM — interactions
   ════════════════════════════════════════════════════════════════ */

/* ─── 1. Live persona morph demo ───────────────────────────────── */
const PERSONAS = {
  professional: { label: 'Professional', color: '#2563EB', text: "Would you be able to share the project deck before end of day?" },
  casual:       { label: 'Casual',       color: '#FB8C2A', text: "hey, mind sending over the deck whenever you get a sec? 🙌" },
  autopilot:    { label: 'Autopilot',    color: '#22C55E', text: "Sure! The deck is on its way — you'll have it before 5PM." },
  confident:    { label: 'Confident',    color: '#7C5CFC', text: "Send the deck across by end of day — I'll take it from there." },
  genz:         { label: 'Gen-Z',        color: '#EC4899', text: "yo drop the deck when ur free, no rush fr 🫶" },
  poetic:       { label: 'Poetic',       color: '#0EA5E9', text: "Might the deck drift my way before the day takes its leave?" },
};

const ORDER = ['professional','casual','autopilot','confident','genz','poetic'];

const personaRow = document.getElementById('persona-row');
const outText    = document.getElementById('out-text');
const outBadge   = document.getElementById('out-badge');
const outBadgeTxt= document.getElementById('out-badge-text');
const demoInput  = document.getElementById('demo-input');

let current = 'professional';
let typer = null;

function morph(target){
  if (typer) clearTimeout(typer);
  const start = outText.textContent;
  let common = 0;
  while (common < start.length && common < target.length && start[common] === target[common]) common++;
  let i = start.length;
  let j = common;
  function type(){
    if (j < target.length){ outText.textContent = target.slice(0, ++j); typer = setTimeout(type, 20); }
  }
  function erase(){
    if (i > common){ outText.textContent = start.slice(0, --i); typer = setTimeout(erase, 9); }
    else type();
  }
  erase();
}

function transform(raw, id){
  const o = (raw||'').trim();
  // canonical demo sentence → curated rewrites
  if (!o || /deck/i.test(o) && /eod|end of day|send/i.test(o)) return PERSONAS[id].text;
  switch(id){
    case 'professional': return o.replace(/\bcan you\b/gi,'would you be able to').replace(/\bu\b/gi,'you').replace(/\bplz\b/gi,'please').replace(/\basap\b/gi,'at your earliest convenience');
    case 'casual':       return o.toLowerCase().replace(/[.?!]+$/,'') + ' — no rush! 🙌';
    case 'autopilot':    return "Sure thing — on it. " + o.replace(/\?+$/,'') + ".";
    case 'confident':    return o.replace(/\bi think\b/gi,'').replace(/\bmaybe\b/gi,'').replace(/\bjust\b/gi,'').trim().replace(/^./, c=>c.toUpperCase()) + ".";
    case 'genz':         return o.toLowerCase().replace(/[.?!]+$/,'') + ' fr no cap 🫶';
    case 'poetic':       return "O, " + o.toLowerCase().replace(/[.?!]+$/,'') + ", if thou wouldst.";
    default: return o;
  }
}

function setPersona(id){
  current = id;
  const p = PERSONAS[id];
  [...personaRow.children].forEach(b=>{
    const on = b.dataset.id === id;
    b.classList.toggle('active', on);
    b.style.background = on ? p.color : '';
    b.style.borderColor = on ? p.color : '';
  });
  outBadge.style.background = p.color;
  outBadgeTxt.textContent = p.label;
  morph(transform(demoInput.textContent, id));
}

personaRow.addEventListener('click', e=>{
  const b = e.target.closest('.persona'); if(!b) return;
  stopAuto(); setPersona(b.dataset.id);
});
let inDeb;
demoInput.addEventListener('input', ()=>{ clearTimeout(inDeb); inDeb = setTimeout(()=>setPersona(current), 260); });

// auto-rotate until user interacts
let autoIdx = 0;
let auto = setInterval(()=>{ autoIdx = (autoIdx+1)%ORDER.length; setPersona(ORDER[autoIdx]); }, 3200);
function stopAuto(){ if(auto){ clearInterval(auto); auto=null; } }
setPersona('professional');

/* ─── 2. Phone AI-card tone dropdown + auto cycle ──────────────── */
const phoneTones = [
  { label:'Casual',       color:'#3B82F6', text:"Sure! Here are the notes from today's meeting." },
  { label:'Professional', color:'#3B82F6', text:"Please find the notes from today's meeting attached below." },
  { label:'Friendly',     color:'#22C55E', text:"Hey! Just sent over the notes from today — let me know if you need more 🙂" },
  { label:'Confident',    color:'#8B5CF6', text:"Notes from today's meeting are ready. Reach out with questions." },
];
const aiDDtext = document.getElementById('ai-dd-text');
const aiBodyTxt= document.getElementById('ai-body-text');
let phoneIdx = 0;
const aiDD = document.getElementById('ai-dd');
let phoneAuto = setInterval(cyclePhone, 3600);
function cyclePhone(){
  phoneIdx = (phoneIdx+1)%phoneTones.length;
  const t = phoneTones[phoneIdx];
  aiDDtext.textContent = t.label;
  aiBodyTxt.style.opacity = 0;
  setTimeout(()=>{ aiBodyTxt.textContent = t.text; aiBodyTxt.style.opacity = 1; }, 280);
}
aiDD.addEventListener('click', ()=>{
  aiDD.classList.toggle('open');
  cyclePhone();
});
// Generate button micro-interaction
const aiGen = document.getElementById('ai-gen');
if (aiGen) aiGen.addEventListener('click', ()=>{
  aiGen.style.transform = 'scale(.96)';
  setTimeout(()=>aiGen.style.transform='', 130);
  cyclePhone();
});

/* ─── 3. Tone knob in bento ────────────────────────────────────── */
const knob = document.getElementById('knob-thumb');
const knobLabels = document.querySelectorAll('#knob-labels span');
const knobPos = [8, 50, 92, 50];
let kIdx = 0;
if (knob) setInterval(()=>{
  kIdx = (kIdx+1)%knobPos.length;
  knob.style.left = knobPos[kIdx] + '%';
  const map = [0,1,2,1];
  knobLabels.forEach((s,i)=>s.classList.toggle('on', i===map[kIdx]));
}, 1700);

/* ─── 4. Scroll reveal (with guaranteed-visible fallback) ───────── */
function revealNow(el){ el.classList.add('in'); }
const io = new IntersectionObserver((entries)=>{
  entries.forEach(en=>{ if(en.isIntersecting){ revealNow(en.target); io.unobserve(en.target); } });
}, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });
const revealEls = [...document.querySelectorAll('.reveal')];
revealEls.forEach(el=>io.observe(el));
// Reveal anything in/near the first viewport once layout settles (covers no-IO contexts)
function revealInView(){
  revealEls.forEach(el=>{
    if (el.classList.contains('in')) return;
    if (el.getBoundingClientRect().top < window.innerHeight * 1.05) revealNow(el);
  });
}
requestAnimationFrame(()=>requestAnimationFrame(revealInView));
// Hard safety net: force final visible state instantly (transitions can freeze in
// non-painting/offscreen contexts, so jump straight to the resolved style)
setTimeout(()=>revealEls.forEach(el=>{
  el.style.transition = 'none';
  el.style.opacity = '1';
  el.style.transform = 'none';
  el.classList.add('in');
}), 1300);

/* ─── 5. Floating pill nav: hide on scroll-down + sliding active glow ─── */
const navdock = document.querySelector('.navdock');
const pillnav = document.getElementById('pillnav');
const pillGlow = document.getElementById('pillGlow');
const pillLinks = [...document.querySelectorAll('.pillnav .pl')];

function moveGlow(link){
  if (!link || !pillGlow) return;
  pillGlow.style.opacity = '1';
  pillGlow.style.left = link.offsetLeft + 'px';
  pillGlow.style.width = link.offsetWidth + 'px';
}
function setActive(sec){
  let match = null;
  pillLinks.forEach(l=>{
    const on = l.dataset.sec === sec;
    l.classList.toggle('active', on);
    if (on) match = l;
  });
  moveGlow(match);
}
// track which section is in view
const secIds = ['features','demo','tools','privacy'];
const secEls = secIds.map(id=>document.getElementById(id)).filter(Boolean);
const navIO = new IntersectionObserver((entries)=>{
  entries.forEach(en=>{ if(en.isIntersecting) setActive(en.target.id); });
}, { rootMargin: '-45% 0px -50% 0px', threshold: 0 });
secEls.forEach(el=>navIO.observe(el));
// smooth-scroll + immediate highlight on click
pillLinks.forEach(l=>l.addEventListener('click', ()=>setActive(l.dataset.sec)));
// keep glow aligned on resize
window.addEventListener('resize', ()=>{ const a = pillnav.querySelector('.pl.active'); if(a) moveGlow(a); });
// initial active state
requestAnimationFrame(()=>setActive('features'));

// hide dock on scroll-down, show on scroll-up
let lastY = 0;
window.addEventListener('scroll', ()=>{
  const y = window.scrollY;
  if (y > 240 && y > lastY) navdock.classList.add('hide');
  else navdock.classList.remove('hide');
  lastY = y;
}, { passive: true });

/* ─── 5b. Seamless marquee — duplicate each row's content ─── */
document.querySelectorAll('.marquee-track[data-row]').forEach(track=>{
  track.innerHTML += track.innerHTML;
});

/* ─── 6. Button press ripple-free pop ──────────────────────────── */
document.querySelectorAll('.btn').forEach(b=>{
  b.addEventListener('pointerdown', ()=>{ b.style.transform='translateY(1px) scale(.98)'; });
  b.addEventListener('pointerup',   ()=>{ b.style.transform=''; });
  b.addEventListener('pointerleave',()=>{ b.style.transform=''; });
});
