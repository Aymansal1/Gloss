#!/usr/bin/env python3
"""
Builds app/src/main/assets/japanese.db, the dictionary behind Gloss's Learn features.

Sources (all CC BY-SA, credited in the app and README):
  - JMdict, JMnedict and KANJIDIC2 from the Electronic Dictionary Research and Development Group,
    as packaged in SQLite by the jamdict-data project on PyPI.
  - Stroke order from KanjiVG (https://kanjivg.tagaini.net).

Usage: japanese_pack.py <jamdict.db> <kanjivg checkout> <out.db>
"""
import os
import re
import sqlite3
import sys
from collections import defaultdict

src_path, kvg_dir, out_path = sys.argv[1:4]
src = sqlite3.connect(src_path)

# Priority tags that JMdict uses to mark everyday words.
COMMON = {"news1", "ichi1", "spec1", "spec2", "gai1"}

def rank_of(tags):
    """Lower is more common: nfXX bands first, then the other priority lists."""
    best = 99
    for t in tags:
        if t.startswith("nf"):
            best = min(best, int(t[2:]))
        elif t in COMMON:
            best = min(best, 50)
    return best

kanji_forms = defaultdict(list)   # idseq -> [(text, tags)]
for kid, idseq, text in src.execute("SELECT ID, idseq, text FROM Kanji ORDER BY ID"):
    kanji_forms[idseq].append([text, []])
kanji_by_id = {}
for kid, idseq, text in src.execute("SELECT ID, idseq, text FROM Kanji"):
    kanji_by_id[kid] = (idseq, text)
kjp = defaultdict(list)
for kid, text in src.execute("SELECT kid, text FROM KJP"):
    kjp[kid].append(text)

kana_forms = defaultdict(list)
for kid, idseq, text, nokanji in src.execute("SELECT ID, idseq, text, nokanji FROM Kana ORDER BY ID"):
    kana_forms[idseq].append((kid, text))
knp = defaultdict(list)
for kid, text in src.execute("SELECT kid, text FROM KNP"):
    knp[kid].append(text)

kanji_tags = defaultdict(list)
for kid, (idseq, text) in kanji_by_id.items():
    kanji_tags[idseq].append((kid, text))

senses = defaultdict(list)  # idseq -> [sid]
for sid, idseq in src.execute("SELECT ID, idseq FROM Sense ORDER BY ID"):
    senses[idseq].append(sid)
pos = defaultdict(list)
for sid, text in src.execute("SELECT sid, text FROM pos"):
    pos[sid].append(text)
misc = defaultdict(list)
for sid, text in src.execute("SELECT sid, text FROM misc"):
    misc[sid].append(text)
gloss = defaultdict(list)
for sid, lang, text in src.execute("SELECT sid, lang, text FROM SenseGloss"):
    if lang in (None, "", "eng"):
        gloss[sid].append(text)

# JMdict spells parts of speech out in full ("Ichidan verb"); keep short codes the app maps.
POS_CODES = [
    ("Ichidan verb - zuru", "vz"), ("Ichidan verb - kureru", "v1-s"), ("Ichidan verb", "v1"),
    ("Godan verb - Iku/Yuku", "v5k-s"), ("Godan verb - -aru", "v5aru"),
    ("Godan verb with 'u' ending (special", "v5u-s"), ("Godan verb with 'u' ending", "v5u"),
    ("Godan verb with 'ku' ending", "v5k"), ("Godan verb with 'gu' ending", "v5g"),
    ("Godan verb with 'su' ending", "v5s"), ("Godan verb with 'tsu' ending", "v5t"),
    ("Godan verb with 'nu' ending", "v5n"), ("Godan verb with 'bu' ending", "v5b"),
    ("Godan verb with 'mu' ending", "v5m"), ("Godan verb with 'ru' ending (irregular", "v5r-i"),
    ("Godan verb with 'ru' ending", "v5r"), ("Kuru verb", "vk"),
    ("suru verb - irregular", "vs-i"), ("suru verb - included", "vs-i"), ("suru verb - special", "vs-s"),
    ("noun or participle which takes the aux. verb suru", "vs"),
    ("adjective (keiyoushi) - yoi/ii", "adj-ix"), ("adjective (keiyoushi)", "adj-i"),
    ("adjectival nouns or quasi-adjectives", "adj-na"), ("pre-noun adjectival", "adj-pn"),
    ("nouns which may take the genitive", "adj-no"), ("noun (common)", "n"),
    ("adverb taking the 'to'", "adv-to"), ("adverb (fukushi)", "adv"), ("particle", "prt"),
    ("auxiliary verb", "aux-v"), ("auxiliary adjective", "aux-adj"), ("auxiliary", "aux"),
    ("copula", "cop"), ("conjunction", "conj"), ("interjection", "int"), ("pronoun", "pn"),
    ("prefix", "pref"), ("noun, used as a prefix", "n-pref"), ("suffix", "suf"),
    ("noun, used as a suffix", "n-suf"), ("counter", "ctr"), ("expressions", "exp"),
    ("numeric", "num"), ("transitive verb", "vt"), ("intransitive verb", "vi"),
]

