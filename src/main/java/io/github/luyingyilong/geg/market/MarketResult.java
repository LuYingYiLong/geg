package io.github.luyingyilong.geg.market;


public enum MarketResult {
    LISTED("listed"),
    BOUGHT("bought"),
    SOLD("sold"),
    REPRICED("repriced"),
    CANCELLED("cancelled"),
    CLAIMED("claimed"),
    FAVORITED("favorited"),
    UNFAVORITED("unfavorited"),
    INVALID("invalid"),
    STALE("stale"),
    INSUFFICIENT("insufficient"),
    OVERFLOW("overflow"),
    LIMIT("limit"),
    NO_ROOM("no_room"),
    EMPTY("empty"),
    SELF_BUY("self_buy"),
    REQUESTED("requested"),
    FULFILLED("fulfilled"),
    INSUFFICIENT_ITEMS("insufficient_items"),
    DISABLED("disabled"),
    ;

    private final String id;


    MarketResult(String id) {
        this.id = id;
    }


    public String translationKey() {
        return "market.geg.result." + id;
    }

    public boolean success() {
        return switch (this) {
            case LISTED, BOUGHT, SOLD, REPRICED, CANCELLED, CLAIMED,
                    FAVORITED, UNFAVORITED, REQUESTED, FULFILLED -> true;
            default -> false;
        };
    }
}
