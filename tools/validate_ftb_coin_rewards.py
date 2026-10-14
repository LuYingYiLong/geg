"""Validate the copied pack's coin rewards and SNBT bracket structure."""

from __future__ import annotations

import re

from patch_ftb_coin_rewards import ROOT, REWARDS, STARTER_QUEST, closing, quest_span


def validate() -> None:
    expected = dict(REWARDS)
    expected[STARTER_QUEST] = 2
    found: set[str] = set()
    reward_ids: set[str] = set()
    for path in ROOT.glob("*.snbt"):
        text = path.read_text(encoding="utf-8-sig")
        for opener, closer in (("{", "}"), ("[", "]")):
            for match in re.finditer(re.escape(opener), text):
                # Structural check for each opening token. The SNBT files may contain
                # formatted text in strings; only reward placement is checked below.
                try:
                    closing(text, match.start(), opener, closer)
                except ValueError:
                    if text[: match.start()].count('"') % 2 == 0:
                        raise
        for identifier, coins in expected.items():
            if not re.search(r'\bid:\s*"' + identifier + r'"', text):
                continue
            if identifier in found:
                raise ValueError(f"Duplicate quest {identifier}")
            found.add(identifier)
            start, end = quest_span(text, identifier)
            block = text[start:end]
            rewards = re.findall(r"coinMinorUnits:\s*(\d+)L\s+id:\s*\"([0-9A-F]{16})\"\s+team_reward:\s*false\s+type:\s*\"geg:coin\"", block)
            if len(rewards) != 1 or int(rewards[0][0]) != coins * 100:
                raise ValueError(f"Wrong GEG coin reward in {identifier}: {rewards}")
            if rewards[0][1] in reward_ids:
                raise ValueError(f"Duplicate reward ID {rewards[0][1]}")
            reward_ids.add(rewards[0][1])
    if found != set(expected):
        raise ValueError(f"Missing quests: {sorted(set(expected) - found)}")
    print(f"Validated {len(found)} quests and {len(reward_ids)} unique coin rewards")


if __name__ == "__main__":
    validate()
