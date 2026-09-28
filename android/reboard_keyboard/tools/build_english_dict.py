"""Builds the English dictionary (ime/dict/data.json + offensive.json).

Usage: python tools/build_english_dict.py

Base: AOSP LatinIME en_US + en_GB + en wordlists (Apache-2.0).
Plus modern/slang words from the legacy list (typos, apostrophe-less
contractions and foreign fragments filtered out), Indian-English and
Hinglish vocabulary. Frequencies use AOSP's log scale (0..255).
"""
import gzip, json, re, collections, statistics, sys
import os
HERE = os.path.dirname(os.path.abspath(__file__))
# AOSP LatinIME wordlists (Apache-2.0), vendored with the native engine.
AOSP = os.environ.get("AOSP_DICT_DIR", os.path.join(HERE, "..", "src", "main", "cpp", "aosp", "dictionaries")) + os.sep
# The pre-3.1 50k web wordlist; its modern/slang words are merged in.
DATA = os.environ.get("LEGACY_WORDLIST", os.path.join(HERE, "legacy_wordlist.json"))

def load(fn):
    out = {}
    for l in gzip.open(AOSP + fn, 'rt', encoding='utf-8'):
        s = l.strip()
        if not s.startswith('word='):
            continue
        parts = dict(p.split('=', 1) for p in s.split(',') if '=' in p)
        if parts.get('not_a_word') == 'true':
            continue
        w = parts['word']; f = int(parts['f'])
        off = parts.get('possibly_offensive') == 'true' or 'offensive' in parts.get('flags', '')
        prev = out.get(w)
        if prev is None or f > prev[0]:
            out[w] = (f, off or (prev[1] if prev else False))
    return out

union = {}
for fn in ('en_US_wordlist.combined.gz', 'en_GB_wordlist.combined.gz', 'en_wordlist.combined.gz'):
    for w, (f, off) in load(fn).items():
        p = union.get(w)
        if p is None or f > p[0]:
            union[w] = (f, off or (p[1] if p else False))

lower_index = collections.defaultdict(list)
for w in union:
    lower_index[w.lower()].append(w)

data = json.load(open(DATA, encoding='utf-8'))

# Frequency mapping data.json scale -> AOSP scale via overlapping lowercase words.
pairs = collections.defaultdict(list)
for w, f in data.items():
    if w in union and union[w][0] > 0:
        pairs[f].append(union[w][0])
mapping = {}
for f in range(0, 256):
    near = []
    for d in range(0, 6):
        near += pairs.get(f - d, []) + (pairs.get(f + d, []) if d else [])
        if len(near) >= 25:
            break
    mapping[f] = int(statistics.median(near)) if near else 0
# enforce monotonic
m = 0
for f in range(256):
    m = max(m, mapping[f]); mapping[f] = m

SLANG = set('''u ur ya yea yeah yup yep nah nope ok okk okay k lol lmao lmfao rofl omg omfg idk idc tbh btw brb bbl ikr imo imho smh ngl fr rn tho thx ty tysm np pls plz bruh bro sis fam gonna wanna gotta kinda sorta dunno lemme gimme haha hahaha hahahaha hehe hmm hmmm ugh omw wyd hbu wbu jk ttyl gg xd irl dm dms asap fyi diy aka rip wtf af til tldr bday bf gf bff ily ilysm lit sus vibe vibes vibing selfie selfies covid emoji emojis meme memes app apps wifi hashtag blog blogger vlog vlogger podcast podcasts youtuber youtube instagram tiktok whatsapp snapchat facebook twitter spotify netflix uber gif gifs pic pics vid vids yall ain't y'all cuz coz cos luv tmrw tmr tmrw nite ppl bc b4 gr8 hw txt msg msgs mins sec secs hr hrs kg km cm mm ft lb lbs yo sup wassup whatsup hiya heya hey heyy heyyy hii hiii byee okayy yayy yay woah whoa wow oops oof ouch aww awww nvm goin doin nothin somethin gst upi otp emi atm aadhaar aadhar paytm phonepe gpay zomato swiggy flipkart ola rapido blinkit zepto'''.split())