def pos_code(text):
    for full, code in POS_CODES:
        if text.startswith(full):
            return code
    return None

out_tmp = out_path + ".tmp"
if os.path.exists(out_tmp):
    os.remove(out_tmp)
out = sqlite3.connect(out_tmp)
out.executescript("""
PRAGMA page_size = 4096;
CREATE TABLE entry (id INTEGER PRIMARY KEY, kanji TEXT NOT NULL, kana TEXT NOT NULL, senses TEXT NOT NULL, rank INTEGER NOT NULL);
CREATE TABLE form (form TEXT NOT NULL, rank INTEGER NOT NULL, id INTEGER NOT NULL, PRIMARY KEY (form, rank, id)) WITHOUT ROWID;
CREATE TABLE kanji (ch TEXT PRIMARY KEY, strokes INTEGER, grade INTEGER, jlpt INTEGER, freq INTEGER, onyomi TEXT, kunyomi TEXT, meanings TEXT, paths TEXT) WITHOUT ROWID;
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT) WITHOUT ROWID;
""")

n = 0
for (idseq,) in src.execute("SELECT idseq FROM Entry ORDER BY idseq"):
    ks = [(kid, t) for kid, t in kanji_tags.get(idseq, [])]
    ns = kana_forms.get(idseq, [])
    if not ns:
        continue
    sense_lines = []
    last_pos = ""
    for sid in senses.get(idseq, []):
        g = gloss.get(sid)
        if not g:
            continue
        codes = [c for c in (pos_code(p) for p in pos.get(sid, [])) if c]
        p = ",".join(dict.fromkeys(codes)) or last_pos
        last_pos = p
        notes = []
        for m in misc.get(sid, []):
            if "usually written using kana" in m:
                notes.append("uk")
            elif m.startswith("abbreviation"):
                notes.append("abbr")
            elif "slang" in m:
                notes.append("sl")
            elif m.startswith("onomatopoeic"):
                notes.append("on-mim")
        sense_lines.append(p + "\t" + ",".join(notes) + "\t" + "; ".join(g))
    if not sense_lines:
        continue
    rank = min([rank_of(kjp.get(kid, [])) for kid, _ in ks] + [rank_of(knp.get(kid, [])) for kid, _ in ns] + [99])
    out.execute("INSERT INTO entry VALUES (?,?,?,?,?)", (
        idseq, "|".join(t for _, t in ks), "|".join(t for _, t in ns), "\n".join(sense_lines), rank,
    ))
    seen = set()
    for kid, t in ks:
        if t not in seen:
            seen.add(t)
            out.execute("INSERT OR IGNORE INTO form VALUES (?,?,?)", (t, min(rank_of(kjp.get(kid, [])), rank), idseq))
    for kid, t in ns:
        if t not in seen:
            seen.add(t)
            out.execute("INSERT OR IGNORE INTO form VALUES (?,?,?)", (t, min(rank_of(knp.get(kid, [])), rank), idseq))
    n += 1

