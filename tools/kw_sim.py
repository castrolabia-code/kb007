#!/usr/bin/env python3
"""
Simulador del detector de palabra clave de KB-007 (misma lógica que VoskKeywordDetector.kt:
bloques de 20 ms, compuerta de energía con pre-buffer y hangover, gramática restringida,
parciales solo con confianza). Sirve para probar frases y umbrales en la PC.

Uso:
  pip install vosk numpy
  python3 tools/kw_sim.py MODELO_DIR audio_16k_mono.wav "palabra clave" [palabra_stop] [umbral]
"""
import sys, json, wave, numpy as np, vosk, unicodedata, re
vosk.SetLogLevel(-1)
M = vosk.Model(sys.argv[1])
def norm(s):
    s = unicodedata.normalize('NFD', s.lower()); s = ''.join(c for c in s if unicodedata.category(c) != 'Mn')
    return re.sub(r'\s+',' ',re.sub(r'[^a-zñ0-9 ]',' ',s)).strip()
def find(words, phrase):
    t = norm(phrase).split(); n=[norm(w['word']) for w in words]; best=None
    for i in range(len(words)-len(t)+1):
        if n[i:i+len(t)]==t:
            a=sum(words[i+k].get('conf',1) for k in range(len(t)))/len(t); best=a if best is None or a>best else best
    return best
def run(path, kw, stop=None, thr=0.6, noise=150):
    pcm = np.frombuffer(wave.open(path).readframes(10**9), dtype=np.int16).astype(np.int32)
    sil = np.zeros(16000, dtype=np.int32)
    x = np.concatenate([sil, pcm, sil, sil]); x = x + np.random.RandomState(1).normal(0, noise, len(x)).astype(np.int32)
    x = np.clip(x, -32768, 32767).astype(np.int16)
    grammar = json.dumps([kw] + ([stop] if stop else []) + ["[unk]"], ensure_ascii=False)
    r = vosk.KaldiRecognizer(M, 16000, grammar); r.SetWords(True); r.SetPartialWords(True)
    floor=-60; open_until=0; feeding=False; pre=[]; events=[]; t=0; fed=0
    def handle(js, final):
        if not final and '"conf"' not in js: return
        o=json.loads(js); words=o.get('result') or o.get('partial_result') or []
        k=find(words,kw); s=find(words,stop) if stop else None
        need = thr if final else max(thr+0.15,0.85)
        if final and words: events.append(("oído"," ".join(w['word'] for w in words), round(k or s or 0,2)))
        if k is not None and k>=need: events.append(("DISPARO" + ("" if final else " (parcial)"), round(k,2), round(t,2))); r.Reset()
        elif s is not None and s>=need: events.append(("STOP", round(s,2), round(t,2))); r.Reset()
    for i in range(0, len(x)-320, 320):
        b = x[i:i+320]; t = i/16000
        rms = np.sqrt(np.mean(b.astype(np.float64)**2)); db = 20*np.log10(max(rms,1)/32768)
        floor = floor + (db-floor)*(0.3 if db<floor else 0.005)
        if db > max(floor+9, -55): open_until = t+1.2
        if t < open_until:
            if not feeding:
                feeding=True
                for p in pre:
                    if r.AcceptWaveform(p.tobytes()): handle(r.Result(), True)
                pre=[]
            fed+=1
            if r.AcceptWaveform(b.tobytes()): handle(r.Result(), True)
            else: handle(r.PartialResult(), False)
        else:
            if feeding: feeding=False; handle(r.FinalResult(), True)
            pre.append(b); pre=pre[-15:]
    tot=len(x)//320
    print(f"{path.split('/')[-1]:32s} kw={kw!r:24s} → {events}  | Vosk procesó {100*fed//tot}% del audio")
if __name__ == "__main__":
    a = sys.argv
    run(a[2], a[3], a[4] if len(a) > 4 and a[4] else None, float(a[5]) if len(a) > 5 else 0.6)