MISSPELLINGS = set('''teh taht hte adn nad waht jsut becasue becuase beacuse thier freind freinds wierd recieve recieved seperate seperately definately definatly definetly untill beleive belive tommorow tomorow tommorrow goverment enviroment begining neccessary necesary occurence occured occuring accomodate acheive adress agressive apparantly arguement basicly calender cemetary collegue comming commited concious curiousity decieve dissapear dissapoint embarass existance familar finaly foriegn gaurd happend harrass immediatly independant knowlege liason libary lisence maintainance millenium mispell neice noticable occassion persue posession prefered priviledge publically realy reccomend recomend refered relevent religous remeber resistence responsability rythm sieze similiar sincerly speach strenght succesful supercede suprise tatoo tendancy therefor threshhold tounge truely unfortunatly usefull vaccum vegatable wich withold writting alot atleast infact aswell eachother everytime alright'''.split()) - {'alright'}

HINGLISH = set('''hai hain nahi nahin nhi kya kyu kyun kyon kaise kaisa kaisi acha accha achha acha theek thik haan haa bhai bhaiya yaar yaara bhi toh mein mai mera meri mere tera teri tere tum aap hum ham kar karo karna karke kr kro krna krke raha rahi rahe rha rhi rhe hoga hogi hoge gaya gayi gaye gya gyi chal chalo chalega chalte abhi kal aaj bahut bohot bht sab kuch kuchh kab kaha kahan kidhar idhar udhar woh voh yeh jab tab phir fir lekin aur matlab samajh samjha samjhe pata pta bol bolo bola boli bata batao bataya dekh dekho dekha sun suno suna khana pani ghar kaam paise paisa dost bas bilkul zaroor jaldi arre arey abe oye sahi galat pyaar pyar dil jaan shukriya dhanyavad namaste ji naa mat sakta sakti sakte chahiye chahta chahti wala wali wale apna apni apne uska uski unka unki iska iski kaun kitna kitni kitne thoda thodi zyada jyada pehle baad saath sath liye tak hua hui hue kiya kiye diya liya dena lena jana aana aaya aayi dijiye kijiye bhej bhejo milte milenge mila mili shayad didi beta maa mummy pagal bakwas mast badhiya bekar timepass jugaad chai roti sabzi haina hainaa kyunki isliye waise vaise aisa aisi aise jaisa jaisi jaise kahi kabhi sabhi koi kisi kiska kiski hoon hun hu tha thi the rahega rahegi karunga karungi karega karegi dunga dungi lunga lungi jaunga jaungi aaunga aaungi bolna bolta bolti sochna socha sochta sochti samay din raat subah shaam pehla dusra teesra ek do teen char paanch ho ki ke ka ko ne se tu ab ye wo ha kr mt bhaiyya accha haanji nahiii kyaa acchaa theekhai'''.split())

FRAGMENTS = set('''don doesn didn isn wasn couldn wouldn shouldn aren haven hasn weren mustn needn ll ve re des les der von und que sur para sen ing del los las una por con est mit ist nicht ich sie wir aus bei auf zum zur dem den het een van voor niet ook elle avec dans pour sont mais tres tout nous vous sich uno dos tres il la le du au et en di da na ne pas une ses nel della dei degli sono www http https com org net html'''.split())

POSS_OK = re.compile(r"(n't|'m|'re|'ve|'ll|'d)$")
PRON_S = set("he she it that what who where here there let how when why this".split())
def is_contraction(apos_word):
    lw = apos_word.lower()
    if POSS_OK.search(lw):
        return True
    if lw.endswith("'s") and lw[:-2] in PRON_S:
        return True
    return lw in ("y'all", "ma'am", "ain't", "c'mon")