# JMnedict: people's names, so 萩原 reads as a surname rather than "reedy field". Places, companies
# and the like stay out; they're rarely what a passage means and would double the file. One row per
# spelling holds every reading of it, as lines of "reading\ttypes\tgloss". The gloss of a
# plain surname or given name is just its romaji, which the app works out itself, so only real
# people (宮本茂: "Shigeru Miyamoto (1952.11.16-), game designer") keep theirs.
NAME_TYPES = {"surname": "s", "given": "g", "fem": "f", "masc": "m", "person": "p", "place": "pl"}
PERSON = {"s", "g", "f", "m", "p"}
out.execute("CREATE TABLE name (form TEXT PRIMARY KEY, readings TEXT NOT NULL) WITHOUT ROWID")
ne_kanji = defaultdict(list)
for idseq, text in src.execute("SELECT idseq, text FROM NEKanji ORDER BY ID"):
    ne_kanji[idseq].append(text)
ne_kana = defaultdict(list)
for idseq, text in src.execute("SELECT idseq, text FROM NEKana ORDER BY ID"):
    ne_kana[idseq].append(text)
ne_types = defaultdict(list)
ne_gloss = defaultdict(list)
for idseq, kind in src.execute("SELECT t.idseq, y.text FROM NETranslation t JOIN NETransType y ON y.tid = t.ID"):
    if kind in NAME_TYPES and NAME_TYPES[kind] not in ne_types[idseq]:
        ne_types[idseq].append(NAME_TYPES[kind])
for idseq, lang, text in src.execute("SELECT t.idseq, g.lang, g.text FROM NETranslation t JOIN NETransGloss g ON g.tid = t.ID"):
    if lang in (None, "", "eng") and text not in ne_gloss[idseq]:
        ne_gloss[idseq].append(text)
by_form = defaultdict(list)
for idseq in sorted(ne_types):
    types = ne_types[idseq]
    if not PERSON.intersection(types) or not ne_kanji.get(idseq) or not ne_kana.get(idseq):
        continue
    gloss = "; ".join(ne_gloss[idseq]) if "p" in types else ""
    for form in dict.fromkeys(ne_kanji[idseq]):
        for kana in ne_kana[idseq][:1]:
            by_form[form].append("%s\t%s\t%s" % (kana, ",".join(types), gloss))
for form, lines in by_form.items():
    out.execute("INSERT INTO name VALUES (?,?)", (form, "\n".join(lines)))
names = len(by_form)

# KANJIDIC2: readings and meanings come through reading-meaning groups.
groups = defaultdict(list)
for gid, cid in src.execute("SELECT ID, cid FROM rm_group"):
    groups[cid].append(gid)
reads = defaultdict(lambda: ([], []))
for gid, rtype, value in src.execute("SELECT gid, r_type, value FROM reading"):
    if rtype == "ja_on":
        reads[gid][0].append(value)
    elif rtype == "ja_kun":
        reads[gid][1].append(value)
means = defaultdict(list)
for gid, value, lang in src.execute("SELECT gid, value, m_lang FROM meaning"):
    if lang in (None, "", "en"):
        means[gid].append(value)

def strokes_for(ch):
    path = os.path.join(kvg_dir, "kanji", "%05x.svg" % ord(ch))
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as f:
        svg = f.read()
    return "\n".join(re.findall(r'<path[^>]*\sd="([^"]+)"', svg)) or None

k = 0
for cid, literal, strokes, grade, freq, jlpt in src.execute(
        "SELECT ID, literal, stroke_count, grade, freq, jlpt FROM character"):
    on, kun, mean = [], [], []
    for gid in groups.get(cid, []):
        on += reads[gid][0]
        kun += reads[gid][1]
        mean += means[gid]
    if not mean:
        continue
    out.execute("INSERT OR REPLACE INTO kanji VALUES (?,?,?,?,?,?,?,?,?)", (
        literal, strokes, int(grade) if grade else None, int(jlpt) if jlpt else None,
        int(freq) if freq else None, "、".join(on), "、".join(kun), "; ".join(mean), strokes_for(literal),
    ))
    k += 1

out.executemany("INSERT INTO meta VALUES (?,?)", [
    ("format", "2"),
    ("jmdict", dict(src.execute("SELECT key, value FROM meta").fetchall()).get("jmdict.version", "")),
])
out.commit()
out.execute("VACUUM")
out.close()
os.replace(out_tmp, out_path)
print(f"{n} words, {names} name spellings, {k} kanji -> {out_path} ({os.path.getsize(out_path) / 1e6:.1f} MB)")
