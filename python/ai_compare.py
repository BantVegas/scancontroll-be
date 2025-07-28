#!/usr/bin/env python3
import os
import sys
import openai
import base64
import json
from dotenv import load_dotenv

load_dotenv()
openai.api_key = os.getenv("OPENAI_API_KEY")

def image_to_b64(path: str) -> str:
    with open(path, "rb") as f:
        return base64.b64encode(f.read()).decode()

def ai_denzita(label_path: str):
    l_b64 = image_to_b64(label_path)
    prompt = (
        "Si skúsený polygrafický inžinier. "
        "Analyzuj optickú denzitu etikety na obrázku podľa nasledujúcich rozsahov: "
        "Cyan: 1.3 až 1.5; Magenta: 1.3 až 1.5; Yellow: 1.1 až 1.4; Black: 1.5 až 1.8. "
        "Vráť len hodnoty denzity v JSON formáte:\n"
        '{"c": {"value": <hodnota>, "ok": <true/false>}, '
        '"m": {"value": <hodnota>, "ok": <true/false>}, '
        '"y": {"value": <hodnota>, "ok": <true/false>}, '
        '"k": {"value": <hodnota>, "ok": <true/false>}}. '
        "Nehodnoť nič iné, žiadne ďalšie komentáre. Hodnoty zaokrúhli na 2 desatinné miesta."
    )

    resp = openai.chat.completions.create(
        model="gpt-4o",
        messages=[
            {"role": "system", "content": "Si AI na meranie denzity etikiet."},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": prompt},
                    {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{l_b64}"}}
                ]
            }
        ],
        max_tokens=120,
        temperature=0.0
    )

    # extrahuj a vráť JSON priamo
    answer = resp.choices[0].message.content.strip()
    # ak náhodou AI vráti text pred alebo po, vytiahni len JSON
    start = answer.find("{")
    end = answer.rfind("}")
    if start != -1 and end != -1:
        answer = answer[start:end+1]
    try:
        parsed = json.loads(answer)
    except Exception as e:
        parsed = {"error": "Parsing AI JSON failed", "raw": answer}
    return parsed

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Použitie: ai_denzita_single.py <etiketa.png>")
        sys.exit(1)
    vysl = ai_denzita(sys.argv[1])
    print(json.dumps(vysl, ensure_ascii=False, indent=2))
