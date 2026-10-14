"""Add the approved GEG coin rewards to a copied FTB Quests pack tree."""

from __future__ import annotations

import hashlib
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1] / "pack-overrides/config/ftbquests/quests/chapters"
REWARDS: dict[str, int] = {
    "15562B5313DDE9A5": 5,
    "40B6536B8B1750BB": 5,
    "60A8BD339235D4C5": 20,
    "5DD1DFBC650A5E8D": 25,
    "2E234A7111AC8092": 50,
    "1AC858C2BE3027E6": 25,
    "102432CFE3159567": 30,
    "2D763D90223C9A51": 40,
    "7823AA372E7CF0B5": 80,
    "28D68A77A793C60D": 10,
    "26E61D8A06FAB278": 30,
    "4677ACC569C2F349": 100,
    "523EE9F005F26AB1": 5,
    "6B34AF93D6458C6A": 5,
    "074A9D09364D75A4": 5,
    "436B853DD5DA2904": 5,
}
STARTER_QUEST = "7E6A4B31C9D80F22"
STARTER_TASK = "7E6A4B31C9D80F23"
STARTER_REWARD = "7E6A4B31C9D80F24"


def closing(text: str, start: int, opener: str, closer: str) -> int:
    depth = 0
    quoted = False
    escaped = False
    for index in range(start, len(text)):
        character = text[index]
        if quoted:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == '"':
                quoted = False
        elif character == '"':
            quoted = True
        elif character == opener:
            depth += 1
        elif character == closer:
            depth -= 1
            if depth == 0:
                return index
    raise ValueError(f"Unclosed {opener} at {start}")


def quest_span(text: str, quest_id: str) -> tuple[int, int]:
    match = re.search(r'\bid:\s*"' + quest_id + r'"', text)
    if match is None:
        raise ValueError(f"Missing quest {quest_id}")
    candidates: list[tuple[int, int]] = []
    for open_match in re.finditer(r"\{", text[: match.start()]):
        start = open_match.start()
        end = closing(text, start, "{", "}")
        if end > match.end():
            candidates.append((start, end + 1))
    if not candidates:
        raise ValueError(f"No object for {quest_id}")
    return min(candidates, key=lambda pair: pair[1] - pair[0])


def reward_id(quest_id: str) -> str:
    return hashlib.sha256(f"geg:coin:{quest_id}".encode()).hexdigest()[:16].upper()


def with_reward(block: str, identifier: str, coins: int) -> str:
    new_id = STARTER_REWARD if identifier == STARTER_QUEST else reward_id(identifier)
    if f'id: "{new_id}"' in block:
        return block
    reward = ("{\n\t\t\t\tcoinMinorUnits: " + str(coins * 100)
              + "L\n\t\t\t\tid: \"" + new_id
              + "\"\n\t\t\t\tteam_reward: false\n\t\t\t\ttype: \"geg:coin\"\n\t\t\t}")
    match = re.search(r"\brewards:\s*\[", block)
    if match is None:
        task = re.search(r"\btasks:\s*\[", block)
        if task is None:
            raise ValueError(f"Quest {identifier} has no task")
        return block[: task.start()] + "rewards: [\n\t\t\t\t" + reward + "\n\t\t\t]\n\t\t\t" + block[task.start() :]
    bracket = block.index("[", match.start())
    end = closing(block, bracket, "[", "]")
    return block[:end] + "\n\t\t\t\t" + reward + "\n\t\t\t" + block[end:]


def patch_file(path: Path, rewards: dict[str, int]) -> None:
    raw = path.read_bytes()
    newline = "\r\n" if b"\r\n" in raw else "\n"
    text = raw.decode("utf-8-sig").replace("\r\n", "\n")
    for identifier, coins in rewards.items():
        start, end = quest_span(text, identifier)
        text = text[:start] + with_reward(text[start:end], identifier, coins) + text[end:]
    if path.stem == "1E8DBE6066945792" and STARTER_QUEST not in text:
        new_quest = (
            "\n\t\t{\n"
            "\t\t\tcan_repeat: false\n"
            "\t\t\tdependencies: [\"09FABBF9B7AA076B\"]\n"
            f"\t\t\tid: \"{STARTER_QUEST}\"\n"
            "\t\t\trewards: [{\n"
            "\t\t\t\tcoinMinorUnits: 200L\n"
            f"\t\t\t\tid: \"{STARTER_REWARD}\"\n"
            "\t\t\t\tteam_reward: false\n"
            "\t\t\t\ttype: \"geg:coin\"\n"
            "\t\t\t}]\n"
            "\t\t\tsubtitle: \"获得市场终端后，可以进入全服自由市场\"\n"
            "\t\t\ttasks: [{\n"
            "\t\t\t\tconsume_items: false\n"
            f"\t\t\t\tid: \"{STARTER_TASK}\"\n"
            "\t\t\t\titem: \"geg:market_terminal\"\n"
            "\t\t\t\ttype: \"item\"\n"
            "\t\t\t}]\n"
            "\t\t\ttitle: \"&e获得市场终端\"\n"
            "\t\t\tx: 9.0d\n"
            "\t\t\ty: -4.0d\n"
            "\t\t}\n\t"
        )
        quests = re.search(r"\bquests:\s*\[", text)
        if quests is None:
            raise ValueError("Missing quest list")
        bracket = text.index("[", quests.start())
        end = closing(text, bracket, "[", "]")
        text = text[:end] + new_quest + text[end:]
    path.write_bytes(text.replace("\n", newline).encode("utf-8"))


def main() -> None:
    remaining = dict(REWARDS)
    for path in ROOT.glob("*.snbt"):
        text = path.read_text(encoding="utf-8-sig")
        chosen = {identifier: remaining.pop(identifier) for identifier in list(remaining)
                  if re.search(r'\bid:\s*"' + identifier + r'"', text)}
        if chosen or path.stem == "1E8DBE6066945792":
            patch_file(path, chosen)
    if remaining:
        raise ValueError(f"Quest IDs not found: {sorted(remaining)}")


if __name__ == "__main__":
    main()
