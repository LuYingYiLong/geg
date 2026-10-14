# GEG pack overrides

`config/ftbquests/quests` is a copy of the GEG modpack's FTB Quests definition tree.
It contains 13 one-time GEG coin rewards (including the new market terminal quest)
and four repeatable rewards. Existing quest rewards remain intact.

Deploy this directory to both the dedicated server and client pack. The server's
quest definitions are authoritative. Do not copy `saves/*/ftbquests`,
`saves/*/data/geg_money.dat`, or `saves/*/geg` between installations.
Back up the server's quest definitions and world before replacing either.

Run `python tools/validate_ftb_coin_rewards.py` from the repository root before
packaging. The game also needs FTB Quests 2001.2.0, FTB Teams 2001.1.4,
FTB Library 2001.1.4, and Architectury 9.2.14 on server and client for quest
rewards; GEG's ordinary money and market features still work without FTB Quests.