# Legacy words that the AOSP decoder confidently "corrects" to a much more
# common word 1-2 edits away (found with the host-side decoder harness).
# Mostly foreign fragments and misspellings; left in the dictionary they trap
# typos ("stadt" for "start", "morne" for "more") so they never get fixed.
# Legitimate modern words from the same scan are intentionally NOT listed
# (creatine, fanfic, merch, patreon, spacex, weeknd, raina, gopal, ...).
DECODER_FLAGGED = set("""accidently actin albus alles appel argus armes bader bains ballon bloor boateng boone
bowles brics buller carrera carrey castel centro chambre chiral cleary comey compagnie corse cortez costed
coutts coyle crowe curtin danvers descendents downton fatih fondation fonte generale gente grohl groupe hamer
hanes harkness hazzard holliday homme hommes instituto internacional killa larue lauer lazer lettres liber
linde malin markey massie matic maximus mayne morne musik nacional nationale nazionale neves orchestre parte
persson populus porta presidente sakurai scotus seater stadion stadt starz stott streep suprised tasker testa
unger unser warne watters wellcome grammer embarassing""".split())

def strip_apos(w):
    return w.replace("'", "").replace("\u2019", "").lower()

apos_forms = collections.defaultdict(list)
for w in union:
    if "'" in w:
        apos_forms[strip_apos(w)].append(w)

# Words from AOSP with f >= 100 used for typo-neighbour filtering (lowercase)
common = {w.lower(): f for w, (f, off) in union.items() if f >= 100}

ALPH = 'abcdefghijklmnopqrstuvwxyz'
def edits1(w):
    splits = [(w[:i], w[i:]) for i in range(len(w) + 1)]
    deletes = [a + b[1:] for a, b in splits if b]
    trans = [a + b[1] + b[0] + b[2:] for a, b in splits if len(b) > 1]
    reps = [a + c + b[1:] for a, b in splits if b for c in ALPH]
    ins = [a + c + b for a, b in splits for c in ALPH]
    return set(deletes), set(trans), set(reps), set(ins)

out = {}
offensive = set()
for w, (f, off) in union.items():
    out[w] = f
    if off or f == 0:
        offensive.add(w)

added, dropped = [], collections.Counter()
# Legacy words that are not force-kept; candidates for the decoder-based
# misspelling check (see tools/decoder_flagged_misspellings.txt).
vettable = []
drop_samples = collections.defaultdict(list)
for w, f in data.items():
    lw = w.lower()
    if lw in lower_index:
        continue
    reason = None
    if not re.fullmatch(r"[a-z][a-z']*[a-z]|[a-z]", w):
        reason = 'charset'
    elif w in MISSPELLINGS or w in DECODER_FLAGGED:
        reason = 'misspelling'
    elif w in FRAGMENTS and w not in SLANG and w not in HINGLISH:
        reason = 'fragment'
    elif w in SLANG or w in HINGLISH:
        reason = None
    elif "'" not in w and any(is_contraction(x) for x in apos_forms.get(strip_apos(w), [])):
        reason = 'contraction'
    elif len(w) <= 2:
        reason = 'short'
    elif any(common.get(w[:k], 0) >= 165 and common.get(w[k:], 0) >= 165 for k in range(2, len(w) - 1)):
        # run-together words from web text: "ofthe", "inthe", "tothe"
        reason = 'concatenation'
    elif (len(w) == 3 and f < 200) or (len(w) == 4 and f < 170):
        reason = 'short_rare'
    else:
        d, t, r, i = edits1(w)
        # transposition of a very common word -> almost certainly a typo
        if any(common.get(x, 0) >= 150 for x in t):
            reason = 'transposition'
        elif f < 190 and any(common.get(x, 0) >= 170 for x in (d | r | i)):
            reason = 'edit1_of_very_common'
    if reason:
        dropped[reason] += 1
        if len(drop_samples[reason]) < 60:
            drop_samples[reason].append((w, f))
        continue
    if w in HINGLISH and w not in SLANG:
        nf = 40 if len(w) <= 2 else 55
    else:
        nf = max(1, mapping.get(f, 0) - (4 if w in SLANG else 10))
    out[w] = nf
    added.append((w, f, nf))
    if w not in SLANG and w not in HINGLISH:
        vettable.append(w)

# Indian-English vocabulary (food, family, everyday words) and proper nouns.
INDIAN_ENGLISH = """paneer biryani samosa samosas dosa dosas idli idlis vada chapati chapatis naan tikka lassi
puja mehndi kurta kurtas dupatta autorickshaw dhaba paratha parathas pakora pakoras raita paani gulab jamun
halwa ladoo laddoo jalebi rasgulla kheer bhaji vadapav pav bhel chaat golgappa panipuri kulfi thali aarti
rakhi yatra behen bhabhi chacha chachi mami nani dadi sambar rasam upma poha khichdi rajma chole bhature
pulao keema kebab kebabs tandoor masala ghee dal roti sabzi lakh lakhs crore crores rupee rupees""".split()
INDIAN_PROPER = """Holi Navratri Dussehra Onam Pongal Sankranti Lohri Ganpati Diwali Eid Raksha Bandhan Karva Chauth
Tollywood Kollywood IPL Aadhaar Paytm PhonePe Swiggy Zomato Flipkart Bengaluru Mumbai Chennai Kolkata
Hyderabad Pune Delhi Noida Gurugram Kerala Karnataka Telangana Maharashtra""".split()
for w in INDIAN_ENGLISH:
    if w in out or w.lower() in lower_index:
        continue
    out[w] = 70
    added.append((w, None, 70))
for w in INDIAN_PROPER:
    if w in out:
        continue
    out[w] = 90
    added.append((w, None, 90))

# Abbreviations typed before a period ("Smt.", "Univ.", "B.Sc" typed as
# "BSc"). Unknown words get autocorrected, so without these "Sr." becomes
# "St." Only forms that are not a likely typo of a common word.
ABBREVIATIONS = """Smt Sr Univ yr yrs Rd Estd Engg Addl Insp Treas Admn Engr BTech MTech BSc BCom MCom""".split()
for w in ABBREVIATIONS:
    if w in out or w.lower() in lower_index:
        continue
    out[w] = 60
    added.append((w, None, 60))

# Hinglish vocabulary: valid words (never autocorrected away) at a low
# frequency so they rarely outrank English suggestions.
for w in HINGLISH:
    if w in out or w.lower() in lower_index:
        continue
    out[w] = 55
    added.append((w, None, 55))

# Lowercase variants for chat abbreviations that AOSP only lists in caps.
for w in SLANG:
    if w in out:
        continue
    if w in data:
        out[w] = max(1, mapping[data[w]] - 4)
        added.append((w, data[w], out[w]))
    elif w.lower() in lower_index:
        # e.g. ok -> OK exists; add lowercase variant with the same frequency
        base = max(out[x] for x in lower_index[w.lower()])
        out[w] = base
        added.append((w, None, base))
    else:
        out[w] = 60
        added.append((w, None, 60))

print('union', len(union), 'final', len(out), 'added', len(added), 'dropped', dict(dropped), file=sys.stderr)
for k, v in drop_samples.items():
    print(k, v, file=sys.stderr)
print('added sample', sorted(added, key=lambda x: -(x[1] or 0))[:120], file=sys.stderr)
print('mapping', {k: mapping[k] for k in range(120, 256, 8)}, file=sys.stderr)
if os.environ.get('WRITE_VETTABLE'):
    with open(os.environ['WRITE_VETTABLE'], 'w', encoding='utf-8') as fh:
        for word in vettable:
            fh.write(word + '\n')

OUT = os.environ.get('OUT_DIR', os.path.join(HERE, '..', 'src', 'main', 'assets', 'ime', 'dict'))
json.dump(out, open(os.path.join(OUT, 'data.json'), 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'), sort_keys=False)
json.dump(sorted(offensive), open(os.path.join(OUT, 'offensive.json'), 'w', encoding='utf-8'), ensure_ascii=False)
